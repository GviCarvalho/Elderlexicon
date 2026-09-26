package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.ligabis.world.golem.GolemEntity;
import com.elderlexicon.mod.spell.SpellTicks;
import com.elderlexicon.mod.spell.scene.ArcBoltEntity;
import com.elderlexicon.mod.spell.sight.Revelation;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import com.elderlexicon.mod.spelling.network.RevelationPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The spirit's gaze in a revelation, on the server: it finds what carries the sought source or mark around the mage
 * and shows it to the mage alone ({@code docs/surgit-visao-design.md}, section 1). Creatures are looked for again every
 * half second while the gaze lasts, since they move; blocks are found once. A new gaze replaces the one before.
 */
public final class RevelationSight {

    private static final int REFRESH_TICKS = 10;
    /** At most this many blocks are shown, the closest ones. */
    private static final int MAX_BLOCKS = 4096;

    /** The gaze each player holds now; an older one stops when it sees a newer number here. */
    private static final Map<UUID, Integer> GAZES = new ConcurrentHashMap<>();

    private RevelationSight() {
    }

    /**
     * Starts a gaze of {@code player} for {@code kind} (and {@code mark}, for {@link Revelation.Kind#MARK}).
     * {@code sought} is the UMU asked of each thing ({@code quantum 2 firmo surgit}), or null for all of it; for now only
     * earth has a measure, so a sought value shows only the blocks of earth that hold it.
     */
    public static void begin(ServerPlayer player, Revelation.Kind kind, @Nullable String mark, @Nullable Double sought,
                             double radius, int ticks) {
        int gaze = GAZES.merge(player.getUUID(), 1, Integer::sum);
        List<RevelationPacket.SeenBlock> blocks = seenBlocks(player, kind, mark, sought, radius);
        look(player, kind, mark, sought, radius, ticks, gaze, blocks);
    }

    private static void look(ServerPlayer player, Revelation.Kind kind, @Nullable String mark, @Nullable Double sought,
                             double radius, int ticksLeft, int gaze, List<RevelationPacket.SeenBlock> blocks) {
        if (GAZES.getOrDefault(player.getUUID(), -1) != gaze) {
            return;
        }
        if (ticksLeft <= 0 || player.isRemoved() || !player.isAlive()) {
            GAZES.remove(player.getUUID(), gaze);
            if (!player.isRemoved()) {
                SpellingNetwork.sendRevelation(player, RevelationPacket.end());
            }
            return;
        }
        SpellingNetwork.sendRevelation(player,
                new RevelationPacket(ticksLeft, kind.color(),
                        sought == null ? seenEntities(player, kind, mark, radius) : List.of(), blocks,
                        kind == Revelation.Kind.VIS ? seenSpirits(player, radius) : List.of()));
        int step = Math.min(REFRESH_TICKS, ticksLeft);
        SpellTicks.schedule(player.server, step,
                () -> look(player, kind, mark, sought, radius, ticksLeft - step, gaze, blocks));
    }

    // ------------------------------------------------------------------ creatures

    private static List<RevelationPacket.SeenEntity> seenEntities(ServerPlayer player, Revelation.Kind kind,
                                                                  @Nullable String mark, double radius) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        List<RevelationPacket.SeenEntity> seen = new ArrayList<>();
        for (Entity entity : level.getEntities(player, new AABB(eye, eye).inflate(radius))) {
            if (entity.distanceToSqr(eye) > radius * radius) {
                continue;
            }
            Optional<String> carried = markOf(entity);
            boolean shown = switch (kind) {
                case IGNI -> isIgni(entity);
                case AQUA -> isAqua(entity);
                case FIRMO -> isFirmo(entity);
                case AURA -> isAura(entity);
                case VIS -> carried.isPresent() || isMagic(entity);
                case MARK -> carried.isPresent() && carried.get().equals(mark);
            };
            if (shown) {
                boolean labelled = kind == Revelation.Kind.VIS || kind == Revelation.Kind.MARK;
                seen.add(new RevelationPacket.SeenEntity(entity.getId(), labelled ? carried.orElse("") : ""));
            }
        }
        return seen;
    }

    /** Spirits projected out of their bodies around the mage: magic, which only a revelation of it shows. */
    private static List<RevelationPacket.SeenSpirit> seenSpirits(ServerPlayer player, double radius) {
        List<RevelationPacket.SeenSpirit> seen = new ArrayList<>();
        for (Map.Entry<ServerPlayer, Vec3> spirit : AstralProjections.spiritsNear(player.serverLevel(),
                player.getEyePosition(), radius)) {
            Vec3 at = spirit.getValue();
            seen.add(new RevelationPacket.SeenSpirit(spirit.getKey().getId(), at.x, at.y, at.z));
        }
        return seen;
    }

    /** The mark a creature carries, or the one on the scroll it holds up (a frame, a placed scroll). */
    private static Optional<String> markOf(Entity entity) {
        return MarkHelper.markForEntity(entity).or(() -> MarkHelper.markForItem(heldScroll(entity)));
    }

    private static ItemStack heldScroll(Entity entity) {
        if (entity instanceof ItemFrame frame) {
            return frame.getItem();
        }
        if (entity instanceof PlacedScrollEntity placed) {
            return placed.getScroll();
        }
        return ItemStack.EMPTY;
    }

    private static boolean isIgni(Entity entity) {
        return entity.isOnFire()
                || entity instanceof LightningBolt
                || entity instanceof AbstractHurtingProjectile
                || entity instanceof LivingEntity && entity.fireImmune();
    }

    private static boolean isAqua(Entity entity) {
        return entity instanceof WaterAnimal || entity instanceof Axolotl || entity instanceof Frog
                || entity instanceof Drowned || entity instanceof Guardian
                || entity instanceof LivingEntity && entity.isInWaterRainOrBubble();
    }

    private static boolean isFirmo(Entity entity) {
        return entity instanceof IronGolem || entity instanceof GolemEntity || entity instanceof FallingBlockEntity
                || entity instanceof Silverfish;
    }

    private static boolean isAura(Entity entity) {
        if (entity instanceof FlyingMob || entity instanceof FlyingAnimal || entity instanceof Bat || entity instanceof Vex) {
            return true;
        }
        if (entity instanceof Player player && (player.getAbilities().flying || player.isFallFlying())) {
            return true;
        }
        // Whatever the air is holding up right now, like one carried by the wind.
        return entity instanceof LivingEntity && !entity.onGround() && !entity.isInWater() && entity.getDeltaMovement().y > 0.05D;
    }

    private static boolean isMagic(Entity entity) {
        return entity instanceof GolemEntity || entity instanceof ArcBoltEntity || SpellVisibility.altered(entity)
                || heldScroll(entity).getItem() instanceof SpellScrollItem;
    }

    // ------------------------------------------------------------------ blocks

    private static List<RevelationPacket.SeenBlock> seenBlocks(ServerPlayer player, Revelation.Kind kind,
                                                               @Nullable String mark, @Nullable Double sought,
                                                               double radius) {
        ServerLevel level = player.serverLevel();
        BlockPos center = BlockPos.containing(player.getEyePosition());
        double reach = Math.min(radius, Revelation.MAX_BLOCK_RADIUS);
        List<RevelationPacket.SeenBlock> seen = new ArrayList<>();
        switch (kind) {
            case AURA -> {
                // Air is everywhere; what aura shows is what moves through it.
            }
            case VIS, MARK -> {
                markedBlocks(level, center, reach).forEach((pos, carried) -> {
                    if (kind == Revelation.Kind.VIS || carried.equals(mark)) {
                        seen.add(new RevelationPacket.SeenBlock(pos, carried));
                    }
                });
                if (kind == Revelation.Kind.VIS) {
                    // Blocks hidden by a spell are magic too, marked or not.
                    for (BlockPos pos : SpellVisibility.alteredBlocks(level.dimension())) {
                        if (pos.distSqr(center) <= reach * reach
                                && seen.stream().noneMatch(block -> block.pos().equals(pos))) {
                            seen.add(new RevelationPacket.SeenBlock(pos, ""));
                        }
                    }
                }
            }
            default -> {
                int r = (int) Math.ceil(reach);
                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx * dx + dy * dy + dz * dz > reach * reach) {
                                continue;
                            }
                            cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                            if (!level.hasChunkAt(cursor)) {
                                continue;
                            }
                            BlockState state = level.getBlockState(cursor);
                            if (carries(kind, state) && holds(kind, state, level, cursor, sought)
                                    && !buried(level, cursor, kind, sought)) {
                                seen.add(new RevelationPacket.SeenBlock(cursor.immutable(), "",
                                        strength(kind, state, level, cursor)));
                            }
                        }
                    }
                }
            }
        }
        if (seen.size() > MAX_BLOCKS) {
            seen.sort(Comparator.comparingDouble(block -> block.pos().distSqr(center)));
            return new ArrayList<>(seen.subList(0, MAX_BLOCKS));
        }
        return seen;
    }

    /** Whether a block holds the UMU sought; earth is measured by its hardness, the other elements not yet. */
    private static boolean holds(Revelation.Kind kind, BlockState state, ServerLevel level, BlockPos pos,
                                 @Nullable Double sought) {
        if (sought == null) {
            return true;
        }
        return kind == Revelation.Kind.FIRMO
                && Revelation.matches(Revelation.earthUmu(state.getDestroySpeed(level, pos)), sought);
    }

    /** Earth is seen by the UMU it holds, so dense rock and ores stand out of loose soil; the rest is seen whole. */
    private static int strength(Revelation.Kind kind, BlockState state, ServerLevel level, BlockPos pos) {
        if (kind != Revelation.Kind.FIRMO) {
            return 255;
        }
        double umu = Revelation.earthUmu(state.getDestroySpeed(level, pos));
        return (int) Math.round(255.0D * Revelation.density(umu));
    }

    /** Whether a block carries the element sought (never for vis or a mark, which are not in the block itself). */
    static boolean carries(Revelation.Kind kind, BlockState state) {
        return switch (kind) {
            case IGNI -> isIgni(state);
            case AQUA -> isAqua(state);
            case FIRMO -> isFirmo(state);
            default -> false;
        };
    }

    /**
     * A block wrapped on all six sides by the same element cannot be seen from anywhere, so it is left out: the
     * ground or the sea shows as its surface, and the few thousand blocks allowed reach much farther.
     */
    private static boolean buried(ServerLevel level, BlockPos pos, Revelation.Kind kind, @Nullable Double sought) {
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            next.setWithOffset(pos, side);
            if (!level.hasChunkAt(next)) {
                return false;
            }
            BlockState neighbour = level.getBlockState(next);
            // Only what is shown too can hide it: stone under dirt still shows when only stone is sought.
            if (!carries(kind, neighbour) || !holds(kind, neighbour, level, next, sought)) {
                return false;
            }
        }
        return true;
    }

    /** Marked blocks around {@code center}: plain ones the Ligabis keeps and those whose block entity bears a mark. */
    private static Map<BlockPos, String> markedBlocks(ServerLevel level, BlockPos center, double reach) {
        Map<BlockPos, String> found = new java.util.LinkedHashMap<>();
        LigabisManager manager = LigabisManager.get();
        if (manager != null) {
            manager.markedBlocks(level.dimension()).forEach((pos, carried) -> {
                if (pos.distSqr(center) <= reach * reach) {
                    found.put(pos, carried);
                }
            });
        }
        int chunkReach = (int) Math.ceil(reach / 16.0D);
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int x = cx - chunkReach; x <= cx + chunkReach; x++) {
            for (int z = cz - chunkReach; z <= cz + chunkReach; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (pos.distSqr(center) <= reach * reach) {
                        MarkHelper.markForBlock(level, pos).ifPresent(carried -> found.put(pos, carried));
                    }
                }
            }
        }
        return found;
    }

    private static boolean isIgni(BlockState state) {
        return state.is(BlockTags.FIRE)
                || state.getFluidState().is(FluidTags.LAVA)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)
                || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN) || state.is(Blocks.JACK_O_LANTERN)
                || state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    private static boolean isAqua(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER) || state.is(BlockTags.ICE) || state.is(Blocks.WATER_CAULDRON);
    }

    /** Earth: soil (dirt, grass, sand, gravel, clay, mud) and rock (stone of every dimension, sandstone, terracotta), and ores. */
    private static boolean isFirmo(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Tags.Blocks.GRAVEL)
                || state.is(Blocks.CLAY) || state.is(Blocks.MUD) || state.is(Blocks.PACKED_MUD)
                || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Tags.Blocks.STONE) || state.is(Tags.Blocks.COBBLESTONE) || state.is(Tags.Blocks.SANDSTONE)
                || state.is(Tags.Blocks.END_STONES) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.BEDROCK)
                || state.is(Tags.Blocks.OBSIDIAN)
                || state.is(Tags.Blocks.ORES);
    }
}
