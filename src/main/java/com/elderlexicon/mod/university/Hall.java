package com.elderlexicon.mod.university;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A house of the university (docs/mago-design.md, 5g), in the manner of the old halls of learning: stone walls with
 * buttresses between tall windows, polished corners, a wooden floor on every storey under a flat ceiling, and a steep
 * slate roof over the eaves, its gables closed in stone with a window high up.
 *
 * @param x0          the least corner, along x (the wall)
 * @param z0          the least corner, along z
 * @param x1          the greatest corner, along x
 * @param z1          the greatest corner, along z
 * @param height      the height of the walls (the top of the ceiling)
 * @param ridgeAlongZ whether the roof's ridge runs along z (the long walls are then the ones at x0 and x1)
 * @param floor       what its floors are of
 * @param storeys     the heights of the floors above the ground floor
 */
record Hall(int x0, int z0, int x1, int z1, int height, boolean ridgeAlongZ, Block floor, int... storeys) {

    static final Block ROOF = Blocks.DEEPSLATE_TILE_STAIRS;
    /** The ridge: a slab, so nothing is born up there. */
    static final Block RIDGE = Blocks.DEEPSLATE_TILE_SLAB;

    /** How far the roof rises above the walls. */
    int roofRise() {
        return ((ridgeAlongZ ? x1 - x0 : z1 - z0) + 2) / 2 + 1;
    }

    void draw(Masonry m) {
        if (!m.touches(x0 - 2, 0, z0 - 2, x1 + 2, height + roofRise() + 1, z1 + 2)) {
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
            wallColumn(m, x, z0, true);
            wallColumn(m, x, z1, true);
        }
        for (int z = z0 + 1; z < z1; z++) {
            wallColumn(m, x0, z, false);
            wallColumn(m, x1, z, false);
        }
        roof(m);
        lights(m);
    }

    /** A column of the wall: stone, a window where one falls, a buttress outside the long walls between windows. */
    private void wallColumn(Masonry m, int x, int z, boolean alongX) {
        boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
        if (corner) {
            m.fill(x, 0, z, x, height + 1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
            return;
        }
        int along = alongX ? x - x0 : z - z0;
        int length = alongX ? x1 - x0 : z1 - z0;
        boolean window = along >= 2 && along <= length - 2 && along % 4 == 2;
        boolean pier = along >= 2 && along <= length - 2 && along % 4 == 0;
        for (int y = 1; y <= height; y++) {
            m.set(x, y, z, window && glazed(y) ? pane(alongX) : m.stone(x, y, z));
        }
        boolean longWall = ridgeAlongZ != alongX;
        if (pier && longWall) {
            int outX = alongX ? 0 : (x == x0 ? -1 : 1);
            int outZ = alongX ? (z == z0 ? -1 : 1) : 0;
            Direction toWall = Direction.fromDelta(-outX, 0, -outZ);
            m.fill(x + outX, 1, z + outZ, x + outX, height - 2, z + outZ, Blocks.STONE_BRICKS.defaultBlockState());
            m.set(x + outX, height - 1, z + outZ, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, toWall));
            m.set(x + outX, 0, z + outZ, Blocks.STONE_BRICKS.defaultBlockState());
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

    private static BlockState pane(boolean alongX) {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        return alongX ? pane.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true)
                : pane.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
    }

    /** The steep slate roof, a slope from each long wall up to the ridge, the gables closed in stone. */
    private void roof(Masonry m) {
        int low = (ridgeAlongZ ? x0 : z0) - 1;
        int high = (ridgeAlongZ ? x1 : z1) + 1;
        int from = (ridgeAlongZ ? z0 : x0) - 1;
        int to = (ridgeAlongZ ? z1 : x1) + 1;
        Direction up = ridgeAlongZ ? Direction.EAST : Direction.SOUTH;
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
                    for (int across = a + 1; across < b; across++) {
                        boolean window = across == middle && layer >= 2 && b - a > 4;
                        cell(m, along, y, across, window ? Blocks.GLASS.defaultBlockState() : m.stone(along, y, across));
                    }
                }
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
     * Lanterns hung under each ceiling, on a grid; in a tall room, on chains, so they hang where their light reaches
     * the floor.
     */
    private void lights(Masonry m) {
        int floorOf = 0;
        int[] ceilings = new int[storeys.length + 1];
        int[] floors = new int[storeys.length + 1];
        for (int i = 0; i < storeys.length; i++) {
            ceilings[i] = storeys[i];
            floors[i + 1] = storeys[i];
        }
        floors[0] = floorOf;
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

    /**
     * A straight stair from the floor at {@code from} up to the one at {@code to}, starting at {@code (x, z)} and rising
     * toward {@code rising}, the floor above opened over it.
     */
    static void stair(Masonry m, int x, int z, Direction rising, int from, int to, Block steps) {
        for (int step = 1; step <= to - from; step++) {
            int sx = x + rising.getStepX() * (step - 1);
            int sz = z + rising.getStepZ() * (step - 1);
            m.set(sx, from + step, sz, Masonry.stairs(steps, rising));
            for (int y = from + step + 1; y <= Math.min(to, from + step + 3); y++) {
                m.set(sx, y, sz, Blocks.AIR.defaultBlockState());
            }
            m.set(sx, from + step - 1 + 0, sz, m.get(sx, from + step - 1, sz).isAir() && step > 1
                    ? Blocks.STONE_BRICKS.defaultBlockState() : m.get(sx, from + step - 1, sz));
        }
    }
}
