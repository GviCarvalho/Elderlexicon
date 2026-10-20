package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.spelling.flow.Happenings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The dreams of the facts (docs/aprendizado-runas-design.md, 4e): "if, and who". The place the mage lay down in, of its
 * stone, twice, a wall of glass between; on the far side the other, {@code ille}, a figure with the dreamer's face that
 * does what they do, mirrored. The mirror lies: where the dreamer has floor, the other has a pit, fire, water, a beast.
 * <ul>
 *   <li><b>The fact:</b> it must happen to the other, twice, and not to the dreamer as it does; then the rift opens.</li>
 *   <li><b>Turned round ({@code non}):</b> the rift is open at the far end, and the other must not live the fact on the
 *       way there; if it does, back to the beginning.</li>
 * </ul>
 * Learning any fact teaches the four words of the family too: {@code aut}, {@code non}, {@code ego}, {@code ille}.
 */
final class FeverMirror {

    private FeverMirror() {
    }

    /** The facts of the body, each with the happening that is it (Happenings), for the dreamer's own. */
    static final Map<String, String> FACTS = Map.ofEntries(
            Map.entry("ferit", Happenings.ATTACK), Map.entry("patitur", Happenings.HURT),
            Map.entry("necat", Happenings.KILL), Map.entry("salit", Happenings.JUMP),
            Map.entry("cadit", Happenings.LAND), Map.entry("frangit", Happenings.BREAK),
            Map.entry("utitur", Happenings.USE), Map.entry("latet", Happenings.SNEAK),
            Map.entry("currit", Happenings.SPRINT), Map.entry("languet", Happenings.LOW),
            Map.entry("ardet", Happenings.BURNING), Map.entry("mergitur", Happenings.UNDERWATER));
    /** The words of the family, learned with the first fact. */
    static final List<String> WORDS = List.of("aut", "non", "ego", "ille");

    /** What lies on the other's side, for each fact that is a place. */
    private enum Lie {
        PIT, POOL, FIRE, SLIME, LOW, ICE, THORN, MAGMA, GLASS
    }

    private static final Map<String, Lie> LIES = Map.of("cadit", Lie.PIT, "mergitur", Lie.POOL, "ardet", Lie.FIRE,
            "salit", Lie.SLIME, "latet", Lie.LOW, "currit", Lie.ICE, "languet", Lie.THORN, "patitur", Lie.MAGMA,
            "frangit", Lie.GLASS);
    private static final Set<String> BEASTS = Set.of("ferit", "necat");

    /** How wide each side is, from the glass to its wall; how long. */
    private static final int WIDE = 11;
    private static final int LONG = 22;
    /** How many times the fact must happen to the other. */
    private static final int TWICE = 2;
    private static final int PIT_DEPTH = 4;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final Map<String, FeverVerbs.Rule> RULES = new HashMap<>();

    /** The rule of {@code fact}'s scene: the fact ({@code turned} false), or its "not". */
    static FeverVerbs.Rule rule(String fact, boolean turned) {
        return RULES.computeIfAbsent(fact + (turned ? "/non" : ""), key -> new Mirror(fact, turned));
    }

    // ------------------------------------------------------------------ where things are

    /** Across the glass: the block of the other side where the dreamer's {@code pos} is reflected. */
    private static BlockPos mirrored(BlockPos island, BlockPos pos) {
        return new BlockPos(2 * island.getX() - pos.getX(), pos.getY(), pos.getZ());
    }

    private static int mine(BlockPos island, int lx) {
        return island.getX() - 1 - lx;
    }

    private static int theirs(BlockPos island, int lx) {
        return island.getX() + 1 + lx;
    }

    private static int along(BlockPos island, int lz) {
        return island.getZ() - LONG / 2 + lz;
    }

    // ------------------------------------------------------------------ the scene

    private static final class Mirror implements FeverVerbs.Rule {
        private final String fact;
        private final boolean turned;
        private final Lie lie;

        Mirror(String fact, boolean turned) {
            this.fact = fact;
            this.turned = turned;
            this.lie = LIES.get(fact);
        }

        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public Vec3 start(BlockPos island, CompoundTag state) {
            return new Vec3(mine(island, WIDE / 2) + 0.5D, island.getY() + 1, along(island, 0) + 0.5D);
        }

        @Override
        public boolean keeps(BlockState was) {
            // Only the wool of the walls to be broken gives way: not the glass, nor the walls of the place.
            return !was.is(Blocks.WHITE_WOOL);
        }

        @Override
        public BlockPos place(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, RandomSource random) {
            BodyMemories.Piece home = FeverMeasures.home(level, island, state);
            BlockState floor = common(home, true, Blocks.STONE_BRICKS.defaultBlockState());
            BlockState wall = common(home, false, Blocks.OAK_PLANKS.defaultBlockState());
            int y = island.getY();
            for (int x = island.getX() - WIDE - 1; x <= island.getX() + WIDE + 1; x++) {
                for (int z = along(island, -1); z <= along(island, LONG); z++) {
                    boolean end = z == along(island, -1) || z == along(island, LONG)
                            || Math.abs(x - island.getX()) == WIDE + 1;
                    for (int dy = -PIT_DEPTH - 1; dy <= 9; dy++) {
                        BlockState here = dy <= 0 ? floor
                                : dy > 5 ? AIR
                                : x == island.getX() ? Blocks.GLASS.defaultBlockState()
                                : end ? wall : AIR;
                        KernDressing.put(level, mage, new BlockPos(x, y + dy, z), here);
                    }
                }
            }
            state.putInt("count", 0);
            state.putFloat("illeHp", 20.0F);
            state.putDouble("illeY", y + 1);
            int[] lies = new int[WIDE * LONG];
            if (lie != null) {
                if (turned) {
                    path(lies, random);
                } else {
                    spots(lies, random);
                    walls(level, mage, island, lies, random);
                }
                for (int lx = 0; lx < WIDE; lx++) {
                    for (int lz = 0; lz < LONG; lz++) {
                        if (lies[lx * LONG + lz] != 0) {
                            lay(level, mage, new BlockPos(theirs(island, lx), y, along(island, lz)), lie, floor);
                        }
                    }
                }
            } else if (BEASTS.contains(fact)) {
                beasts(level, mage, island, random);
            } else {
                levers(level, mage, island, random, state);
            }
            state.putIntArray("lies", lies);
            other(level, island, state, mage);
            BlockPos rift = new BlockPos(mine(island, WIDE / 2), y + 2, along(island, LONG - 1));
            state.putLong("rift", rift.asLong());
            // The fact: the rift opens once it has happened to the other. Turned round, it is open, at the far end.
            return turned ? rift : island.below(300);
        }

        /** The beasts, on the other's side: to be struck through it; turned round, walls of wool to break past them. */
        private void beasts(ServerLevel level, UUID mage, BlockPos island, RandomSource random) {
            EntityType<? extends Mob> kind = "necat".equals(fact) ? EntityType.CHICKEN : EntityType.SHEEP;
            int y = island.getY() + 1;
            if (!turned) {
                for (int n = 0; n < 3; n++) {
                    beast(level, kind, theirs(island, 1 + random.nextInt(WIDE - 2)) + 0.5D, y,
                            along(island, 5 + n * 6 + random.nextInt(3)) + 0.5D, false);
                    // And on this side, where there is nothing over there: struck, they count against the dreamer.
                    beast(level, kind, mine(island, 1 + random.nextInt(WIDE - 2)) + 0.5D, y,
                            along(island, 4 + n * 6 + random.nextInt(3)) + 0.5D, true);
                }
                int[] none = new int[WIDE * LONG];
                walls(level, mage, island, none, random);
                return;
            }
            for (int row = 6; row < LONG - 2; row += 6) {
                for (int lx = 0; lx < WIDE; lx++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        KernDressing.put(level, mage, new BlockPos(mine(island, lx), y - 1 + dy, along(island, row)),
                                Blocks.WHITE_WOOL.defaultBlockState());
                    }
                }
                for (int n = 0; n < 2; n++) {
                    beast(level, kind, theirs(island, random.nextInt(WIDE)) + 0.5D, y, along(island, row - 1) + 0.5D,
                            false);
                }
            }
        }

        /** The levers of the other's wall; turned round, gates on this side, and buttons to open them. */
        private void levers(ServerLevel level, UUID mage, BlockPos island, RandomSource random, CompoundTag state) {
            int y = island.getY() + 2;
            BlockState lever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                    .setValue(LeverBlock.FACING, Direction.WEST);
            if (!turned) {
                int first = 3 + random.nextInt(7);
                int second = 12 + random.nextInt(8);
                for (int lz : new int[]{first, second}) {
                    KernDressing.put(level, mage, new BlockPos(theirs(island, WIDE - 1), y, along(island, lz)), lever);
                }
                walls(level, mage, island, new int[WIDE * LONG], random);
                return;
            }
            // Three gates; before each, three buttons on this wall: across the glass, two of them are levers.
            int[] good = new int[3];
            for (int g = 0; g < 3; g++) {
                int row = 6 + g * 6;
                for (int lx = 0; lx < WIDE; lx++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        KernDressing.put(level, mage, new BlockPos(mine(island, lx), island.getY() + dy,
                                along(island, row)), Blocks.IRON_BARS.defaultBlockState());
                    }
                }
                good[g] = random.nextInt(3);
                for (int b = 0; b < 3; b++) {
                    int lz = row - 1 - b;
                    KernDressing.put(level, mage, new BlockPos(mine(island, WIDE - 1), y, along(island, lz)),
                            Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL)
                                    .setValue(ButtonBlock.FACING, Direction.EAST));
                    if (b != good[g]) {
                        KernDressing.put(level, mage, new BlockPos(theirs(island, WIDE - 1), y, along(island, lz)),
                                lever);
                    }
                }
            }
            state.putIntArray("good", good);
        }

        @Override
        public boolean used(ServerPlayer player, BlockPos island, CompoundTag state, BlockPos pos, Direction face,
                            ItemStack held) {
            if (!"utitur".equals(fact)) {
                return false;
            }
            ServerLevel level = player.serverLevel();
            BlockState clicked = level.getBlockState(pos);
            BlockPos touched = clicked.getBlock() instanceof ButtonBlock ? pos : pos.relative(face);
            BlockPos across = mirrored(island, touched);
            BlockState there = level.getBlockState(across);
            boolean pulled = there.getBlock() instanceof LeverBlock && !there.getValue(LeverBlock.POWERED);
            if (pulled) {
                // The other uses what is in its reach, as the dreamer does.
                KernDressing.put(level, player.getUUID(), across, there.setValue(LeverBlock.POWERED, true));
                player.playNotifySound(SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0F, 0.6F);
            }
            if (!turned) {
                if (pulled) {
                    counted(player, island, state, Component.translatable("dream.elderlexicon.mirror.lived"));
                }
                return false;
            }
            if (pulled) {
                failed(player, island, state);
                return false;
            }
            if (clicked.getBlock() instanceof ButtonBlock) {
                // A good button: its gate opens.
                int gate = (Math.floorDiv(pos.getZ() - along(island, 0), 6) + 1) * 6;
                for (int lx = 0; lx < WIDE; lx++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos bar = new BlockPos(mine(island, lx), island.getY() + dy, along(island, gate));
                        if (level.getBlockState(bar).is(Blocks.IRON_BARS)) {
                            KernDressing.put(level, player.getUUID(), bar, AIR);
                        }
                    }
                }
                player.playNotifySound(SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
            }
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            ServerLevel level = player.serverLevel();
            player.fallDistance = 0.0F;
            Entity other = state.hasUUID("ille") ? level.getEntity(state.getUUID("ille")) : null;
            if (other == null) {
                other(level, island, state, player.getUUID());
                return;
            }
            // Where the other is: the dreamer, reflected; its height its own, over its own floor.
            double ox = 2.0D * island.getX() + 1.0D - player.getX();
            double oz = player.getZ();
            int lx = Mth.floor(ox) - theirs(island, 0);
            int lz = Mth.floor(oz) - along(island, 0);
            int[] lies = state.getIntArray("lies");
            int spot = lx >= 0 && lz >= 0 && lx < WIDE && lz < LONG && lies.length == WIDE * LONG
                    ? lies[lx * LONG + lz] : 0;
            double ground = island.getY() + 1;
            if (spot != 0 && lie == Lie.PIT) {
                ground = island.getY() + 1 - PIT_DEPTH;
            } else if (spot != 0 && lie == Lie.POOL) {
                ground = island.getY() - 1;
            }
            double oy = state.getDouble("illeY");
            oy = oy > ground ? Math.max(ground, oy - 0.5D) : ground;
            if (spot != 0 && lie == Lie.LOW) {
                oy -= 0.35D;
            }
            if (spot != 0 && lie == Lie.SLIME) {
                oy += Math.abs(Mth.sin(player.tickCount * 0.5F)) * 0.9D;
            }
            state.putDouble("illeY", spot != 0 && (lie == Lie.LOW || lie == Lie.SLIME) ? ground : oy);
            other.moveTo(ox, oy, oz, -player.getYRot(), player.getXRot());
            other.setYHeadRot(-player.getYHeadRot());
            other.setRemainingFireTicks(spot != 0 && lie == Lie.FIRE ? 20 : 0);
            boolean lived = false;
            if (lie != null) {
                lived = lives(player, island, state, spot, oy, ground, lx, lz);
            } else if (BEASTS.contains(fact)) {
                lived = strikes(player, level, other, state);
            }
            int last = state.getInt("lastSpot");
            state.putInt("lastSpot", spot);
            if (lived) {
                if (turned) {
                    failed(player, island, state);
                    return;
                }
                if (Happenings.recent(player, FACTS.get(fact))) {
                    // In the dreamer too, as in the other: it is not the other's alone, and does not count.
                    player.displayClientMessage(Component.translatable("dream.elderlexicon.mirror.both")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                } else if (lie == null || spot != last || lie == Lie.PIT || lie == Lie.POOL || lie == Lie.THORN) {
                    counted(player, island, state, Component.translatable("dream.elderlexicon.mirror.lived"));
                    if (lie != null && spot != 0) {
                        spent(level, player.getUUID(), island, state, spot);
                    }
                }
                state.putDouble("illeY", island.getY() + 1);
            }
            if (state.getInt("count") >= TWICE || turned) {
                FeverVerbs.shine(player, FeverDream.rift(dream));
            }
        }

        /** Whether the other lives the fact this tick, at the place it is. */
        private boolean lives(ServerPlayer player, BlockPos island, CompoundTag state, int spot, double oy,
                              double ground, int lx, int lz) {
            if (spot == 0) {
                if (lie == Lie.THORN && !turned) {
                    // Off the thorns, the other mends, slowly.
                    state.putFloat("illeHp", Math.min(20.0F, state.getFloat("illeHp") + 0.05F));
                }
                return false;
            }
            switch (lie) {
                case PIT, POOL -> {
                    return oy <= ground + 0.01D;
                }
                case ICE -> {
                    Vec3 moved = player.getDeltaMovement();
                    double speed = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
                    double last = state.getDouble("lastMoved");
                    Vec3 at = player.position();
                    double step = state.contains("lastPX") ? Math.hypot(at.x - state.getDouble("lastPX"),
                            at.z - state.getDouble("lastPZ")) : 0.0D;
                    state.putDouble("lastPX", at.x);
                    state.putDouble("lastPZ", at.z);
                    state.putDouble("lastMoved", Math.max(speed, step));
                    return Math.max(last, step) > 0.13D;
                }
                case THORN -> {
                    float hp = state.getFloat("illeHp") - 0.25F;
                    state.putFloat("illeHp", hp);
                    if (player.tickCount % 10 == 0) {
                        player.playNotifySound(SoundEvents.SWEET_BERRY_BUSH_BREAK, SoundSource.BLOCKS, 0.6F, 0.6F);
                    }
                    if (hp <= 0.0F) {
                        // Too far: the other is gone, and comes back whole; nothing was lived.
                        state.putFloat("illeHp", 20.0F);
                        player.playNotifySound(SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.5F);
                        return false;
                    }
                    if (hp <= 20.0F * Happenings.LOW_HEALTH && !turned) {
                        state.putFloat("illeHp", 20.0F);
                        return true;
                    }
                    return turned && hp <= 20.0F * Happenings.LOW_HEALTH;
                }
                case MAGMA -> {
                    if (spot != state.getInt("lastSpot")) {
                        player.playNotifySound(SoundEvents.PLAYER_HURT_ON_FIRE, SoundSource.PLAYERS, 1.0F, 0.7F);
                        return true;
                    }
                    return false;
                }
                case GLASS -> {
                    BlockPos pane = new BlockPos(theirs(island, lx), island.getY() + 1, along(island, lz));
                    ServerLevel level = player.serverLevel();
                    if (level.getBlockState(pane).is(Blocks.GLASS_PANE)) {
                        level.sendParticles(player, new BlockParticleOption(ParticleTypes.BLOCK,
                                Blocks.GLASS.defaultBlockState()), false, pane.getX() + 0.5D, pane.getY() + 1.0D,
                                pane.getZ() + 0.5D, 20, 0.3D, 0.6D, 0.3D, 0.1D);
                        player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
                        KernDressing.put(level, player.getUUID(), pane, AIR);
                        KernDressing.put(level, player.getUUID(), pane.above(), AIR);
                        return true;
                    }
                    return false;
                }
                default -> {
                    // Fire, slime, the low ceiling: being there is living it.
                    return true;
                }
            }
        }

        /** The dreamer swings; so does the other, at what is before it on its side. Whether it struck (or killed). */
        private boolean strikes(ServerPlayer player, ServerLevel level, Entity other, CompoundTag state) {
            boolean swinging = player.swinging;
            int time = player.swingTime;
            boolean began = swinging && (!state.getBoolean("swung") || time < state.getInt("swingTime"));
            state.putBoolean("swung", swinging);
            state.putInt("swingTime", time);
            if (!began) {
                return false;
            }
            Vec3 look = player.getLookAngle();
            Vec3 facing = new Vec3(-look.x, 0.0D, look.z).normalize();
            for (Mob beast : level.getEntitiesOfClass(Mob.class, other.getBoundingBox().inflate(3.0D),
                    mob -> mob.getPersistentData().contains(BEAST) && !mob.getPersistentData().getBoolean(BEAST))) {
                Vec3 to = beast.position().subtract(other.position()).multiply(1.0D, 0.0D, 1.0D);
                if (to.length() > 3.0D || to.normalize().dot(facing) < 0.5D) {
                    continue;
                }
                level.broadcastDamageEvent(beast, level.damageSources().generic());
                player.playNotifySound("necat".equals(fact) ? SoundEvents.CHICKEN_HURT : SoundEvents.SHEEP_HURT,
                        SoundSource.NEUTRAL, 1.0F, 1.0F);
                int blows = beast.getPersistentData().getInt(BLOWS) + 1;
                beast.getPersistentData().putInt(BLOWS, blows);
                if (!"necat".equals(fact)) {
                    if (!turned) {
                        beast.getPersistentData().putBoolean(BEAST, true);
                    }
                    return true;
                }
                if (blows >= 2) {
                    // Killed by the other: gone, and nothing left of it.
                    level.sendParticles(player, ParticleTypes.POOF, false, beast.getX(), beast.getY() + 0.4D,
                            beast.getZ(), 12, 0.2D, 0.2D, 0.2D, 0.05D);
                    player.playNotifySound(SoundEvents.CHICKEN_DEATH, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    beast.discard();
                    return true;
                }
            }
            return false;
        }

        private void counted(ServerPlayer player, BlockPos island, CompoundTag state, Component said) {
            int count = state.getInt("count") + 1;
            state.putInt("count", count);
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.5F, 0.8F + count * 0.2F);
            if (count >= TWICE) {
                FeverDream.riftAt(Dreams.dream(player), Vec3.atCenterOf(BlockPos.of(state.getLong("rift"))));
                player.displayClientMessage(Component.translatable("dream.elderlexicon.mirror.open")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
            } else {
                player.displayClientMessage(said.copy().withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC),
                        true);
            }
        }

        /** Turned round, the other lived it: back to the beginning, both of them. */
        private void failed(ServerPlayer player, BlockPos island, CompoundTag state) {
            Vec3 back = start(island, state);
            player.teleportTo(back.x, back.y, back.z);
            player.setDeltaMovement(Vec3.ZERO);
            state.putDouble("illeY", island.getY() + 1);
            state.putFloat("illeHp", 20.0F);
            state.putInt("lastSpot", 0);
            ServerLevel level = player.serverLevel();
            if ("utitur".equals(fact)) {
                // The levers back as they were.
                for (int lz = 0; lz < LONG; lz++) {
                    BlockPos at = new BlockPos(theirs(island, WIDE - 1), island.getY() + 2, along(island, lz));
                    BlockState lever = level.getBlockState(at);
                    if (lever.getBlock() instanceof LeverBlock) {
                        KernDressing.put(level, player.getUUID(), at, lever.setValue(LeverBlock.POWERED, false));
                    }
                }
            }
            player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.6F);
            player.displayClientMessage(Component.translatable("dream.elderlexicon.mirror.failed")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }

        /** The spot the other lived the fact in, spent: the floor there again, as on this side. */
        private void spent(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, int spot) {
            int[] lies = state.getIntArray("lies");
            BlockState floor = level.getBlockState(new BlockPos(mine(island, 0), island.getY(), along(island, 0)));
            for (int lx = 0; lx < WIDE; lx++) {
                for (int lz = 0; lz < LONG; lz++) {
                    if (lies[lx * LONG + lz] == spot) {
                        lies[lx * LONG + lz] = 0;
                        BlockPos at = new BlockPos(theirs(island, lx), island.getY(), along(island, lz));
                        for (int dy = -PIT_DEPTH; dy <= 2; dy++) {
                            KernDressing.put(level, mage, at.above(dy), dy <= 0 ? floor : AIR);
                        }
                    }
                }
            }
            state.putIntArray("lies", lies);
        }
    }

    private static final String BEAST = "elderlexicon_beast";
    private static final String BLOWS = "elderlexicon_blows";

    /** A beast of the dream, still; {@code mine} on the dreamer's side, untouchable there. */
    private static void beast(ServerLevel level, EntityType<? extends Mob> kind, double x, double y, double z,
                              boolean mine) {
        Mob beast = kind.create(level);
        if (beast == null) {
            return;
        }
        beast.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        beast.setNoAi(true);
        beast.setPersistenceRequired();
        beast.setSilent(true);
        beast.setInvulnerable(mine);
        if (!mine) {
            beast.getPersistentData().putBoolean(BEAST, false);
        }
        FeverDream.mark(beast);
        level.addFreshEntity(beast);
    }

    /** The other: a figure with the dreamer's face, dressed dark, that nothing touches. */
    private static void other(ServerLevel level, BlockPos island, CompoundTag state, UUID mage) {
        ServerPlayer dreamer = level.getServer().getPlayerList().getPlayer(mage);
        ArmorStand other = new ArmorStand(EntityType.ARMOR_STAND, level);
        CompoundTag looks = new CompoundTag();
        looks.putBoolean("Marker", true);
        looks.putBoolean("ShowArms", true);
        looks.putBoolean("NoBasePlate", true);
        other.readAdditionalSaveData(looks);
        other.moveTo(theirs(island, WIDE / 2) + 0.5D, island.getY() + 1, along(island, 0) + 0.5D, 0.0F, 0.0F);
        other.setNoGravity(true);
        other.setInvulnerable(true);
        ItemStack face = new ItemStack(Items.PLAYER_HEAD);
        if (dreamer != null) {
            face.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(),
                    dreamer.getGameProfile()));
        }
        other.setItemSlot(EquipmentSlot.HEAD, face);
        other.setItemSlot(EquipmentSlot.CHEST, dark(Items.LEATHER_CHESTPLATE));
        other.setItemSlot(EquipmentSlot.LEGS, dark(Items.LEATHER_LEGGINGS));
        other.setItemSlot(EquipmentSlot.FEET, dark(Items.LEATHER_BOOTS));
        other.setCustomName(Component.literal("ille"));
        FeverDream.mark(other);
        level.addFreshEntity(other);
        state.putUUID("ille", other.getUUID());
    }

    private static ItemStack dark(net.minecraft.world.item.Item kind) {
        ItemStack worn = new ItemStack(kind);
        worn.getOrCreateTagElement("display").putInt("color", 0x22202A);
        return worn;
    }

    /** The commonest block of the place, of its floor or of what stands over it. */
    private static BlockState common(BodyMemories.Piece home, boolean floor, BlockState otherwise) {
        Map<BlockState, Integer> counts = new HashMap<>();
        for (int x = 0; x < BodyMemories.Piece.WIDTH; x++) {
            for (int z = 0; z < BodyMemories.Piece.WIDTH; z++) {
                int from = floor ? BodyMemories.BELOW - 1 : BodyMemories.BELOW;
                int to = floor ? BodyMemories.BELOW - 1 : BodyMemories.Piece.HEIGHT - 1;
                for (int y = from; y <= to; y++) {
                    BlockState here = FeverMeasures.sized(home.at(x, y, z));
                    if (here.isSolid() && !here.hasBlockEntity() && here.isCollisionShapeFullBlock(
                            net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                            && !(here.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)) {
                        counts.merge(here, 1, Integer::sum);
                    }
                }
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(otherwise);
    }

    /** Three spots of two by two on the other's side, where the mirror lies. */
    private static void spots(int[] lies, RandomSource random) {
        for (int n = 1; n <= 3; n++) {
            int lz = 3 + (n - 1) * 6 + random.nextInt(4);
            int lx = random.nextInt(WIDE - 1);
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    lies[(lx + dx) * LONG + lz + dz] = n;
                }
            }
        }
    }

    /**
     * Turned round: the other's side full of the lie, but for a way through, two wide, from the beginning to the far
     * end. Every cell of it is its own spot.
     */
    private static void path(int[] lies, RandomSource random) {
        boolean[] safe = new boolean[WIDE * LONG];
        int lx = WIDE / 2;
        for (int lz = 0; lz < LONG; lz++) {
            for (int dz = 0; dz < 1; dz++) {
                safe[lx * LONG + lz] = true;
                safe[Math.min(WIDE - 1, lx + 1) * LONG + lz] = true;
            }
            if (lz % 2 == 1) {
                int to = Mth.clamp(lx + random.nextInt(7) - 3, 0, WIDE - 2);
                for (int x = Math.min(lx, to); x <= Math.max(lx, to) + 1; x++) {
                    safe[x * LONG + lz] = true;
                }
                lx = to;
            }
        }
        int n = 1;
        for (int x = 0; x < WIDE; x++) {
            for (int z = 2; z < LONG - 1; z++) {
                if (!safe[x * LONG + z] && random.nextFloat() < 0.75F) {
                    lies[x * LONG + z] = n++;
                }
            }
        }
    }

    /** Walls on the dreamer's side, two high: never where the other's spots are reflected, nor at the beginning. */
    private static void walls(ServerLevel level, UUID mage, BlockPos island, int[] lies, RandomSource random) {
        BlockState wall = level.getBlockState(new BlockPos(mine(island, 0), island.getY(), along(island, -1)).above());
        for (int n = 0; n < 6; n++) {
            int lz = 3 + random.nextInt(LONG - 5);
            int from = random.nextInt(WIDE - 3);
            int length = 3 + random.nextInt(5);
            for (int lx = from; lx < Math.min(WIDE, from + length); lx++) {
                if (lies[lx * LONG + lz] != 0) {
                    continue;
                }
                for (int dy = 1; dy <= 2; dy++) {
                    KernDressing.put(level, mage, new BlockPos(mine(island, lx), island.getY() + dy, along(island, lz)),
                            wall);
                }
            }
        }
    }

    /** What the mirror shows on the other's side, at the floor block {@code at}. */
    private static void lay(ServerLevel level, UUID mage, BlockPos at, Lie lie, BlockState floor) {
        switch (lie) {
            case PIT -> {
                for (int dy = 0; dy > -PIT_DEPTH; dy--) {
                    KernDressing.put(level, mage, at.above(dy), AIR);
                }
                KernDressing.put(level, mage, at.below(PIT_DEPTH), Blocks.BLACK_CONCRETE.defaultBlockState());
            }
            case POOL -> {
                KernDressing.put(level, mage, at, Blocks.WATER.defaultBlockState());
                KernDressing.put(level, mage, at.below(), Blocks.WATER.defaultBlockState());
            }
            case FIRE -> {
                KernDressing.put(level, mage, at, Blocks.SOUL_SOIL.defaultBlockState());
                KernDressing.put(level, mage, at.above(), Blocks.SOUL_FIRE.defaultBlockState());
            }
            case SLIME -> KernDressing.put(level, mage, at, Blocks.SLIME_BLOCK.defaultBlockState());
            case LOW -> KernDressing.put(level, mage, at.above(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP));
            case ICE -> KernDressing.put(level, mage, at, Blocks.BLUE_ICE.defaultBlockState());
            case THORN -> KernDressing.put(level, mage, at.above(), Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                    .setValue(SweetBerryBushBlock.AGE, 3));
            case MAGMA -> KernDressing.put(level, mage, at, Blocks.MAGMA_BLOCK.defaultBlockState());
            case GLASS -> {
                KernDressing.put(level, mage, at.above(), Blocks.GLASS_PANE.defaultBlockState());
                KernDressing.put(level, mage, at.above(2), Blocks.GLASS_PANE.defaultBlockState());
            }
        }
    }
}
