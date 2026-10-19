package com.elderlexicon.mod.nahva;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * The halls of a fever dream (docs/aprendizado-runas-design.md, 4b): the kern turned into a castle of rooms that make
 * no sense, a liminal place, but of the world it is, stone and wood and candlelight. A grid of rooms over the island,
 * joined by narrow doorways as a maze; rooms that repeat, a single chair facing a wall, a table laid for no one, shelves
 * with no way out, a stair that ends in the ceiling, a bed alone in a hall too big, a well with no bottom, a doorway
 * onto a wall; and doorways that do not go where they should: through them, the dreamer is somewhere else in the
 * castle. Each scene of the dream builds them anew in its own stuff: the heat in nether bricks and embers, the cold in
 * ice and snow, the memories in old stone and wood by candlelight, with the dreamer's memories inside some rooms.
 */
final class FeverHalls {

    /** The grid: rooms across, each this wide outside, its floor where the island's top is. */
    static final int ROOMS = 5;
    static final int ROOM = 9;
    private static final int HALF = ROOMS * ROOM / 2;
    /** How high a room is, at the most: what is looked at round the halls. */
    private static final int TALLEST = 6;

    /** What a room has in it. */
    private enum Kind {
        EMPTY, PILLARS, CHAIR, TABLE, SHELVES, STAIR, BED, WELL, FALSE_DOOR
    }

    /**
     * The stuff of a scene's halls: floors, walls, ceiling, the wood of their furniture, their light (a lantern hung,
     * candles, or a lamp set in the ceiling), how high they are, and what more there is to them: some rooms flooded to
     * the ceiling, winds that blow through the rooms, something strewn on the floors.
     */
    record Stuff(Block floor, Block floorAlt, Block wall, Block wallAlt, Block ceiling, Block stairs, Block fence,
                 Block slab, Block shelf, Block light, int height, boolean flooded, boolean windy,
                 @org.jetbrains.annotations.Nullable Block strewn, Opening opening, boolean apart, boolean uniform,
                 int elsewheres) {

        /** Halls with doorways, rooms side by side, of every kind, two pairs of doorways that lead elsewhere. */
        Stuff(Block floor, Block floorAlt, Block wall, Block wallAlt, Block ceiling, Block stairs, Block fence,
              Block slab, Block shelf, Block light, int height, boolean flooded, boolean windy,
              @org.jetbrains.annotations.Nullable Block strewn) {
            this(floor, floorAlt, wall, wallAlt, ceiling, stairs, fence, slab, shelf, light, height, flooded, windy,
                    strewn, Opening.DOOR, false, false, 2);
        }

        /** The same halls, otherwise: their openings, the rooms apart, all alike, so many doorways elsewhere. */
        Stuff otherwise(Opening opening, boolean apart, boolean uniform, int elsewheres) {
            return new Stuff(floor, floorAlt, wall, wallAlt, ceiling, stairs, fence, slab, shelf, light, height,
                    flooded, windy, strewn, opening, apart, uniform, elsewheres);
        }
    }

    /**
     * How the rooms open on one another: doorways one wide and three high, or three wide (to be thrown through); windows,
     * a hole at the height of the eyes, that nothing passes but a look; or nothing at all.
     */
    enum Opening {
        DOOR, WIDE, WINDOW, NONE
    }

    /** The memories, of every rune: old stone and wood, by candlelight. */
    static final Stuff OLD = new Stuff(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS, Blocks.DARK_OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_FENCE, Blocks.OAK_SLAB,
            Blocks.BOOKSHELF, Blocks.CANDLE, 6, false, false, null);
    /** igni: the heat, nether brick and embers; the cold, ice and snow. */
    static final Stuff HEAT = new Stuff(Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, Blocks.NETHER_BRICKS,
            Blocks.CRACKED_NETHER_BRICKS, Blocks.BASALT, Blocks.CRIMSON_STAIRS, Blocks.CRIMSON_FENCE,
            Blocks.CRIMSON_SLAB, Blocks.CRIMSON_PLANKS, Blocks.LANTERN, 6, false, false, null);
    static final Stuff COLD = new Stuff(Blocks.BLUE_ICE, Blocks.PACKED_ICE, Blocks.PACKED_ICE,
            Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_SLAB,
            Blocks.SPRUCE_PLANKS, Blocks.LANTERN, 6, false, false, null);
    /** aqua: the flood, prismarine and rooms full of water; the drought, sandstone, cracked clay and dead bushes. */
    static final Stuff FLOOD = new Stuff(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE,
            Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.WARPED_FENCE,
            Blocks.PRISMARINE_BRICK_SLAB, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, 6, true, false, null);
    static final Stuff DROUGHT = new Stuff(Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA, Blocks.SANDSTONE,
            Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.ACACIA_FENCE,
            Blocks.SANDSTONE_SLAB, Blocks.ACACIA_PLANKS, Blocks.LANTERN, 6, false, false, Blocks.DEAD_BUSH);
    /** aura: the gale, pale stone and winds through the rooms; the void, deepslate, cobwebs and no sound. */
    static final Stuff GALE = new Stuff(Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.CALCITE,
            Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_STAIRS, Blocks.BIRCH_FENCE, Blocks.QUARTZ_SLAB, Blocks.BIRCH_PLANKS,
            Blocks.LANTERN, 6, false, true, null);
    static final Stuff VOID = new Stuff(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS,
            Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICK_STAIRS,
            Blocks.DARK_OAK_FENCE, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.SOUL_LANTERN, 6, false,
            false, Blocks.COBWEB);
    /** firmo: the weight, low rooms of deepslate and tuff; the burial, earth and stone, walls that close in. */
    static final Stuff WEIGHT = new Stuff(Blocks.POLISHED_ANDESITE, Blocks.ANDESITE, Blocks.COBBLED_DEEPSLATE,
            Blocks.TUFF, Blocks.STONE, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.SPRUCE_FENCE,
            Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.LANTERN, 3, false, false, null);
    static final Stuff BURIAL = new Stuff(Blocks.ROOTED_DIRT, Blocks.COARSE_DIRT, Blocks.STONE, Blocks.COBBLESTONE,
            Blocks.STONE, Blocks.STONE_STAIRS, Blocks.OAK_FENCE, Blocks.STONE_SLAB, Blocks.OAK_PLANKS, Blocks.LANTERN, 5,
            false, false, null);

    private FeverHalls() {
    }

    /** The middle of the grid's room {@code (i, j)}, on its floor, from {@code island} (the island's top middle). */
    static BlockPos middle(BlockPos island, int i, int j) {
        return corner(island, i, j).offset(ROOM / 2, 1, ROOM / 2);
    }

    static BlockPos corner(BlockPos island, int i, int j) {
        return island.offset(-HALF + i * ROOM, 0, -HALF + j * ROOM);
    }

    /** Where the dreamer begins: the middle room. */
    static Vec3 spawn(BlockPos island) {
        return Vec3.atBottomCenterOf(middle(island, ROOMS / 2, ROOMS / 2));
    }

    /** The room of the grid {@code at} is in, or null outside it. */
    static int[] roomAt(BlockPos island, Vec3 at) {
        int i = (int) Math.floor((at.x - island.getX() + HALF) / ROOM);
        int j = (int) Math.floor((at.z - island.getZ() + HALF) / ROOM);
        double dy = at.y - island.getY();
        return i >= 0 && i < ROOMS && j >= 0 && j < ROOMS && dy > -2 && dy < TALLEST + 3 ? new int[]{i, j} : null;
    }

    /**
     * The halls of a scene, built on the kern of {@code stuff}, with the pieces of the dreamer's memories in some rooms
     * (turned by {@code swap}) when there are any. Where the doorways that lead elsewhere are, and the winds, go in
     * {@code state}. Returns where the rift is, in a room far from the middle.
     */
    static BlockPos build(ServerLevel level, UUID mage, BlockPos island, Stuff stuff, List<BodyMemories.Piece> pieces,
                          java.util.function.UnaryOperator<BlockState> swap, RandomSource random, CompoundTag state) {
        boolean memory = !pieces.isEmpty();
        boolean[][] eastOpen = new boolean[ROOMS][ROOMS];
        boolean[][] southOpen = new boolean[ROOMS][ROOMS];
        maze(eastOpen, southOpen, random);
        // Every room: the same kind as the first comes back here and there (a room seen before).
        Kind[][] kinds = new Kind[ROOMS][ROOMS];
        Kind repeated = Kind.values()[1 + random.nextInt(Kind.values().length - 1)];
        for (int i = 0; i < ROOMS; i++) {
            for (int j = 0; j < ROOMS; j++) {
                kinds[i][j] = stuff.uniform() || stuff.apart() ? Kind.EMPTY
                        : random.nextFloat() < 0.25F ? repeated : Kind.values()[random.nextInt(Kind.values().length)];
            }
        }
        int center = ROOMS / 2;
        kinds[center][center] = Kind.EMPTY;
        // The rift is in a room far from where the dreamer begins.
        int ri;
        int rj;
        do {
            ri = random.nextInt(ROOMS);
            rj = random.nextInt(ROOMS);
        } while (Math.abs(ri - center) + Math.abs(rj - center) < 3);
        kinds[ri][rj] = Kind.EMPTY;
        for (int i = 0; i < ROOMS; i++) {
            for (int j = 0; j < ROOMS; j++) {
                room(level, mage, corner(island, i, j), stuff, kinds[i][j], random);
            }
        }
        // The doorways of the maze, through both walls between two rooms. Rooms apart open on every side, onto the
        // gap between them.
        for (int i = 0; i < ROOMS; i++) {
            for (int j = 0; j < ROOMS; j++) {
                BlockPos at = corner(island, i, j);
                if (stuff.apart()) {
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        doorway(level, mage, at.offset(ROOM / 2, 0, ROOM / 2).relative(side, ROOM / 2 - 1), stuff);
                    }
                    continue;
                }
                if (stuff.opening() == Opening.NONE) {
                    continue;
                }
                if (i + 1 < ROOMS && eastOpen[i][j]) {
                    doorway(level, mage, at.offset(ROOM - 1, 0, ROOM / 2), stuff, Direction.Axis.Z);
                    doorway(level, mage, at.offset(ROOM, 0, ROOM / 2), stuff, Direction.Axis.Z);
                }
                if (j + 1 < ROOMS && southOpen[i][j]) {
                    doorway(level, mage, at.offset(ROOM / 2, 0, ROOM - 1), stuff, Direction.Axis.X);
                    doorway(level, mage, at.offset(ROOM / 2, 0, ROOM), stuff, Direction.Axis.X);
                }
            }
        }
        if (!stuff.apart()) {
            elsewheres(level, mage, island, stuff, random, state);
        } else {
            state.put("elsewheres", new ListTag());
        }
        if (memory) {
            // The memories, inside rooms, the rift's among them.
            List<int[]> rooms = new ArrayList<>();
            rooms.add(new int[]{ri, rj});
            for (int n = 0; n < pieces.size() * 2 && rooms.size() < pieces.size(); n++) {
                int[] other = {random.nextInt(ROOMS), random.nextInt(ROOMS)};
                if (!(other[0] == center && other[1] == center) && rooms.stream()
                        .noneMatch(room -> room[0] == other[0] && room[1] == other[1])) {
                    rooms.add(other);
                }
            }
            for (int n = 0; n < rooms.size(); n++) {
                inside(level, mage, corner(island, rooms.get(n)[0], rooms.get(n)[1]), pieces.get(n % pieces.size()),
                        stuff, swap, random);
            }
        }
        int[] pools = new int[ROOMS * ROOMS];
        if (stuff.flooded()) {
            // Some rooms sunk into deep pools: the one the dreamer begins in never; the rift's always, at its bottom.
            for (int i = 0; i < ROOMS; i++) {
                for (int j = 0; j < ROOMS; j++) {
                    boolean rift = i == ri && j == rj;
                    if (rift || !(i == center && j == center) && random.nextFloat() < 0.4F) {
                        flood(level, mage, corner(island, i, j), stuff);
                        pools[i * ROOMS + j] = 1;
                    }
                }
            }
        }
        state.putIntArray("pools", pools);
        int[] winds = new int[ROOMS * ROOMS];
        for (int n = 0; n < winds.length; n++) {
            winds[n] = stuff.windy() && random.nextFloat() < 0.6F ? random.nextInt(4) : -1;
        }
        state.putIntArray("winds", winds);
        BlockPos rift = stuff.flooded() ? middle(island, ri, rj).below(POOL / 2 + 1)
                : middle(island, ri, rj).above(Math.min(2, stuff.height() - 2));
        // Room round the rift: open air, or open water at the bottom of a pool.
        BlockState round = stuff.flooded() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        for (BlockPos clear : BlockPos.betweenClosed(rift.offset(-1, -1, -1), rift.offset(1, 1, 1))) {
            KernDressing.put(level, mage, clear.immutable(), round);
        }
        return rift;
    }

    // ------------------------------------------------------------------ the maze

    /** A maze over the grid (every room reached, one way between any two), and a few more doorways besides. */
    private static void maze(boolean[][] east, boolean[][] south, RandomSource random) {
        boolean[][] seen = new boolean[ROOMS][ROOMS];
        Deque<int[]> path = new ArrayDeque<>();
        path.push(new int[]{ROOMS / 2, ROOMS / 2});
        seen[ROOMS / 2][ROOMS / 2] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!path.isEmpty()) {
            int[] at = path.peek();
            List<int[]> next = new ArrayList<>();
            for (int[] step : steps) {
                int i = at[0] + step[0];
                int j = at[1] + step[1];
                if (i >= 0 && i < ROOMS && j >= 0 && j < ROOMS && !seen[i][j]) {
                    next.add(new int[]{i, j});
                }
            }
            if (next.isEmpty()) {
                path.pop();
                continue;
            }
            int[] to = next.get(random.nextInt(next.size()));
            open(east, south, at, to);
            seen[to[0]][to[1]] = true;
            path.push(to);
        }
        for (int n = 0; n < 4; n++) {
            int i = random.nextInt(ROOMS - 1);
            int j = random.nextInt(ROOMS - 1);
            if (random.nextBoolean()) {
                east[i][j] = true;
            } else {
                south[i][j] = true;
            }
        }
    }

    private static void open(boolean[][] east, boolean[][] south, int[] a, int[] b) {
        if (a[1] == b[1]) {
            east[Math.min(a[0], b[0])][a[1]] = true;
        } else {
            south[a[0]][Math.min(a[1], b[1])] = true;
        }
    }

    // ------------------------------------------------------------------ a room

    private static void room(ServerLevel level, UUID mage, BlockPos at, Stuff stuff, Kind kind, RandomSource random) {
        int height = stuff.height();
        int lo = stuff.apart() ? 1 : 0;
        int hi = ROOM - 1 - lo;
        for (int x = 0; x < ROOM; x++) {
            for (int z = 0; z < ROOM; z++) {
                if (x < lo || z < lo || x > hi || z > hi) {
                    // The gap round a room apart: nothing, down into the dark.
                    for (int y = -GAP; y <= TALLEST + 1; y++) {
                        put(level, mage, at.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    continue;
                }
                boolean edge = x == lo || z == lo || x == hi || z == hi;
                put(level, mage, at.offset(x, 0, z), ((x + z) % 2 == 0 ? stuff.floor() : stuff.floorAlt())
                        .defaultBlockState());
                // Up to the tallest halls of any scene: what a taller scene left above a low ceiling is cleared too.
                for (int y = 1; y <= TALLEST + 1; y++) {
                    BlockState wall = (!stuff.uniform() && random.nextFloat() < 0.15F ? stuff.wallAlt() : stuff.wall())
                            .defaultBlockState();
                    BlockState here = y > height + 1 ? Blocks.AIR.defaultBlockState()
                            : y == height + 1 ? stuff.ceiling().defaultBlockState()
                            : edge ? wall : Blocks.AIR.defaultBlockState();
                    put(level, mage, at.offset(x, y, z), here);
                }
            }
        }
        BlockPos middle = at.offset(ROOM / 2, 0, ROOM / 2);
        // The light, dim either way: candles in the corners, a lantern hung from the ceiling, or a lamp set in it.
        if (stuff.light() == Blocks.AIR) {
            // No light at all.
        } else if (stuff.light() == Blocks.CANDLE) {
            BlockState candles = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3)
                    .setValue(CandleBlock.LIT, true);
            put(level, mage, at.offset(1, 1, 1), candles);
            put(level, mage, at.offset(ROOM - 2, 1, ROOM - 2), candles);
        } else if (stuff.light() instanceof LanternBlock) {
            put(level, mage, middle.above(height), stuff.light().defaultBlockState().setValue(LanternBlock.HANGING,
                    true));
        } else {
            put(level, mage, middle.above(height + 1), stuff.light().defaultBlockState());
        }
        if (stuff.strewn() != null) {
            for (int n = 0; n < 3; n++) {
                BlockPos on = at.offset(1 + random.nextInt(ROOM - 2), 1, 1 + random.nextInt(ROOM - 2));
                if (level.getBlockState(on).isAir()) {
                    put(level, mage, on, stuff.strewn().defaultBlockState());
                }
            }
        }
        switch (kind) {
            case EMPTY -> {
            }
            case PILLARS -> {
                for (int x = 2; x < ROOM - 1; x += 2) {
                    for (int z = 2; z < ROOM - 1; z += 2) {
                        for (int y = 1; y <= height; y++) {
                            put(level, mage, at.offset(x, y, z), stuff.wall().defaultBlockState());
                        }
                    }
                }
            }
            // A single chair, facing the wall.
            case CHAIR -> put(level, mage, at.offset(ROOM / 2, 1, 2), stuff.stairs().defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
            // A long table, laid for no one: its chairs all pushed back.
            case TABLE -> {
                for (int x = 2; x <= ROOM - 3; x++) {
                    put(level, mage, middle.offset(x - ROOM / 2, 1, 0), stuff.fence().defaultBlockState());
                    put(level, mage, middle.offset(x - ROOM / 2, 2, 0), stuff.slab().defaultBlockState());
                    if (x % 2 == 0) {
                        put(level, mage, middle.offset(x - ROOM / 2, 1, -2), stuff.stairs().defaultBlockState()
                                .setValue(StairBlock.FACING, Direction.SOUTH));
                        put(level, mage, middle.offset(x - ROOM / 2, 1, 2), stuff.stairs().defaultBlockState()
                                .setValue(StairBlock.FACING, Direction.NORTH));
                    }
                }
            }
            // Shelves on every wall, to the ceiling.
            case SHELVES -> {
                for (int x = 1; x < ROOM - 1; x++) {
                    for (int y = 1; y <= height; y++) {
                        put(level, mage, at.offset(x, y, 1), stuff.shelf().defaultBlockState());
                        put(level, mage, at.offset(x, y, ROOM - 2), stuff.shelf().defaultBlockState());
                    }
                }
            }
            // A stair that climbs to the ceiling, and ends there.
            case STAIR -> {
                for (int n = 0; n < height; n++) {
                    put(level, mage, at.offset(2 + n, 1 + n, ROOM / 2), stuff.stairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST));
                    for (int below = 1; below <= n; below++) {
                        put(level, mage, at.offset(2 + n, below, ROOM / 2), stuff.wall().defaultBlockState());
                    }
                }
            }
            // A bed, alone, in the middle.
            case BED -> {
                BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
                put(level, mage, middle.above(), bed.setValue(BedBlock.PART, BedPart.FOOT));
                put(level, mage, middle.above().north(), bed.setValue(BedBlock.PART, BedPart.HEAD));
            }
            // A well with no bottom.
            case WELL -> {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        boolean rim = x != 0 || z != 0;
                        put(level, mage, middle.offset(x, 1, z), rim ? Blocks.COBBLESTONE_WALL.defaultBlockState()
                                : Blocks.AIR.defaultBlockState());
                        for (int down = 0; down >= -5; down--) {
                            put(level, mage, middle.offset(x, down, z), rim ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                put(level, mage, middle.below(6), Blocks.BLACK_CONCRETE.defaultBlockState());
            }
            // A doorway, framed, onto the wall behind it.
            case FALSE_DOOR -> {
                int top = Math.min(3, height);
                for (int y = 1; y <= top; y++) {
                    put(level, mage, at.offset(ROOM / 2 - 1, y, 1), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
                    put(level, mage, at.offset(ROOM / 2 + 1, y, 1), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
                }
                if (top < height) {
                    put(level, mage, at.offset(ROOM / 2, top + 1, 1), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
                }
            }
        }
    }

    /** A doorway through the wall at {@code at} (on the floor): one wide, three high; or a window, at the eyes. */
    private static void doorway(ServerLevel level, UUID mage, BlockPos at, Stuff stuff) {
        doorway(level, mage, at, stuff, null);
    }

    /** The same, a wide one widened along {@code along}, the way the wall runs. */
    private static void doorway(ServerLevel level, UUID mage, BlockPos at, Stuff stuff,
                                @org.jetbrains.annotations.Nullable Direction.Axis along) {
        if (stuff.opening() == Opening.WINDOW) {
            put(level, mage, at.above(2), Blocks.AIR.defaultBlockState());
            return;
        }
        for (int y = 1; y <= 3; y++) {
            put(level, mage, at.above(y), Blocks.AIR.defaultBlockState());
            if (stuff.opening() == Opening.WIDE && along != null) {
                // Three wide: along the wall on both sides.
                for (BlockPos side : along == Direction.Axis.Z ? new BlockPos[]{at.above(y).north(), at.above(y).south()}
                        : new BlockPos[]{at.above(y).east(), at.above(y).west()}) {
                    put(level, mage, side, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** How deep the dark goes under the gaps and the chasms. */
    static final int GAP = 6;

    /** The room {@code (i, j)} walled round again: its doorways shut, a room with no way in. */
    static void seal(ServerLevel level, UUID mage, BlockPos island, int i, int j, Stuff stuff) {
        BlockPos at = corner(island, i, j);
        for (int x = 0; x < ROOM; x++) {
            for (int z = 0; z < ROOM; z++) {
                if (x == 0 || z == 0 || x == ROOM - 1 || z == ROOM - 1) {
                    for (int y = 1; y <= stuff.height(); y++) {
                        put(level, mage, at.offset(x, y, z), stuff.wall().defaultBlockState());
                    }
                }
            }
        }
    }

    /**
     * The floor of the room {@code (i, j)} gone: a chasm down into the dark, walled by the kern's earth, its bottom
     * {@code bottom} (slime, for the throws of iactare: what falls in is thrown back up).
     */
    static void chasm(ServerLevel level, UUID mage, BlockPos island, int i, int j, Block bottom) {
        BlockPos at = corner(island, i, j);
        for (int x = 1; x < ROOM - 1; x++) {
            for (int z = 1; z < ROOM - 1; z++) {
                for (int y = 0; y >= -GAP; y--) {
                    put(level, mage, at.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                put(level, mage, at.offset(x, -GAP - 1, z), bottom.defaultBlockState());
            }
        }
    }

    /** Every wall and floor of the halls made of {@code wall} and {@code floor}: the openings are left open. */
    static void repaint(ServerLevel level, UUID mage, BlockPos island, Stuff stuff, Block wall, Block floor) {
        for (int i = 0; i < ROOMS; i++) {
            for (int j = 0; j < ROOMS; j++) {
                BlockPos at = corner(island, i, j);
                for (int x = 0; x < ROOM; x++) {
                    for (int z = 0; z < ROOM; z++) {
                        boolean edge = x == 0 || z == 0 || x == ROOM - 1 || z == ROOM - 1;
                        if (!level.getBlockState(at.offset(x, 0, z)).isAir()) {
                            put(level, mage, at.offset(x, 0, z), floor.defaultBlockState());
                        }
                        for (int y = 1; edge && y <= stuff.height(); y++) {
                            if (!level.getBlockState(at.offset(x, y, z)).isAir()) {
                                put(level, mage, at.offset(x, y, z), wall.defaultBlockState());
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the doorways that lead elsewhere

    /**
     * Doorways in the castle's outer walls that do not lead out: through one, the dreamer comes in through its pair,
     * on the other side of the castle. Kept in {@code state} as pairs of (doorway, the way into its room).
     */
    private static void elsewheres(ServerLevel level, UUID mage, BlockPos island, Stuff stuff, RandomSource random,
                                   CompoundTag state) {
        List<long[]> doors = new ArrayList<>();
        for (int n = 0; n < stuff.elsewheres() * 2; n++) {
            Direction side = Direction.from2DDataValue(n % 4);
            int k = random.nextInt(ROOMS);
            int i = side == Direction.EAST ? ROOMS - 1 : side == Direction.WEST ? 0 : k;
            int j = side == Direction.SOUTH ? ROOMS - 1 : side == Direction.NORTH ? 0 : k;
            BlockPos middle = corner(island, i, j).offset(ROOM / 2, 0, ROOM / 2);
            BlockPos door = middle.relative(side, ROOM / 2);
            doorway(level, mage, door, stuff);
            // Beyond it, nothing to see: a wall of the dark.
            for (int y = 1; y <= 3; y++) {
                put(level, mage, door.relative(side).above(y), Blocks.BLACK_CONCRETE.defaultBlockState());
            }
            doors.add(new long[]{door.above().asLong(), side.getOpposite().get2DDataValue()});
        }
        ListTag pairs = new ListTag();
        for (int n = 0; n + 1 < doors.size(); n += 2) {
            CompoundTag pair = new CompoundTag();
            pair.putLong("a", doors.get(n)[0]);
            pair.putInt("aIn", (int) doors.get(n)[1]);
            pair.putLong("b", doors.get(n + 1)[0]);
            pair.putInt("bIn", (int) doors.get(n + 1)[1]);
            pairs.add(pair);
        }
        state.put("elsewheres", pairs);
    }

    /** Each tick: one stepping into a doorway that leads elsewhere comes in through its pair. */
    static void step(ServerPlayer player, CompoundTag state) {
        if (player.level().getGameTime() < state.getLong("steppedAt") + 20) {
            return;
        }
        BlockPos feet = player.blockPosition();
        ListTag pairs = state.getList("elsewheres", Tag.TAG_COMPOUND);
        for (int n = 0; n < pairs.size(); n++) {
            CompoundTag pair = pairs.getCompound(n);
            BlockPos a = BlockPos.of(pair.getLong("a"));
            BlockPos b = BlockPos.of(pair.getLong("b"));
            if (within(feet, a)) {
                through(player, b, Direction.from2DDataValue(pair.getInt("bIn")));
            } else if (within(feet, b)) {
                through(player, a, Direction.from2DDataValue(pair.getInt("aIn")));
            } else {
                continue;
            }
            state.putLong("steppedAt", player.level().getGameTime());
            return;
        }
    }

    private static boolean within(BlockPos feet, BlockPos door) {
        return feet.getX() == door.getX() && feet.getZ() == door.getZ() && feet.getY() >= door.getY() - 1
                && feet.getY() <= door.getY() + 2;
    }

    /** In through {@code door}, a step into its room, facing into it. */
    private static void through(ServerPlayer player, BlockPos door, Direction in) {
        Vec3 at = Vec3.atBottomCenterOf(door.relative(in, 2));
        player.connection.teleport(at.x, at.y, at.z, in.toYRot(), player.getXRot());
    }

    // ------------------------------------------------------------------ the memories in a room

    /** A memory inside a room, as much of it as the room holds: turned, and as the rune swaps what it is. */
    private static void inside(ServerLevel level, UUID mage, BlockPos at, BodyMemories.Piece piece, Stuff stuff,
                               java.util.function.UnaryOperator<BlockState> swap, RandomSource random) {
        int inner = ROOM - 2;
        int off = (BodyMemories.Piece.WIDTH - inner) / 2;
        net.minecraft.world.level.block.Rotation rotation = net.minecraft.world.level.block.Rotation.getRandom(random);
        for (int x = 0; x < inner; x++) {
            for (int z = 0; z < inner; z++) {
                for (int y = 0; y < Math.min(stuff.height(), BodyMemories.Piece.HEIGHT); y++) {
                    BlockState state = piece.at(x + off, y, z + off);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockPos turned = new BlockPos(x - inner / 2, 0, z - inner / 2).rotate(rotation);
                    put(level, mage, at.offset(ROOM / 2 + turned.getX(), y + 1, ROOM / 2 + turned.getZ()),
                            swap.apply(state).rotate(rotation));
                }
            }
        }
    }

    /**
     * A room sunk into a deep pool: its floor gone, water down to {@link #POOL} blocks, walled and floored in the scene's
     * stone. The water's top is at the floor's level and the doorways are above it, so it stays in the pool: water let
     * loose in the halls would wash away what stands in them.
     */
    private static void flood(ServerLevel level, UUID mage, BlockPos at, Stuff stuff) {
        for (int x = 0; x < ROOM; x++) {
            for (int z = 0; z < ROOM; z++) {
                boolean rim = x == 0 || z == 0 || x == ROOM - 1 || z == ROOM - 1;
                for (int y = 0; y >= -POOL; y--) {
                    BlockState here = rim || y == -POOL ? stuff.wall().defaultBlockState()
                            : Blocks.WATER.defaultBlockState();
                    put(level, mage, at.offset(x, y, z), here);
                }
            }
        }
    }

    /** How deep a pool of the flood is. */
    static final int POOL = 6;

    /** Whether the room {@code (i, j)} was sunk into a pool, by the record kept in {@code state}. */
    static boolean pool(CompoundTag state, int i, int j) {
        int[] pools = state.getIntArray("pools");
        return pools.length == ROOMS * ROOMS && pools[i * ROOMS + j] != 0;
    }

    /** The way the wind blows in the room {@code at} is in, if it blows there. */
    @org.jetbrains.annotations.Nullable
    static Direction wind(BlockPos island, Vec3 at, CompoundTag state) {
        int[] room = roomAt(island, at);
        int[] winds = state.getIntArray("winds");
        if (room == null || winds.length != ROOMS * ROOMS) {
            return null;
        }
        int way = winds[room[0] * ROOMS + room[1]];
        return way < 0 ? null : Direction.from2DDataValue(way);
    }

    /** The walls of every room come in by one more row, {@code inset} from where they were: the halls close in. */
    static void closeIn(ServerLevel level, UUID mage, BlockPos island, Stuff stuff, int inset) {
        for (int i = 0; i < ROOMS; i++) {
            for (int j = 0; j < ROOMS; j++) {
                BlockPos at = corner(island, i, j);
                for (int x = inset; x < ROOM - inset; x++) {
                    for (int z = inset; z < ROOM - inset; z++) {
                        boolean ring = x == inset || z == inset || x == ROOM - 1 - inset || z == ROOM - 1 - inset;
                        if (!ring) {
                            continue;
                        }
                        for (int y = 1; y <= stuff.height(); y++) {
                            put(level, mage, at.offset(x, y, z), stuff.wall().defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    private static void put(ServerLevel level, UUID mage, BlockPos pos, BlockState state) {
        KernDressing.put(level, mage, pos, state);
    }
}
