package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The dreams of the filters (docs/aprendizado-runas-design.md, 4d). A source's dream changes the matter of the kern, a
 * function's its physics; a filter's changes the measures of the dreamer: how much they are, when, where, and where
 * from. No halls: the place where the mage lay down, made big, made small, wrecked, repeated, or a mass to be drunk;
 * a loop of time; a plain of numbers; a void built of the body. Each has the verb, and the verb turned round.
 * <ul>
 *   <li><b>quantum:</b> the dreamer small, in their own place four times as big, to be climbed; then the dreamer big,
 *       over a model of the land round their bed, where the bed is to be found.</li>
 *   <li><b>chronos:</b> a loop: every twenty seconds back to the start, the earlier loops walking again as echoes; at
 *       the bell three plates must be held at once. Then time going back: the place wrecked, mended only while the
 *       dreamer walks backwards.</li>
 *   <li><b>ubis:</b> a white plain, walls no one sees, and only the numbers of where one is and where to be. Then the
 *       same place everywhere, every way out a way back in, but down.</li>
 *   <li><b>tenet:</b> from the world: a mass of the place, whatever is touched drunk (food and life), what is near it
 *       grey; the hunger does not stop. Then from the body: a void, and blocks made of the dreamer's life, or of what
 *       they know, crouched.</li>
 * </ul>
 */
final class FeverMeasures {

    private FeverMeasures() {
    }

    /** The runes dreamed this way. */
    static final Set<String> FILTERS = Set.of("quantum", "chronos", "ubis", "tenet");
    private static final String ECHO = ElderLexicon.MODID + "_echo";
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Where a rift is that is not anywhere yet: under the kern, where no one goes. */
    private static BlockPos nowhere(BlockPos island) {
        return island.below(300);
    }

    // ------------------------------------------------------------------ the waking world, kept for the dream

    /** How many columns of the land a side the model has, how far apart they are taken, and how many it shows. */
    private static final int LAND = 64;
    private static final int LAND_STEP = 4;
    private static final int SHOWN = 44;

    /**
     * As the mage falls into a filter's dream: the place they lay down in, and for quantum the land round it, from
     * above, one column in four.
     */
    static void prepare(ServerPlayer player, CompoundTag state) {
        String rune = state.getString("rune");
        if (!FILTERS.contains(rune) || Nahva.isNahva(player.level())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        state.put("home", BodyMemories.Piece.of(level, at).save());
        if ("quantum".equals(rune)) {
            state.put("land", land(level, at));
        }
    }

    private static CompoundTag land(ServerLevel level, BlockPos at) {
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> known = new HashMap<>();
        int[] heights = new int[LAND * LAND];
        int[] tops = new int[LAND * LAND];
        int[] unders = new int[LAND * LAND];
        for (int mx = 0; mx < LAND; mx++) {
            for (int mz = 0; mz < LAND; mz++) {
                int i = mx * LAND + mz;
                int wx = at.getX() + (mx - LAND / 2) * LAND_STEP;
                int wz = at.getZ() + (mz - LAND / 2) * LAND_STEP;
                if (!level.isLoaded(new BlockPos(wx, at.getY(), wz))) {
                    heights[i] = Integer.MIN_VALUE;
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
                heights[i] = top - at.getY();
                tops[i] = known.computeIfAbsent(level.getBlockState(new BlockPos(wx, top, wz)), state -> {
                    palette.add(state);
                    return palette.size() - 1;
                });
                unders[i] = known.computeIfAbsent(level.getBlockState(new BlockPos(wx, top - 1, wz)), state -> {
                    palette.add(state);
                    return palette.size() - 1;
                });
            }
        }
        CompoundTag tag = new CompoundTag();
        ListTag states = new ListTag();
        palette.forEach(state -> states.add(NbtUtils.writeBlockState(state)));
        tag.put("Palette", states);
        tag.putIntArray("Heights", heights);
        tag.putIntArray("Tops", tops);
        tag.putIntArray("Unders", unders);
        return tag;
    }

    /** The place the mage lay down in; with none kept (a dream begun in Nahvá), the kern's own ground. */
    static BodyMemories.Piece home(ServerLevel level, BlockPos island, CompoundTag state) {
        return state.contains("home")
                ? BodyMemories.Piece.load(state.getCompound("home"), level.holderLookup(Registries.BLOCK))
                : BodyMemories.Piece.of(level, island.above());
    }

    // ------------------------------------------------------------------ a memory, made big

    private static final int W = BodyMemories.Piece.WIDTH;
    private static final int H = BodyMemories.Piece.HEIGHT;
    private static final int MID = BodyMemories.HALF;

    /** What a block of a memory is, made big or small: held still; a bed, its wool; a door, the way through. */
    static BlockState sized(BlockState state) {
        if (state.getBlock() instanceof BedBlock bed) {
            return wool(bed.getColor());
        }
        if (state.getBlock() instanceof DoorBlock) {
            return AIR;
        }
        state = FeverDream.still(state);
        if (state.hasProperty(LeavesBlock.PERSISTENT)) {
            state = state.setValue(LeavesBlock.PERSISTENT, true);
        }
        return state;
    }

    private static BlockState wool(DyeColor color) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation(color.getName() + "_wool")).defaultBlockState();
    }

    /** The lowest corner of the cube the block {@code (x, y, z)} of a piece is, made {@code scale} times as big. */
    private static BlockPos cell(BlockPos island, int scale, int x, int y, int z) {
        return island.offset((x - MID) * scale - scale / 2, (y - BodyMemories.BELOW) * scale + 1,
                (z - MID) * scale - scale / 2);
    }

    /**
     * The piece made {@code scale} times as big, its floor's top at the island's: each block a cube of itself; what
     * has no body to stand on (grass, a flower, a torch) only one high.
     */
    private static void enlarge(ServerLevel level, UUID mage, BlockPos island, BodyMemories.Piece piece, int scale) {
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                for (int z = 0; z < W; z++) {
                    fillCell(level, mage, island, scale, x, y, z, sized(piece.at(x, y, z)));
                }
            }
        }
    }

    private static void fillCell(ServerLevel level, UUID mage, BlockPos island, int scale, int x, int y, int z,
                                 BlockState state) {
        BlockPos at = cell(island, scale, x, y, z);
        boolean low = !state.isAir() && !state.isSolid();
        for (int dx = 0; dx < scale; dx++) {
            for (int dy = 0; dy < scale; dy++) {
                for (int dz = 0; dz < scale; dz++) {
                    KernDressing.put(level, mage, at.offset(dx, dy, dz), low && dy > 0 ? AIR : state);
                }
            }
        }
    }

    /** Which blocks of the piece, made big, are there to stand on. */
    private static boolean[][][] solid(BodyMemories.Piece piece) {
        boolean[][][] solid = new boolean[W][H][W];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                for (int z = 0; z < W; z++) {
                    solid[x][y][z] = sized(piece.at(x, y, z)).isSolid();
                }
            }
        }
        return solid;
    }

    private static boolean stands(boolean[][][] solid, int x, int y, int z) {
        return !solid[x][y][z] && (y == 0 || solid[x][y - 1][z]);
    }

    /** Where to stand, near the middle and low: beside the bed, on its floor. */
    private static int[] standing(boolean[][][] solid) {
        int[] best = {MID, BodyMemories.BELOW, MID};
        int score = Integer.MAX_VALUE;
        for (int x = 0; x < W; x++) {
            for (int y = 1; y < H; y++) {
                for (int z = 0; z < W; z++) {
                    if (stands(solid, x, y, z)) {
                        int here = Math.max(Math.abs(x - MID), Math.abs(z - MID)) * 100 + y;
                        if (here < score) {
                            score = here;
                            best = new int[]{x, y, z};
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Every place one can stand that can be come to from {@code from}: walking, falling, and climbing one if {@code climbs}. */
    private static List<int[]> reach(boolean[][][] solid, int[] from, boolean climbs) {
        boolean[][][] seen = new boolean[W][H][W];
        List<int[]> found = new ArrayList<>();
        java.util.ArrayDeque<int[]> next = new java.util.ArrayDeque<>();
        next.add(from);
        seen[from[0]][from[1]][from[2]] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!next.isEmpty()) {
            int[] at = next.poll();
            found.add(at);
            for (int[] step : steps) {
                int x = at[0] + step[0];
                int z = at[2] + step[1];
                int y = at[1];
                if (x < 0 || z < 0 || x >= W || z >= W) {
                    continue;
                }
                if (!solid[x][y][z]) {
                    while (y > 0 && !solid[x][y - 1][z]) {
                        y--;
                    }
                } else if (climbs && y + 1 < H && !solid[x][y + 1][z] && !solid[at[0]][y + 1][at[2]]) {
                    y++;
                } else {
                    continue;
                }
                if (!seen[x][y][z]) {
                    seen[x][y][z] = true;
                    next.add(new int[]{x, y, z});
                }
            }
        }
        return found;
    }

    private static void startAt(CompoundTag state, Vec3 at) {
        state.putDouble("startX", at.x);
        state.putDouble("startY", at.y);
        state.putDouble("startZ", at.z);
    }

    private static Vec3 started(CompoundTag state) {
        return new Vec3(state.getDouble("startX"), state.getDouble("startY"), state.getDouble("startZ"));
    }

    /** The air over the kern, from {@code from} to {@code to}, cleared. */
    private static void clear(ServerLevel level, UUID mage, BlockPos from, BlockPos to) {
        for (BlockPos at : BlockPos.betweenClosed(from, to)) {
            KernDressing.put(level, mage, at.immutable(), AIR);
        }
    }

    /** Below the scene, fallen off it: back where it began, nothing lost but the time. */
    private static boolean caught(ServerPlayer player, CompoundTag state, double floor) {
        if (player.getY() >= floor) {
            return false;
        }
        Vec3 back = started(state);
        player.teleportTo(back.x, back.y, back.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 0.6F);
        return true;
    }

    // ------------------------------------------------------------------ quantum

    private static final int SCALE = 4;

    /** quantum: the dreamer small, in the place they lay down in four times as big; the rift at its top. */
    static final FeverVerbs.Rule SMALL = new FeverVerbs.Rule() {

        @Override
        public BlockPos place(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, RandomSource random) {
            BodyMemories.Piece piece = home(level, island, state);
            enlarge(level, mage, island, piece, SCALE);
            boolean[][][] solid = solid(piece);
            int[] from = standing(solid);
            // The highest place that can be climbed to; the farthest of those.
            int[] top = from;
            for (int[] at : reach(solid, from, true)) {
                if (at[1] > top[1] || at[1] == top[1] && far(at, from) > far(top, from)) {
                    top = at;
                }
            }
            BlockPos begin = cell(island, SCALE, from[0], from[1], from[2]);
            startAt(state, new Vec3(begin.getX() + 2.0D, begin.getY(), begin.getZ() + 2.0D));
            return cell(island, SCALE, top[0], top[1], top[2]).offset(1, 1, 1);
        }

        @Override
        public Vec3 start(BlockPos island, CompoundTag state) {
            return started(state);
        }

        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            // Small, and light: a jump clears a block of the big place.
            if (player.tickCount % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 4, false, false));
            }
            player.fallDistance = 0.0F;
            FeverVerbs.shine(player, FeverDream.rift(dream));
            caught(player, state, island.getY() - 14);
        }
    };

    private static int far(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[2] - b[2]);
    }

    /** quantum turned round: the dreamer big, over a model of the land round their bed; the rift is the bed. */
    static final FeverVerbs.Rule MODEL = new FeverVerbs.Rule() {
        @Override
        public BlockPos place(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, RandomSource random) {
            CompoundTag land = state.getCompound("land");
            List<BlockState> palette = new ArrayList<>();
            ListTag states = land.getList("Palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < states.size(); i++) {
                palette.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), states.getCompound(i)));
            }
            int[] heights = land.getIntArray("Heights");
            int[] tops = land.getIntArray("Tops");
            int[] unders = land.getIntArray("Unders");
            boolean known = heights.length == LAND * LAND && !palette.isEmpty();
            // The model shows a window of the land, not always with the bed in its middle.
            int ox = random.nextInt(LAND - SHOWN + 1);
            int oz = random.nextInt(LAND - SHOWN + 1);
            int[] model = new int[SHOWN * SHOWN];
            int corner = SHOWN / 2;
            for (int mx = 0; mx < SHOWN; mx++) {
                for (int mz = 0; mz < SHOWN; mz++) {
                    int i = (mx + ox) * LAND + mz + oz;
                    int height = known ? heights[i] : 0;
                    boolean none = height == Integer.MIN_VALUE;
                    int h = none ? Integer.MIN_VALUE : Mth.clamp(Math.round(height / (float) LAND_STEP), -5, 9);
                    model[mx * SHOWN + mz] = h;
                    BlockState top = known && !none ? modelled(palette.get(tops[i])) : Blocks.GRASS_BLOCK.defaultBlockState();
                    BlockState under = known && !none ? modelled(palette.get(unders[i])) : Blocks.DIRT.defaultBlockState();
                    if (top == null) {
                        top = under != null ? under : Blocks.DIRT.defaultBlockState();
                    }
                    if (under == null || under.isAir()) {
                        under = Blocks.DIRT.defaultBlockState();
                    }
                    BlockPos column = island.offset(mx - corner, 0, mz - corner);
                    for (int y = -6; y <= 12; y++) {
                        BlockState here = none || y > h ? AIR : y == h ? top : under;
                        KernDressing.put(level, mage, column.above(y), here);
                    }
                }
            }
            // The bed: where the mage lay down, in the model; and others, beds the dream puts where there were none.
            int bx = LAND / 2 - ox;
            int bz = LAND / 2 - oz;
            BlockPos bed = null;
            if (bx >= 1 && bz >= 1 && bx < SHOWN - 1 && bz < SHOWN - 1 && model[bx * SHOWN + bz] != Integer.MIN_VALUE) {
                bed = island.offset(bx - corner, model[bx * SHOWN + bz] + 1, bz - corner);
                bed(level, mage, bed, model, bx, bz, island, corner);
            }
            for (int n = 0; n < 5; n++) {
                int x = 2 + random.nextInt(SHOWN - 4);
                int z = 2 + random.nextInt(SHOWN - 4);
                if (model[x * SHOWN + z] != Integer.MIN_VALUE && (Math.abs(x - bx) > 4 || Math.abs(z - bz) > 4)) {
                    bed(level, mage, island.offset(x - corner, model[x * SHOWN + z] + 1, z - corner), model, x, z,
                            island, corner);
                }
            }
            // Begin at a corner of the model, the farthest from the bed.
            int sx = bx < SHOWN / 2 ? SHOWN - 3 : 2;
            int sz = bz < SHOWN / 2 ? SHOWN - 3 : 2;
            int sh = model[sx * SHOWN + sz] == Integer.MIN_VALUE ? 0 : model[sx * SHOWN + sz];
            if (model[sx * SHOWN + sz] == Integer.MIN_VALUE) {
                KernDressing.put(level, mage, island.offset(sx - corner, 0, sz - corner),
                        Blocks.GRASS_BLOCK.defaultBlockState());
            }
            startAt(state, new Vec3(island.getX() + sx - corner + 0.5D, island.getY() + sh + 1,
                    island.getZ() + sz - corner + 0.5D));
            return bed == null ? nowhere(island) : bed;
        }

        @Override
        public Vec3 start(BlockPos island, CompoundTag state) {
            return started(state);
        }

        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            // Big: a stride takes a hill of the land.
            if (player.tickCount % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, false, false));
            }
            player.fallDistance = 0.0F;
            caught(player, state, island.getY() - 10);
        }
    };

    /** A red bed in the model, on the column at {@code (x, z)}, its head toward a neighbour as high. */
    private static void bed(ServerLevel level, UUID mage, BlockPos foot, int[] model, int x, int z, BlockPos island,
                            int corner) {
        Direction toward = Direction.NORTH;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int nx = x + side.getStepX();
            int nz = z + side.getStepZ();
            if (nx >= 0 && nz >= 0 && nx < SHOWN && nz < SHOWN && model[nx * SHOWN + nz] == model[x * SHOWN + z]) {
                toward = side;
                break;
            }
        }
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, toward);
        KernDressing.put(level, mage, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
        KernDressing.put(level, mage, foot.relative(toward), bed.setValue(BedBlock.PART, BedPart.HEAD));
        BlockPos below = foot.relative(toward).below();
        if (level.getBlockState(below).isAir()) {
            KernDressing.put(level, mage, below, Blocks.DIRT.defaultBlockState());
        }
    }

    /** A block of the land in the model: water glass the colour of water, lava of fire; what has no body, nothing. */
    private static BlockState modelled(BlockState state) {
        if (state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS)) {
            return Blocks.BLUE_STAINED_GLASS.defaultBlockState();
        }
        if (state.is(Blocks.LAVA)) {
            return Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
        }
        if (state.is(Blocks.SNOW)) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        BlockState sized = sized(state);
        return sized.isSolid() ? sized : null;
    }

    // ------------------------------------------------------------------ chronos

    /** How long a loop is, and when in it the bell rings. */
    private static final int LOOP_TICKS = 400;
    private static final int BELL_AT = 300;
    /** How many loops back walk again. */
    private static final int ECHOES = 3;
    private static final int PLATES = 3;
    private static final int DIAL = 14;
    /** By mage: the loops walked, each its places tick by tick; and the loop being walked. */
    private static final Map<UUID, List<List<double[]>>> PAST = new HashMap<>();
    private static final Map<UUID, List<double[]>> WALKING = new HashMap<>();

    /** chronos: a loop of twenty seconds; the loops before walk again; at the bell, three plates held at once. */
    static final FeverVerbs.Rule LOOP = new FeverVerbs.Rule() {
        @Override
        public BlockPos place(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, RandomSource random) {
            PAST.put(mage, new ArrayList<>());
            WALKING.put(mage, new ArrayList<>());
            state.putLong("loopStart", 0L);
            state.putBoolean("open", false);
            // A clock face for a floor.
            for (int x = -DIAL - 2; x <= DIAL + 2; x++) {
                for (int z = -DIAL - 2; z <= DIAL + 2; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    if (r > DIAL + 2) {
                        continue;
                    }
                    clear(level, mage, island.offset(x, 1, z), island.offset(x, 12, z));
                    if (r <= DIAL) {
                        KernDressing.put(level, mage, island.offset(x, 0, z), (r > DIAL - 1
                                ? Blocks.POLISHED_BLACKSTONE : Blocks.SMOOTH_STONE).defaultBlockState());
                    }
                }
            }
            for (int hour = 0; hour < 12; hour++) {
                double angle = hour * Math.PI / 6.0D;
                BlockPos mark = island.offset(Mth.floor(Math.cos(angle) * (DIAL - 2.5D)), 0,
                        Mth.floor(Math.sin(angle) * (DIAL - 2.5D)));
                KernDressing.put(level, mage, mark, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
            // The hands, pointing at no hour.
            for (int n = 2; n < 9; n++) {
                KernDressing.put(level, mage, island.offset(n, 0, -n / 2), Blocks.BLACKSTONE.defaultBlockState());
                if (n < 6) {
                    KernDressing.put(level, mage, island.offset(-n / 2, 0, n), Blocks.BLACKSTONE.defaultBlockState());
                }
            }
            // The plates, round the middle, too far apart for one body.
            int[] plates = new int[PLATES * 2];
            for (int n = 0; n < PLATES; n++) {
                double angle = -Math.PI / 2.0D + n * 2.0D * Math.PI / PLATES + random.nextDouble() * 0.6D;
                int x = Mth.floor(Math.cos(angle) * 9.0D);
                int z = Mth.floor(Math.sin(angle) * 9.0D);
                plates[n * 2] = x;
                plates[n * 2 + 1] = z;
                KernDressing.put(level, mage, island.offset(x, 0, z), Blocks.GOLD_BLOCK.defaultBlockState());
                KernDressing.put(level, mage, island.offset(x, 1, z),
                        Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
            }
            state.putIntArray("plates", plates);
            // The rift, caged in the middle.
            cage(level, mage, island, Blocks.IRON_BARS.defaultBlockState());
            KernDressing.put(level, mage, island.offset(0, 4, 0), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            // The bell, on its post.
            for (int y = 1; y <= 3; y++) {
                KernDressing.put(level, mage, island.offset(0, y, -DIAL - 1), Blocks.DARK_OAK_FENCE.defaultBlockState());
            }
            KernDressing.put(level, mage, island.offset(0, 4, -DIAL - 1), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            startAt(state, new Vec3(island.getX() + 0.5D, island.getY() + 1, island.getZ() + DIAL - 1.5D));
            return island.above(2);
        }

        @Override
        public Vec3 start(BlockPos island, CompoundTag state) {
            return started(state);
        }

        @Override
        public int length() {
            return LOOP_TICKS * 5 + 100;
        }

        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            UUID mage = player.getUUID();
            List<List<double[]>> past = PAST.computeIfAbsent(mage, uuid -> new ArrayList<>());
            List<double[]> walking = WALKING.computeIfAbsent(mage, uuid -> new ArrayList<>());
            if (state.getLong("loopStart") == 0L) {
                state.putLong("loopStart", now);
            }
            int t = (int) (now - state.getLong("loopStart"));
            if (t >= LOOP_TICKS) {
                // Back to the start; the loop just walked walks again, with the ones before it.
                past.add(new ArrayList<>(walking));
                while (past.size() > ECHOES) {
                    past.remove(0);
                }
                walking.clear();
                Vec3 back = started(state);
                player.teleportTo(back.x, back.y, back.z);
                player.setDeltaMovement(Vec3.ZERO);
                echoes(level, player, island, past.size());
                state.putLong("loopStart", now);
                player.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.0F, 0.6F);
                t = 0;
            }
            walking.add(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot()});
            // The echoes, each where it was at this moment of its loop.
            List<Vec3> bodies = new ArrayList<>();
            bodies.add(player.position());
            List<Entity> echoes = level.getEntities((Entity) null, player.getBoundingBox().inflate(40.0D),
                    thing -> thing.getPersistentData().contains(ECHO));
            for (Entity echo : echoes) {
                int which = echo.getPersistentData().getInt(ECHO);
                if (which < 0 || which >= past.size() || past.get(which).isEmpty()) {
                    continue;
                }
                List<double[]> walked = past.get(which);
                double[] was = walked.get(Math.min(t, walked.size() - 1));
                echo.moveTo(was[0], was[1], was[2], (float) was[3], 0.0F);
                echo.setYHeadRot((float) was[3]);
                bodies.add(echo.position());
            }
            if (t > 0 && t % 100 == 0 && t < BELL_AT) {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.AMBIENT, 0.8F, 0.5F);
            }
            if (t == BELL_AT) {
                player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 2.0F, 0.8F);
                if (!state.getBoolean("open") && held(state.getIntArray("plates"), island, bodies)) {
                    state.putBoolean("open", true);
                    cage(level, mage, island, AIR);
                    player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 2.0F, 1.2F);
                }
            }
            FeverVerbs.shine(player, FeverDream.rift(dream));
        }
    };

    /** The bars round the middle, or nothing where they were. */
    private static void cage(ServerLevel level, UUID mage, BlockPos island, BlockState bars) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    for (int y = 1; y <= 3; y++) {
                        KernDressing.put(level, mage, island.offset(x, y, z), bars);
                    }
                }
            }
        }
    }

    /** Whether each plate has a body on it. */
    private static boolean held(int[] plates, BlockPos island, List<Vec3> bodies) {
        for (int n = 0; n + 1 < plates.length; n += 2) {
            double x = island.getX() + plates[n] + 0.5D;
            double z = island.getZ() + plates[n + 1] + 0.5D;
            boolean on = false;
            for (Vec3 body : bodies) {
                if (Math.abs(body.x - x) < 0.8D && Math.abs(body.z - z) < 0.8D
                        && Math.abs(body.y - (island.getY() + 1)) < 1.0D) {
                    on = true;
                    break;
                }
            }
            if (!on) {
                return false;
            }
        }
        return true;
    }

    /** The echoes of the loops walked: stands with the dreamer's face, that nothing touches. */
    private static void echoes(ServerLevel level, ServerPlayer player, BlockPos island, int count) {
        level.getEntities((Entity) null, player.getBoundingBox().inflate(60.0D),
                thing -> thing.getPersistentData().contains(ECHO)).forEach(Entity::discard);
        ItemStack face = new ItemStack(Items.PLAYER_HEAD);
        face.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), player.getGameProfile()));
        for (int n = 0; n < count; n++) {
            ArmorStand echo = new ArmorStand(EntityType.ARMOR_STAND, level);
            CompoundTag looks = new CompoundTag();
            looks.putBoolean("Marker", true);
            looks.putBoolean("ShowArms", true);
            looks.putBoolean("NoBasePlate", true);
            echo.readAdditionalSaveData(looks);
            Vec3 at = player.position();
            echo.moveTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
            echo.setNoGravity(true);
            echo.setInvulnerable(true);
            echo.setGlowingTag(true);
            echo.setItemSlot(EquipmentSlot.HEAD, face.copy());
            echo.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            echo.getPersistentData().putInt(ECHO, n);
            FeverDream.mark(echo);
            level.addFreshEntity(echo);
        }
    }

    private static final int REWIND_SCALE = 3;
    /** By mage: the place of the rewound scene, as it will be when it is whole. */
    private static final Map<UUID, BodyMemories.Piece> WHOLE = new HashMap<>();

    /**
     * chronos turned round: time going back. The place the mage lay down in, three times as big, wrecked; it mends
     * only while the dreamer walks backwards, and breaks again as they walk on. Whole, the bed is the rift.
     */
    static final FeverVerbs.Rule REWIND = new FeverVerbs.Rule() {
        @Override
        public BlockPos place(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, RandomSource random) {
            BodyMemories.Piece piece = home(level, island, state);
            WHOLE.put(mage, piece);
            enlarge(level, mage, island, piece, REWIND_SCALE);
            boolean[][][] solid = solid(piece);
            // What is wrecked, in the order it will mend: the bed last of all.
            List<Integer> wrecked = new ArrayList<>();
            List<Integer> beds = new ArrayList<>();
            int[] bedTop = null;
            for (int x = 0; x < W; x++) {
                for (int y = BodyMemories.BELOW - 1; y < H; y++) {
                    for (int z = 0; z < W; z++) {
                        BlockState here = piece.at(x, y, z);
                        int index = (x * H + y) * W + z;
                        if (here.getBlock() instanceof BedBlock) {
                            beds.add(index);
                            if (bedTop == null || y > bedTop[1]) {
                                bedTop = new int[]{x, y, z};
                            }
                        } else if (!sized(here).isAir() && random.nextFloat() < 0.45F) {
                            wrecked.add(index);
                        }
                    }
                }
            }
            java.util.Collections.shuffle(wrecked, new java.util.Random(random.nextLong()));
            wrecked.addAll(beds);
            int[] order = wrecked.stream().mapToInt(Integer::intValue).toArray();
            for (int index : order) {
                int x = index / (H * W);
                int y = index / W % H;
                int z = index % W;
                BlockState ruin = random.nextFloat() < 0.6F ? AIR
                        : (random.nextBoolean() ? Blocks.COAL_BLOCK : Blocks.BLACKSTONE).defaultBlockState();
                fillCell(level, mage, island, REWIND_SCALE, x, y, z, ruin);
            }
            state.putIntArray("wrecked", order);
            state.putInt("mended", 0);
            int[] from = standing(solid);
            BlockPos begin = cell(island, REWIND_SCALE, from[0], from[1], from[2]);
            startAt(state, new Vec3(begin.getX() + 1.5D, begin.getY(), begin.getZ() + 1.5D));
            // Where the rift will be: on the bed, or with no bed, where the mage stood.
            int[] rest = bedTop != null ? bedTop : from;
            BlockPos lie = cell(island, REWIND_SCALE, rest[0], rest[1], rest[2]);
            Vec3 rift = bedTop != null ? new Vec3(lie.getX() + 1.5D, lie.getY() + REWIND_SCALE + 1.0D,
                    lie.getZ() + 1.5D) : new Vec3(lie.getX() + 1.5D, lie.getY() + 1.5D, lie.getZ() + 1.5D);
            state.putDouble("bedX", rift.x);
            state.putDouble("bedY", rift.y);
            state.putDouble("bedZ", rift.z);
            state.putDouble("lastX", Double.NaN);
            return nowhere(island);
        }

        @Override
        public Vec3 start(BlockPos island, CompoundTag state) {
            return started(state);
        }

        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            ServerLevel level = player.serverLevel();
            if (player.tickCount % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 2, false, false));
            }
            player.fallDistance = 0.0F;
            if (caught(player, state, island.getY() - 12)) {
                return;
            }
            int[] order = state.getIntArray("wrecked");
            int mended = state.getInt("mended");
            BodyMemories.Piece whole = WHOLE.get(player.getUUID());
            if (whole == null) {
                whole = home(level, island, state);
                WHOLE.put(player.getUUID(), whole);
            }
            double lastX = state.getDouble("lastX");
            double lastZ = state.getDouble("lastZ");
            state.putDouble("lastX", player.getX());
            state.putDouble("lastZ", player.getZ());
            if (Double.isNaN(lastX) || mended >= order.length) {
                return;
            }
            Vec3 moved = new Vec3(player.getX() - lastX, 0.0D, player.getZ() - lastZ);
            Vec3 facing = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
            double along = moved.dot(facing);
            if (along < -0.08D) {
                // Walking backwards: time goes back, and what was wrecked comes back whole, falling upwards.
                int per = Math.max(1, order.length / 300);
                for (int n = 0; n < per && mended < order.length; n++, mended++) {
                    int index = order[mended];
                    int x = index / (H * W);
                    int y = index / W % H;
                    int z = index % W;
                    BlockPos at = cell(island, REWIND_SCALE, x, y, z);
                    if (player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(at,
                            at.offset(REWIND_SCALE, REWIND_SCALE, REWIND_SCALE)))) {
                        // Not where the dreamer stands: it waits.
                        break;
                    }
                    BlockState was = sized(whole.at(x, y, z));
                    fillCell(level, player.getUUID(), island, REWIND_SCALE, x, y, z, was);
                    if (!was.isAir()) {
                        level.sendParticles(player, new BlockParticleOption(ParticleTypes.BLOCK, was), false,
                                at.getX() + 1.5D, at.getY() - 1.0D, at.getZ() + 1.5D, 12, 0.6D, 0.2D, 0.6D, 0.4D);
                    }
                }
                if (player.tickCount % 6 == 0) {
                    player.playNotifySound(SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.6F, 0.5F);
                }
                state.putInt("mended", mended);
                if (mended >= order.length) {
                    FeverDream.riftAt(dream, new Vec3(state.getDouble("bedX"), state.getDouble("bedY"),
                            state.getDouble("bedZ")));
                    player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 2.0F, 0.8F);
                }
            } else if (along > 0.1D && mended > 0 && player.tickCount % 4 == 0) {
                // Walking on: time goes on, and what was mended breaks again.
                mended--;
                int index = order[mended];
                fillCell(level, player.getUUID(), island, REWIND_SCALE, index / (H * W), index / W % H, index % W,
                        AIR);
                state.putInt("mended", mended);
            }
            if (player.tickCount % 10 == 0) {
                // The smoke of what burned.
                level.sendParticles(player, ParticleTypes.LARGE_SMOKE, false, island.getX(), island.getY() + 4.0D,
                        island.getZ(), 6, 8.0D, 3.0D, 8.0D, 0.01D);
            }
        }
    };

    // ------------------------------------------------------------------ ubis

    private static final int PLAIN = FeverHalls.ROOMS * FeverHalls.ROOM / 2;

    /** ubis: a white plain, walls no one sees, and the numbers: where one is, and where to be. */
    static final FeverVerbs.Rule NUMBERS = new FeverVerbs.Rule() {
        @Override
        public BlockPos place(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, RandomSource random) {
            int rooms = FeverHalls.ROOMS;
            int room = FeverHalls.ROOM;
            for (int x = -PLAIN; x <= PLAIN + 1; x++) {
                for (int z = -PLAIN; z <= PLAIN + 1; z++) {
                    KernDressing.put(level, mage, island.offset(x, 0, z), Blocks.WHITE_CONCRETE.defaultBlockState());
                    clear(level, mage, island.offset(x, 1, z), island.offset(x, 10, z));
                }
            }
            boolean[][] east = new boolean[rooms][rooms];
            boolean[][] south = new boolean[rooms][rooms];
            FeverHalls.maze(east, south, random);
            BlockState wall = Blocks.BARRIER.defaultBlockState();
            for (int k = 0; k <= rooms; k++) {
                int line = -PLAIN + k * room;
                for (int along = -PLAIN; along <= PLAIN + 1; along++) {
                    int j = Math.floorDiv(along + PLAIN, room);
                    int inRoom = Math.floorMod(along + PLAIN, room);
                    boolean middle = inRoom >= room / 2 - 1 && inRoom <= room / 2 + 1;
                    boolean inside = k > 0 && k < rooms && j >= 0 && j < rooms;
                    boolean openX = inside && middle && east[k - 1][j];
                    boolean openZ = inside && middle && south[j][k - 1];
                    for (int y = 1; y <= 3; y++) {
                        if (!openX) {
                            KernDressing.put(level, mage, island.offset(line, y, along), wall);
                        }
                        if (!openZ) {
                            KernDressing.put(level, mage, island.offset(along, y, line), wall);
                        }
                    }
                }
            }
            int center = rooms / 2;
            int ti;
            int tj;
            do {
                ti = random.nextInt(rooms);
                tj = random.nextInt(rooms);
            } while (Math.abs(ti - center) + Math.abs(tj - center) < 3);
            state.putInt("toX", -PLAIN + ti * room + room / 2 + 1);
            state.putInt("toZ", -PLAIN + tj * room + room / 2 + 1);
            state.putLong("shownFrom", level.getGameTime() + 60L);
            startAt(state, new Vec3(island.getX() - PLAIN + center * room + room / 2 + 1.5D, island.getY() + 1,
                    island.getZ() - PLAIN + center * room + room / 2 + 1.5D));
            return nowhere(island);
        }

        @Override
        public Vec3 start(BlockPos island, CompoundTag state) {
            return started(state);
        }

        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            int hereX = Mth.floor(player.getX()) - island.getX();
            int hereZ = Mth.floor(player.getZ()) - island.getZ();
            int toX = state.getInt("toX");
            int toZ = state.getInt("toZ");
            if (player.tickCount % 10 == 0 && player.level().getGameTime() >= state.getLong("shownFrom")) {
                player.displayClientMessage(Component.translatable("dream.elderlexicon.ubis.where", hereX, hereZ, toX,
                        toZ).withStyle(ChatFormatting.WHITE), true);
            }
            if (hereX == toX && hereZ == toZ) {
                // There: the place was the number, and the rift was in it.
                FeverDream.riftAt(dream, player.position().add(0.0D, player.getBbHeight() / 2.0D, 0.0D));
            }
        }
    };

    /** How many times the place is laid each way. */
    private static final int TILES = 2;

    /** ubis turned round: the same place everywhere; every way out a way back in, but down. */
    static final FeverVerbs.Rule SAME = new FeverVerbs.Rule() {
        @Override
        public BlockPos place(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, RandomSource random) {
            BodyMemories.Piece piece = home(level, island, state);
            for (int tx = -TILES; tx <= TILES; tx++) {
                for (int tz = -TILES; tz <= TILES; tz++) {
                    for (int x = 0; x < W; x++) {
                        for (int y = 0; y < H; y++) {
                            for (int z = 0; z < W; z++) {
                                BlockState here = sized(piece.at(x, y, z));
                                KernDressing.put(level, mage, tiled(island, tx, tz, x, y, z), here);
                            }
                        }
                    }
                }
            }
            // In every other copy, the rift: a frame of it, where in this one there is none.
            int fx = MID;
            int fz = W - 2;
            for (int tx = -1; tx <= 1; tx++) {
                for (int tz = -1; tz <= 1; tz++) {
                    if (tx == 0 && tz == 0) {
                        continue;
                    }
                    BlockPos frame = tiled(island, tx, tz, fx, BodyMemories.BELOW + 1, fz);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            KernDressing.put(level, mage, frame.offset(dx, dy, 0), dx == 0 && dy == 0 ? AIR
                                    : Blocks.CRYING_OBSIDIAN.defaultBlockState());
                        }
                    }
                }
            }
            // The one place that is not repeated: down, under the middle copy.
            BlockPos down = island.below(6);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        KernDressing.put(level, mage, down.offset(dx, dy, dz), AIR);
                    }
                    KernDressing.put(level, mage, down.offset(dx, -2, dz), Blocks.CRYING_OBSIDIAN.defaultBlockState());
                }
            }
            boolean[][][] solid = solid(piece);
            int[] from = standing(solid);
            BlockPos begin = tiled(island, 0, 0, from[0], from[1], from[2]);
            Vec3 start = new Vec3(begin.getX() + 0.5D, begin.getY(), begin.getZ() + 0.5D);
            startAt(state, start);
            FeverDream.thing(level, start.add(0.0D, 0.5D, 0.0D), new ItemStack(Items.IRON_PICKAXE));
            return down;
        }

        @Override
        public Vec3 start(BlockPos island, CompoundTag state) {
            return started(state);
        }

        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            // Out of the middle copy, one is in the middle copy again, on its other side.
            double low = island.getX() - MID;
            double lowZ = island.getZ() - MID;
            double x = player.getX();
            double z = player.getZ();
            if (x < low) {
                x += W;
            } else if (x >= low + W) {
                x -= W;
            }
            if (z < lowZ) {
                z += W;
            } else if (z >= lowZ + W) {
                z -= W;
            }
            if (x != player.getX() || z != player.getZ()) {
                player.teleportTo(x, player.getY(), z);
            }
            if (player.tickCount % 10 == 0) {
                for (int tx = -1; tx <= 1; tx++) {
                    for (int tz = -1; tz <= 1; tz++) {
                        if (tx != 0 || tz != 0) {
                            Vec3 frame = Vec3.atCenterOf(tiled(island, tx, tz, MID, BodyMemories.BELOW + 1, W - 2));
                            player.serverLevel().sendParticles(player, ParticleTypes.REVERSE_PORTAL, true, frame.x,
                                    frame.y, frame.z, 10, 0.4D, 0.4D, 0.1D, 0.02D);
                        }
                    }
                }
            }
        }

        @Override
        public void broke(ServerPlayer player, BlockPos island, CompoundTag state, BlockPos pos) {
            // What is broken in one copy is broken in all: it is the same place.
            int x = Math.floorMod(pos.getX() - (island.getX() - MID), W);
            int z = Math.floorMod(pos.getZ() - (island.getZ() - MID), W);
            int y = pos.getY() - island.getY() + 1;
            if (y < 0 || y >= H) {
                return;
            }
            for (int tx = -TILES; tx <= TILES; tx++) {
                for (int tz = -TILES; tz <= TILES; tz++) {
                    KernDressing.put(player.serverLevel(), player.getUUID(), tiled(island, tx, tz, x, y, z), AIR);
                }
            }
        }
    };

    /** The block {@code (x, y, z)} of the copy {@code (tx, tz)} of the place, the middle copy's middle at the island. */
    private static BlockPos tiled(BlockPos island, int tx, int tz, int x, int y, int z) {
        return island.offset(tx * W + x - MID, y - 1, tz * W + z - MID);
    }

    // ------------------------------------------------------------------ tenet

    private static final int MASS = 7;
    private static final int MASS_DEPTH = 10;
    private static final BlockState GREY = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();

    /**
     * tenet: from the world. A mass of the place the mage lay down in; whatever is touched is drunk, food and life, and
     * what is round it goes grey, and grey gives nothing. The hunger does not stop; the rift is in the middle.
     */
    static final FeverVerbs.Rule DRAIN = new FeverVerbs.Rule() {
        @Override
        public BlockPos place(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, RandomSource random) {
            BodyMemories.Piece piece = home(level, island, state);
            List<BlockState> stuff = new ArrayList<>();
            for (int x = 0; x < W; x++) {
                for (int y = 0; y < H; y++) {
                    for (int z = 0; z < W; z++) {
                        BlockState here = sized(piece.at(x, y, z));
                        if (here.isSolid() && !here.is(Blocks.LIGHT_GRAY_CONCRETE) && !here.isAir()) {
                            stuff.add(here);
                        }
                    }
                }
            }
            if (stuff.size() < 4) {
                stuff.addAll(List.of(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                        Blocks.STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState()));
            }
            for (int x = -MASS - 2; x <= MASS + 2; x++) {
                for (int z = -MASS - 2; z <= MASS + 2; z++) {
                    clear(level, mage, island.offset(x, 1, z), island.offset(x, 10, z));
                    if (Math.abs(x) <= MASS && Math.abs(z) <= MASS) {
                        for (int y = -MASS_DEPTH; y <= 0; y++) {
                            KernDressing.put(level, mage, island.offset(x, y, z), stuff.get(random.nextInt(stuff.size())));
                        }
                    }
                }
            }
            BlockPos rift = island.below(MASS_DEPTH / 2);
            KernDressing.put(level, mage, rift, AIR);
            startAt(state, new Vec3(island.getX() + 0.5D, island.getY() + 1, island.getZ() + 0.5D));
            return rift;
        }

        @Override
        public Vec3 start(BlockPos island, CompoundTag state) {
            return started(state);
        }

        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            if (player.tickCount % 20 == 0) {
                player.causeFoodExhaustion(2.0F);
                if (player.getFoodData().getFoodLevel() <= 0) {
                    player.hurt(player.damageSources().starve(), 1.0F);
                }
            }
        }

        @Override
        public boolean hit(ServerPlayer player, BlockPos island, CompoundTag state, BlockPos pos) {
            int x = pos.getX() - island.getX();
            int y = pos.getY() - island.getY();
            int z = pos.getZ() - island.getZ();
            if (Math.abs(x) > MASS || Math.abs(z) > MASS || y > 0 || y < -MASS_DEPTH) {
                return false;
            }
            ServerLevel level = player.serverLevel();
            BlockState drunk = level.getBlockState(pos);
            if (drunk.isAir()) {
                return true;
            }
            KernDressing.put(level, player.getUUID(), pos, AIR);
            if (drunk.equals(GREY)) {
                player.playNotifySound(SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.6F, 0.6F);
                return true;
            }
            // Drunk: food and life from it; and what was round it gone grey.
            player.getFoodData().eat(1, 0.1F);
            player.heal(1.0F);
            level.sendParticles(player, new BlockParticleOption(ParticleTypes.BLOCK, drunk), false,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 16, 0.3D, 0.3D, 0.3D, 0.1D);
            player.playNotifySound(SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6F, 0.8F);
            for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                int nx = near.getX() - island.getX();
                int ny = near.getY() - island.getY();
                int nz = near.getZ() - island.getZ();
                if (Math.abs(nx) <= MASS && Math.abs(nz) <= MASS && ny <= 0 && ny >= -MASS_DEPTH
                        && !level.getBlockState(near).isAir() && player.getRandom().nextFloat() < 0.5F) {
                    KernDressing.put(level, player.getUUID(), near.immutable(), GREY);
                }
            }
            return true;
        }
    };

    /** Where the void of the body is, over the kern; how far the rift is from it, and how much higher. */
    private static final int VOID_UP = 22;
    private static final int VOID_FAR = 18;
    private static final int VOID_RISE = 4;

    /**
     * tenet turned round: from the body. A void and a small floor; with the hand empty, a block is made of the
     * dreamer: of their life, or crouched, of what they know (a level).
     */
    static final FeverVerbs.Rule BODY = new FeverVerbs.Rule() {
        @Override
        public BlockPos place(ServerLevel level, UUID mage, BlockPos island, CompoundTag state, RandomSource random) {
            BlockPos floor = island.offset(0, VOID_UP, -VOID_FAR / 2);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    KernDressing.put(level, mage, floor.offset(x, 0, z), Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
            startAt(state, Vec3.atBottomCenterOf(floor.above()));
            return floor.offset(0, VOID_RISE + 2, VOID_FAR);
        }

        @Override
        public Vec3 start(BlockPos island, CompoundTag state) {
            return started(state);
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            player.fallDistance = 0.0F;
            if (player.onGround() && player.getY() >= started(state).y - 0.5D) {
                state.putDouble("startX", player.getX());
                state.putDouble("startY", player.getY());
                state.putDouble("startZ", player.getZ());
            }
            caught(player, state, island.getY() + VOID_UP - 8);
        }

        @Override
        public boolean used(ServerPlayer player, BlockPos island, CompoundTag state, BlockPos pos, Direction face,
                            ItemStack held) {
            if (!held.isEmpty()) {
                return false;
            }
            ServerLevel level = player.serverLevel();
            BlockPos at = pos.relative(face);
            if (!level.getBlockState(at).isAir() || player.getBoundingBox().intersects(
                    new net.minecraft.world.phys.AABB(at))) {
                return true;
            }
            if (player.isShiftKeyDown() && player.experienceLevel > 0) {
                player.giveExperienceLevels(-1);
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 0.5F);
            } else {
                player.hurt(player.damageSources().magic(), 1.0F);
            }
            KernDressing.put(level, player.getUUID(), at, Blocks.BONE_BLOCK.defaultBlockState());
            player.playNotifySound(SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
            return true;
        }
    };
}
