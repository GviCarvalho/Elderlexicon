package com.elderlexicon.mod.university;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A house of the campus (docs/universidade-arquitetura-design.md; the old universities keep {@link Hall}), in the manner
 * of the old halls of learning: a plinth of rough stone, stone walls with buttresses between the windows, a sill under
 * each window and a hood over it, a band of polished stone at each floor, corbels under the eaves, and a steep slate
 * roof whose gables are closed with two lancets and, on the wide ones, a small window over them. A house of use is
 * timbered above its stone: posts and beams of dark wood, the infill between them, shutters beside the windows.
 *
 * @param x0          the least corner, along x (the wall)
 * @param z0          the least corner, along z
 * @param x1          the greatest corner, along x
 * @param z1          the greatest corner, along z
 * @param height      the height of the walls (the top of the ceiling)
 * @param ridgeAlongZ whether the roof's ridge runs along z (the long walls are then the ones at x0 and x1)
 * @param floor       what its floors are of
 * @param timber      its timbered walls, or null for a house of stone
 * @param storeys     the heights of the floors above the ground floor
 */
record CampusHouse(int x0, int z0, int x1, int z1, int height, boolean ridgeAlongZ, Block floor, Timber timber,
            int... storeys) {

    /** The timbered walls of a house: from {@code from} up, posts and beams of {@code log}, {@code infill} between. */
    record Timber(int from, Block log, Block infill) {
    }

    static final Block ROOF = Blocks.DEEPSLATE_TILE_STAIRS;
    /** The ridge: a slab, so nothing is born up there. */
    static final Block RIDGE = Blocks.DEEPSLATE_TILE_SLAB;

    /** How far the roof rises above the walls. */
    int roofRise() {
        return ((ridgeAlongZ ? x1 - x0 : z1 - z0) + 2) / 2 + 1;
    }

    /**
     * Draws the house; {@code joined} are the houses it stands against, whose walls it shares: no window, sill, buttress
     * or corbel is put on a wall that faces into one of them, nor a window in a gable that does.
     */
    void draw(Masonry m, CampusHouse... joined) {
        if (!m.touches(x0 - 2, 0, z0 - 2, x1 + 2, height + roofRise() + 4, z1 + 2)) {
            return;
        }
        // The floor, the walls and the air within, the flat ceiling.
        m.fill(x0, 0, z0, x1, 0, z1, Blocks.STONE_BRICKS.defaultBlockState());
        m.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, floor.defaultBlockState());
        m.fill(x0 + 1, 1, z0 + 1, x1 - 1, height - 1, z1 - 1, Blocks.AIR.defaultBlockState());
        // The ceiling is of slabs: nothing that roams the dark is born on a slab, and the roof's space above is dark.
        m.fill(x0 + 1, height, z0 + 1, x1 - 1, height, z1 - 1, Blocks.DARK_OAK_SLAB.defaultBlockState());
        for (int storey : storeys) {
            m.fill(x0 + 1, storey, z0 + 1, x1 - 1, storey, z1 - 1, floor.defaultBlockState());
        }
        for (int x = x0; x <= x1; x++) {
            wallColumn(m, x, z0, true, joined);
            wallColumn(m, x, z1, true, joined);
        }
        for (int z = z0 + 1; z < z1; z++) {
            wallColumn(m, x0, z, false, joined);
            wallColumn(m, x1, z, false, joined);
        }
        roof(m, joined);
        lights(m);
    }

    /** Whether {@code (x, z)} is within this house's walls. */
    boolean holds(int x, int z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
    }

    private static boolean within(CampusHouse[] joined, int x, int z) {
        for (CampusHouse house : joined) {
            if (house.holds(x, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean timbered(int y) {
        return timber != null && y >= timber.from();
    }

    /** Whether a band runs round the house at {@code y}: at each floor, and where the stone gives way to timber. */
    private boolean band(int y) {
        for (int storey : storeys) {
            if (storey == y) {
                return true;
            }
        }
        return timber != null && y == timber.from() - 1;
    }

    /**
     * A column of the wall: the plinth, stone or timber, a window where one falls with its sill and hood (or its
     * shutters), a buttress outside the long walls between windows, a corbel under the eaves.
     */
    private void wallColumn(Masonry m, int x, int z, boolean alongX, CampusHouse[] joined) {
        Direction outward = alongX ? (z == z0 ? Direction.NORTH : Direction.SOUTH)
                : (x == x0 ? Direction.WEST : Direction.EAST);
        int ox = outward.getStepX();
        int oz = outward.getStepZ();
        Direction toWall = outward.getOpposite();
        boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
        if (corner) {
            for (int y = 0; y <= height; y++) {
                m.set(x, y, z, timbered(y) ? log(Direction.Axis.Y) : y <= 1 ? plinth(m, x, y, z)
                        : Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
            return;
        }
        int along = alongX ? x - x0 : z - z0;
        int length = alongX ? x1 - x0 : z1 - z0;
        // Counted from the middle of the wall, so a wall's windows mirror about its middle (a door, a portal).
        int fromMiddle = Math.abs(2 * along - length) / 2;
        boolean inner = along >= 2 && along <= length - 2 && !within(joined, x + ox, z + oz);
        boolean window = inner && fromMiddle % 4 == 0;
        boolean post = inner && fromMiddle % 4 == 2;
        Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
        for (int y = 1; y <= height; y++) {
            BlockState state;
            if (window && glazed(y)) {
                state = pane(alongX);
            } else if (y == 1) {
                state = plinth(m, x, y, z);
            } else if (timbered(y)) {
                state = y == height || band(y) ? log(axis) : post ? log(Direction.Axis.Y)
                        : timber.infill().defaultBlockState();
            } else if (band(y)) {
                state = Blocks.POLISHED_ANDESITE.defaultBlockState();
            } else {
                state = m.stone(x, y, z);
            }
            m.set(x, y, z, state);
        }
        if (window) {
            dressWindow(m, x, z, alongX, outward);
        }
        boolean longWall = ridgeAlongZ != alongX;
        int top = timber != null ? timber.from() - 2 : height - 2;
        if (post && longWall && top >= 2) {
            m.fill(x + ox, 0, z + oz, x + ox, top, z + oz, Blocks.STONE_BRICKS.defaultBlockState());
            m.set(x + ox, top + 1, z + oz, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, toWall));
        }
        if (longWall && !within(joined, x + ox, z + oz)) {
            m.set(x + ox, height - 1, z + oz, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, toWall));
        }
    }

    /** Under each window of stone a sill and over it a hood; beside each window of timber, its shutters. */
    private void dressWindow(Masonry m, int x, int z, boolean alongX, Direction outward) {
        int ox = outward.getStepX();
        int oz = outward.getStepZ();
        for (int y = 1; y < height; y++) {
            if (!glazed(y)) {
                continue;
            }
            if (timbered(y)) {
                BlockState shutter = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, outward)
                        .setValue(TrapDoorBlock.OPEN, true);
                for (int side : new int[]{-1, 1}) {
                    m.set(x + ox + (alongX ? side : 0), y, z + oz + (alongX ? 0 : side), shutter);
                }
            } else {
                if (!glazed(y - 1)) {
                    m.set(x + ox, y - 1, z + oz, slab(Blocks.STONE_BRICK_SLAB, SlabType.TOP));
                }
                if (!glazed(y + 1)) {
                    m.set(x + ox, y + 1, z + oz, slab(Blocks.STONE_BRICK_SLAB, SlabType.BOTTOM));
                }
            }
        }
    }

    /** Whether a window is glazed at {@code y}: from two above each floor to two below the next. */
    private boolean glazed(int y) {
        int from = 0;
        int to = height;
        for (int storey : storeys) {
            if (storey <= y) {
                from = Math.max(from, storey);
            } else {
                to = Math.min(to, storey);
            }
        }
        return y >= from + 2 && y <= to - 2;
    }

    private BlockState log(Direction.Axis axis) {
        return timber.log().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    /** The plinth's rough stone, mossy here and there. */
    static BlockState plinth(Masonry m, int x, int y, int z) {
        return (m.chance(x, y, z) < 30 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState();
    }

    static BlockState slab(Block slab, SlabType type) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState pane(boolean alongX) {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        return alongX ? pane.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true)
                : pane.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
    }

    // ------------------------------------------------------------------ the roof

    private int low() {
        return (ridgeAlongZ ? x0 : z0) - 1;
    }

    private int high() {
        return (ridgeAlongZ ? x1 : z1) + 1;
    }

    private Direction up() {
        return ridgeAlongZ ? Direction.EAST : Direction.SOUTH;
    }

    private Direction alongRidge() {
        return ridgeAlongZ ? Direction.SOUTH : Direction.EAST;
    }

    /**
     * The steep slate roof, a slope from each long wall up to the ridge; the gables closed in stone (or timber), two
     * lancets in each and, on a wide one, a small window over them.
     */
    private void roof(Masonry m, CampusHouse[] joined) {
        int low = low();
        int high = high();
        int from = (ridgeAlongZ ? z0 : x0) - 1;
        int to = (ridgeAlongZ ? z1 : x1) + 1;
        Direction up = up();
        int middle = (low + high) / 2;
        for (int layer = 0; low + layer <= high - layer; layer++) {
            int y = height + layer;
            int a = low + layer;
            int b = high - layer;
            for (int along = from; along <= to; along++) {
                if (a == b) {
                    cell(m, along, y, a, RIDGE.defaultBlockState());
                    continue;
                }
                cell(m, along, y, a, Masonry.stairs(ROOF, up));
                cell(m, along, y, b, Masonry.stairs(ROOF, up.getOpposite()));
                boolean gable = along == from + 1 || along == to - 1;
                if (layer > 0 && gable) {
                    int outside = along == from + 1 ? from : to;
                    gableRow(m, along, y, layer, a, b, middle, outside,
                            within(joined, ridgeAlongZ ? middle : outside, ridgeAlongZ ? outside : middle));
                }
            }
        }
    }

    private void gableRow(Masonry m, int along, int y, int layer, int a, int b, int middle, int outside,
                          boolean blind) {
        int span = b - a;
        for (int across = a + 1; across < b; across++) {
            int off = across - middle;
            boolean lancet = !blind && span > 6 && Math.abs(off) == 1 && layer >= 2 && layer <= 4;
            boolean oculus = !blind && span > 12 && off == 0 && layer >= 6 && layer <= 7;
            BlockState state;
            if (lancet || oculus) {
                state = pane(ridgeAlongZ);
            } else if (timber != null) {
                state = layer == 1 ? log(ridgeAlongZ ? Direction.Axis.X : Direction.Axis.Z)
                        : Math.floorMod(across - low(), 3) == 0 ? log(Direction.Axis.Y)
                        : timber.infill().defaultBlockState();
            } else {
                state = m.stone(ridgeAlongZ ? across : along, y, ridgeAlongZ ? along : across);
            }
            cell(m, along, y, across, state);
            if ((lancet && layer == 2) || (oculus && layer == 6)) {
                cell(m, outside, y - 1, across, slab(Blocks.STONE_BRICK_SLAB, SlabType.TOP));
            }
        }
    }

    /** A cell given along the ridge and across it. */
    private void cell(Masonry m, int along, int y, int across, BlockState state) {
        if (ridgeAlongZ) {
            m.set(across, y, along, state);
        } else {
            m.set(along, y, across, state);
        }
    }

    /**
     * A gabled dormer on each slope at each of {@code positions} (along the ridge): a window in its face, a light
     * behind it, its own little roof of slate.
     */
    void dormers(Masonry m, Block cheek, int... positions) {
        int layer = 2;
        int y = height + layer;
        Direction along = alongRidge();
        for (int d : positions) {
            for (int side : new int[]{0, 1}) {
                int front = side == 0 ? low() + layer : high() - layer;
                int inward = side == 0 ? 1 : -1;
                for (int a = d - 1; a <= d + 1; a++) {
                    for (int k = 0; k <= 1; k++) {
                        int across = front + k * inward;
                        for (int dy = 0; dy <= 1; dy++) {
                            BlockState state = cheek.defaultBlockState();
                            if (a == d && k == 0) {
                                state = pane(!ridgeAlongZ);
                            } else if (a == d) {
                                state = dy == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.AIR.defaultBlockState();
                            }
                            cell(m, a, y + dy, across, state);
                        }
                    }
                }
                for (int k = -1; k <= 2; k++) {
                    int across = front + k * inward;
                    cell(m, d - 1, y + 2, across, Masonry.stairs(ROOF, along));
                    cell(m, d + 1, y + 2, across, Masonry.stairs(ROOF, along.getOpposite()));
                    cell(m, d, y + 2, across, Blocks.DEEPSLATE_TILES.defaultBlockState());
                    cell(m, d, y + 3, across, RIDGE.defaultBlockState());
                }
            }
        }
    }

    /** A brick chimney from {@code y0} to {@code y1}, a fire burning on top. */
    static void chimney(Masonry m, int x, int z, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            m.set(x, y, z, Blocks.BRICKS.defaultBlockState());
        }
        m.set(x, y1 + 1, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
    }

    /**
     * A door in the wall at {@code (x, z)} opening to {@code outward}: the door, posts beside it, a hood over it, a step
     * of polished stone before it; {@code wide} for posts two out from the door.
     */
    static void entrance(Masonry m, int x, int z, Direction outward, Block door, boolean wide) {
        int ox = outward.getStepX();
        int oz = outward.getStepZ();
        boolean alongX = oz != 0;
        m.fill(x, 1, z, x, 2, z, Blocks.AIR.defaultBlockState());
        m.door(x, z, outward, door, false);
        // A door stands in the middle of its wall, where a window would: the wall closed over it, nothing before it.
        m.set(x, 3, z, m.stone(x, 3, z));
        m.set(x, 4, z, m.stone(x, 4, z));
        m.fill(x + ox, 1, z + oz, x + ox, 2, z + oz, Blocks.AIR.defaultBlockState());
        int half = wide ? 2 : 1;
        for (int s = -half; s <= half; s++) {
            int px = x + (alongX ? s : 0) + ox;
            int pz = z + (alongX ? 0 : s) + oz;
            if (Math.abs(s) == half) {
                m.set(px, 1, pz, Blocks.STONE_BRICK_WALL.defaultBlockState());
                m.set(px, 2, pz, Blocks.STONE_BRICK_WALL.defaultBlockState());
            }
            m.set(px, 3, pz, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, outward.getOpposite()));
            m.set(px, 4, pz, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        }
        m.set(x + ox, 0, z + oz, Blocks.POLISHED_ANDESITE.defaultBlockState());
    }

    // ------------------------------------------------------------------ within

    /**
     * Lanterns hung under each ceiling, on a grid; in a tall room, on chains, so they hang where their light reaches
     * the floor.
     */
    private void lights(Masonry m) {
        int[] ceilings = new int[storeys.length + 1];
        int[] floors = new int[storeys.length + 1];
        for (int i = 0; i < storeys.length; i++) {
            ceilings[i] = storeys[i];
            floors[i + 1] = storeys[i];
        }
        ceilings[storeys.length] = height;
        for (int i = 0; i < ceilings.length; i++) {
            int ceiling = ceilings[i];
            int hang = Math.max(1, ceiling - (floors[i] + 6));
            for (int x = x0 + 2; x <= x1 - 2; x += 5) {
                for (int z = z0 + 2; z <= z1 - 2; z += 5) {
                    boolean free = true;
                    for (int y = ceiling - hang; y < ceiling; y++) {
                        free &= m.get(x, y, z).isAir();
                    }
                    if (free) {
                        m.chandelier(x, ceiling, z, hang);
                    }
                }
            }
        }
    }
}
