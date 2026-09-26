package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.sight.Visibility;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.elderlexicon.mod.spelling.network.VisibilityPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What is seen less than whole ({@code surgit m1 quantum 0}), on the server: everything bearing the mark (creatures,
 * other entities and blocks) is kept at the level asked, and everyone who can see it is told
 * ({@code docs/surgit-visao-design.md}, section 5). It lasts two seconds, as every sight spell, or what chronos asks, and
 * is paid for at once; then the sight comes back by itself. {@code surgit m1 quantum 10} brings it back sooner. What it
 * costs follows how much there is to hide (the mass the marks already give to things) and for how long.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class SpellVisibility {

    private static final int SECOND_TICKS = 20;

    /** Something seen less than whole: how much, and until when. */
    private record Altered(int level, long untilTick) {
    }

    /** A block, wherever it is. */
    private record Place(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<UUID, Altered> ENTITIES = new ConcurrentHashMap<>();
    private static final Map<Place, Altered> BLOCKS = new ConcurrentHashMap<>();

    private SpellVisibility() {
    }

    /**
     * Sets the sight of everything loaded bearing {@code mark} to {@code level} for {@code seconds} (chronos, or null for
     * the default); returns the UMU owed for all of it.
     */
    public static double apply(ServerPlayer caster, String mark, int level, @Nullable Double seconds) {
        double owed = 0.0D;
        for (MarkTargets.Marked thing : MarkTargets.find(caster.server, mark)) {
            if (thing.entity != null) {
                owed += applyTo(caster, thing.entity, thing.mass, level, seconds);
            } else if (thing.blockPos != null) {
                owed += applyTo(caster, thing.level, thing.blockPos, thing.mass, level, seconds);
            }
        }
        return owed;
    }

    /**
     * {@code surgit quantum N} with no mark: the sight of what the mage aims at, within touch (the reach to break or use
     * blocks), a creature or thing before a block. Returns the UMU owed now, or -1 when nothing is aimed at.
     */
    public static double applyAimed(ServerPlayer caster, int level, @Nullable Double seconds) {
        double reach = Math.max(1.0D, caster.getBlockReach());
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(reach));
        BlockHitResult block = caster.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, caster));
        double blockDistance = block.getType() == HitResult.Type.MISS ? reach * reach : block.getLocation().distanceToSqr(eye);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(caster.serverLevel(), caster, eye, end,
                caster.getBoundingBox().expandTowards(caster.getLookAngle().scale(reach)).inflate(1.0D),
                candidate -> !candidate.isSpectator() && candidate != caster);
        if (entity != null && entity.getLocation().distanceToSqr(eye) <= blockDistance) {
            return applyTo(caster, entity.getEntity(), MarkTargets.massOf(entity.getEntity()), level, seconds);
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = block.getBlockPos();
            return applyTo(caster, caster.serverLevel(), pos, MarkTargets.massOf(caster.serverLevel().getBlockState(pos)),
                    level, seconds);
        }
        return -1.0D;
    }

    /** Hides the block at {@code pos} for {@code seconds} (an image drawn off or pushed away); returns the UMU owed. */
    static double hideBlock(ServerPlayer caster, ServerLevel world, BlockPos pos, double seconds) {
        return applyTo(caster, world, pos, MarkTargets.massOf(world.getBlockState(pos)), Visibility.HIDDEN, seconds);
    }

    private static double applyTo(ServerPlayer caster, Entity entity, double mass, int level, @Nullable Double seconds) {
        if (level >= Visibility.SEEN) {
            restore(entity);
            return 0.0D;
        }
        ENTITIES.put(entity.getUUID(), new Altered(level, until(caster, seconds)));
        tell(entity, level);
        return owed(mass, level, seconds);
    }

    private static double applyTo(ServerPlayer caster, ServerLevel world, BlockPos pos, double mass, int level,
                                  @Nullable Double seconds) {
        Place place = new Place(world.dimension(), pos.immutable());
        if (level >= Visibility.SEEN) {
            restore(world, place);
            return 0.0D;
        }
        BLOCKS.put(place, new Altered(level, until(caster, seconds)));
        tell(world, pos, level);
        return owed(mass, level, seconds);
    }

    private static long until(ServerPlayer caster, @Nullable Double seconds) {
        return caster.server.getTickCount() + Math.round(Visibility.seconds(seconds) * SECOND_TICKS);
    }

    private static double owed(double mass, int level, @Nullable Double seconds) {
        return Visibility.costPerSecond(mass, level) * Visibility.seconds(seconds);
    }

    /** Whether {@code entity} is being seen less than whole by a spell: magic that {@code vis surgit} reveals. */
    public static boolean altered(Entity entity) {
        return ENTITIES.containsKey(entity.getUUID());
    }

    /** Blocks seen less than whole in {@code dimension}, by position. */
    public static List<BlockPos> alteredBlocks(ResourceKey<Level> dimension) {
        List<BlockPos> found = new ArrayList<>();
        BLOCKS.keySet().forEach(place -> {
            if (place.dimension().equals(dimension)) {
                found.add(place.pos());
            }
        });
        return found;
    }

    private static void restore(Entity entity) {
        if (ENTITIES.remove(entity.getUUID()) != null) {
            tell(entity, Visibility.SEEN);
        }
    }

    private static void restore(@Nullable ServerLevel level, Place place) {
        if (BLOCKS.remove(place) != null && level != null) {
            tell(level, place.pos(), Visibility.SEEN);
        }
    }

    /** Tells everyone who sees {@code entity}, and the entity itself when it is a player, how much of it is seen. */
    private static void tell(Entity entity, int level) {
        SpellingNetwork.sendVisibility(entity, VisibilityPacket.entity(entity.getId(), level));
    }

    /** Tells everyone watching the block's chunk how much of the block is seen. */
    private static void tell(ServerLevel level, BlockPos pos, int visibility) {
        SpellingNetwork.sendVisibility(level.getChunkAt(pos), VisibilityPacket.block(pos, visibility));
    }

    /** Brings back the sight of what has run out of time, checked every tick so it ends when it should. */
    @SubscribeEvent
    public static void expire(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ENTITIES.isEmpty() && BLOCKS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        new ArrayList<>(ENTITIES.entrySet()).forEach(entry -> {
            if (now < entry.getValue().untilTick()) {
                return;
            }
            Entity entity = MarkTargets.findEntity(server, entry.getKey());
            if (entity != null) {
                restore(entity);
            } else {
                ENTITIES.remove(entry.getKey());
            }
        });
        new ArrayList<>(BLOCKS.entrySet()).forEach(entry -> {
            ServerLevel level = server.getLevel(entry.getKey().dimension());
            if (now >= entry.getValue().untilTick()) {
                restore(level, entry.getKey());
            }
        });
    }

    /** Someone coming into sight of an altered entity learns how much of it is seen. */
    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        Altered altered = ENTITIES.get(event.getTarget().getUUID());
        if (altered != null && event.getEntity() instanceof ServerPlayer viewer) {
            SpellingNetwork.sendVisibilityTo(viewer, VisibilityPacket.entity(event.getTarget().getId(), altered.level()));
        }
    }

    /** Someone starting to watch a chunk learns which of its blocks are seen less than whole. */
    @SubscribeEvent
    public static void watchChunk(ChunkWatchEvent.Watch event) {
        ChunkPos chunk = event.getPos();
        ResourceKey<Level> dimension = event.getLevel().dimension();
        BLOCKS.forEach((place, altered) -> {
            if (place.dimension().equals(dimension) && new ChunkPos(place.pos()).equals(chunk)) {
                SpellingNetwork.sendVisibilityTo(event.getPlayer(), VisibilityPacket.block(place.pos(), altered.level()));
            }
        });
    }

    /** An entity that dies or is gone for good takes its spell with it; one merely unloaded keeps it. */
    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide() && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
            ENTITIES.remove(entity.getUUID());
        }
    }
}
