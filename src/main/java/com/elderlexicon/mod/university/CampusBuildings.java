package com.elderlexicon.mod.university;

import com.elderlexicon.mod.galdraria.Galdraria;
import com.elderlexicon.mod.library.RuneGuides;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.List;

/**
 * The buildings of the campus (docs/universidade-arquitetura-design.md), drawn in the campus's offsets from its middle:
 * x toward the village, z away from the walled core. What the old campus had (the Great Hall, the Library, the
 * Observatory) is drawn as it was, shifted into the core; the rest is the campus's own.
 */
final class CampusBuildings {

    private CampusBuildings() {
    }

    /** How deep the ground is mended under a building, where the land left a gap. */
    private static final int MEND = 6;

    /** Under a building's ground, earth where there was a gap; over it, nothing of the land, up to {@code clear}. */
    private static void ground(Masonry m, int x0, int z0, int x1, int z1, int clear) {
        if (!m.touches(x0, -MEND, z0, x1, clear, z1)) {
            return;
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!m.chunk.isInside(m.at(x, 0, z))) {
                    continue;
                }
                for (int y = -1; y >= -MEND && !m.solid(x, y, z); y--) {
                    m.set(x, y, z, Blocks.DIRT.defaultBlockState());
                }
                m.set(x, 0, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        m.fill(x0, 1, z0, x1, clear, z1, Blocks.AIR.defaultBlockState());
    }

    /** A strip of paving, from corner to corner. */
    private static void pave(Masonry m, int x0, int z0, int x1, int z1) {
        if (!m.touches(x0, 0, z0, x1, 0, z1)) {
            return;
        }
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                m.set(x, 0, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
    }

    private static void lampPost(Masonry m, int x, int z) {
        if (m.get(x, 1, z).isAir() && m.get(x, 2, z).isAir() && m.get(x, 3, z).isAir()) {
            m.set(x, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(x, 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(x, 3, z, Blocks.LANTERN.defaultBlockState());
        }
    }

    private static BlockState hanging() {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    // ------------------------------------------------------------------ the practice field

    /** How deep the Descent goes, in the world's heights: past zero, where the depths begin. */
    private static final int DESCENT_BOTTOM = -4;

    /**
     * The practice field (docs/universidade-arquitetura-design.md, 3): a station for each lesson of the first year that
     * needs a place, each where the game itself feels the rune's experience (nahva/Understanding). In the middle the
     * fall tower, the field's mark, its spiral up to a board over a pit of hay, twenty-two blocks down; the pit of the
     * blast, lined with deepslate, a barrel of powder at its rim; the tank, seven deep, and the canal that runs;
     * the cold room of snow and powder snow, and the pit of fire beside it; the cells of sand, a torch holding each one
     * up; the Descent, a shaft down past the world's zero; the shooting range; the lake with its pier; the dig.
     */
    static void campo(Masonry m) {
        ground(m, -38, 66, 38, 122, 2);
        // The ways: in from the ring road down the middle, and across.
        pave(m, -1, 66, 1, 120);
        pave(m, -36, 93, 36, 95);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        Direction[] ringWays = {Direction.EAST, Direction.SOUTH, Direction.SOUTH, Direction.WEST, Direction.WEST,
                Direction.NORTH, Direction.NORTH, Direction.EAST};

        // The fall tower: inside, the spiral up to its top room; out of its north face, a board over the hay.
        Tower fall = tower(0, 102, 3, 27, new int[0], new int[][]{{6, 7}, {14, 15}}, 4, false, 3);
        draw(m, fall);
        if (m.touches(-5, 0, 86, 5, 30, 108)) {
            m.fill(0, 1, 99, 0, 2, 99, air);
            m.fill(0, 0, 102, 0, 25, 102, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            int[][] ring = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
            m.fill(-2, 22, 100, 2, 22, 104, Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int[] cell : ring) {
                m.set(cell[0], 22, 102 + cell[1], air);
            }
            for (int y = 1; y <= 22; y++) {
                int i = (y - 1) % 8;
                m.set(ring[i][0], y, 102 + ring[i][1], Masonry.stairs(Blocks.DARK_OAK_STAIRS, ringWays[i]));
            }
            m.fill(-1, 22, 101, 0, 22, 101, Blocks.SPRUCE_PLANKS.defaultBlockState());
            m.fill(0, 23, 99, 0, 24, 99, air);
            m.fill(0, 22, 96, 0, 22, 98, Blocks.SPRUCE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP));
            // Hay under all the way the jump can land, two deep.
            m.fill(-2, -1, 89, 2, 0, 98, Blocks.HAY_BLOCK.defaultBlockState());
            m.chandelier(0, 27, 101, 3);
        }

        if (m.touches(10, -5, 94, 22, 2, 108)) {
            // The pit of the blast, a ladder down into it; at its rim, a barrel of powder and a striker, and a parapet
            // to stand behind.
            for (int x = 12; x <= 20; x++) {
                for (int z = 99; z <= 107; z++) {
                    boolean edge = x == 12 || x == 20 || z == 99 || z == 107;
                    for (int y = -4; y <= 0; y++) {
                        m.set(x, y, z, edge || y == -4 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : air);
                    }
                }
            }
            m.fill(13, -3, 100, 13, 0, 100, Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
            m.container(18, 1, 97, Blocks.BARREL.defaultBlockState(), "chests/practice_powder");
            m.fill(14, 1, 96, 17, 1, 96, Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
        }

        if (m.touches(-36, -8, 97, -8, 2, 116)) {
            // The tank, seven deep, a ladder down it.
            for (int x = -34; x <= -24; x++) {
                for (int z = 99; z <= 107; z++) {
                    boolean edge = x == -34 || x == -24 || z == 99 || z == 107;
                    for (int y = -7; y <= 0; y++) {
                        m.set(x, y, z, edge || y == -7 ? stoneBricks : Blocks.WATER.defaultBlockState());
                    }
                }
            }
            m.fill(-25, -6, 100, -25, 0, 100, Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
            // The canal: a spring at its head, a step down every eight so the water runs all its length.
            for (int x = -35; x <= -9; x++) {
                int step = Math.max(0, (x + 34) / 8);
                for (int y = -step - 1; y <= 0; y++) {
                    boolean wall = x == -35 || x == -9 || y == -step - 1;
                    m.set(x, y, 112, stoneBricks);
                    m.set(x, y, 114, stoneBricks);
                    m.set(x, y, 113, wall ? stoneBricks : air);
                }
            }
            m.spring(-34, 0, 113);
        }

        // The cold room: a squat round house of snow, packed ice, powder snow two deep in the middle.
        Tower cold = new Tower(28, 113, 4, 5, new int[0], new int[0][], 4, false, 3,
                Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        draw(m, cold);
        if (m.touches(12, -2, 108, 34, 6, 118)) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (!within(dx, dz, 3.5D)) {
                        continue;
                    }
                    boolean middle = within(dx, dz, 1.5D);
                    m.set(28 + dx, 0, 113 + dz, (middle ? Blocks.POWDER_SNOW : Blocks.PACKED_ICE).defaultBlockState());
                    if (middle) {
                        m.set(28 + dx, -1, 113 + dz, Blocks.POWDER_SNOW.defaultBlockState());
                    }
                }
            }
            door(m, 24, 1, 113, Blocks.SPRUCE_DOOR, Direction.WEST);
            m.set(24, 3, 113, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            m.set(28, 4, 113, Blocks.LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            // The pit of fire beside it: fire that does not go out, on netherrack, fenced, a gate to walk in.
            for (int x = 14; x <= 20; x++) {
                for (int z = 110; z <= 116; z++) {
                    boolean edge = x == 14 || x == 20 || z == 110 || z == 116;
                    m.set(x, 0, z, (edge ? Blocks.NETHER_BRICKS : Blocks.NETHERRACK).defaultBlockState());
                    m.set(x, 1, z, edge ? Blocks.NETHER_BRICK_FENCE.defaultBlockState()
                            : Blocks.FIRE.defaultBlockState());
                }
            }
            m.set(14, 1, 113, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.WEST));
        }

        if (m.touches(-18, 0, 112, -2, 8, 120)) {
            // The cells of sand: a torch on the wall holds up the sand over one's head; take it down, and it falls.
            for (int i = 0; i < 4; i++) {
                int x = -14 + i * 3;
                m.fill(x - 1, 0, 115, x + 1, 7, 117, Blocks.SANDSTONE.defaultBlockState());
                m.set(x, 0, 116, Blocks.SAND.defaultBlockState());
                m.fill(x, 1, 116, x, 2, 116, air);
                m.fill(x, 1, 115, x, 2, 115, air);
                m.set(x, 3, 116, Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.NORTH));
                m.fill(x, 4, 116, x, 7, 116, Blocks.SAND.defaultBlockState());
            }
            // Sand and torches, to make a cell up again.
            m.chest(-16, 1, 114, Direction.NORTH, "chests/practice_sand");
        }

        // The Descent: a little tower over a round shaft down past the world's zero, the spiral round its post.
        Tower mouth = tower(8, 115, 3, 6, new int[0], new int[0][], 4, false, 3);
        draw(m, mouth);
        int bottom = m.toWorld(DESCENT_BOTTOM);
        if (m.touches(4, Math.min(bottom - 1, 0), 111, 12, 8, 119)) {
            for (int y = bottom; y <= 0; y++) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (within(dx, dz, 2.5D)) {
                            m.set(8 + dx, y, 115 + dz, air);
                        } else if (within(dx, dz, 3.5D)) {
                            boolean light = Math.floorMod(y, 5) == 0 && (dx == 0 || dz == 0);
                            m.set(8 + dx, y, 115 + dz, light ? Blocks.GLOWSTONE.defaultBlockState()
                                    : Math.floorMod(y, 8) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                                    : m.stone(8 + dx, y, 115 + dz));
                        }
                    }
                }
            }
            m.fill(4, bottom - 1, 111, 12, bottom - 1, 119, stoneBricks);
            m.fill(8, bottom, 115, 8, 5, 115, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            int[][] ring = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
            for (int y = bottom; y <= 0; y++) {
                int i = Math.floorMod(y - bottom, 8);
                m.set(8 + ring[i][0], y, 115 + ring[i][1], Masonry.stairs(Blocks.DARK_OAK_STAIRS, ringWays[i]));
            }
            // A landing round the top of the spiral, where one comes in.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (within(dx, dz, 2.5D) && (Math.abs(dx) == 2 || Math.abs(dz) == 2)) {
                        m.set(8 + dx, 0, 115 + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    }
                }
            }
            m.fill(8, 1, 112, 8, 2, 112, air);
            m.chandelier(8, 6, 116, 2);
        }

        if (m.touches(2, 0, 68, 36, 4, 88)) {
            // The shooting range: a stand behind a fence, targets at ten, twenty and thirty.
            m.fill(4, 0, 70, 4, 0, 86, Blocks.POLISHED_ANDESITE.defaultBlockState());
            m.fill(5, 1, 70, 5, 1, 86, Blocks.SPRUCE_FENCE.defaultBlockState());
            for (int z : new int[]{72, 78, 84}) {
                m.set(5, 1, z, air);
            }
            for (int[] at : new int[][]{{14, 72}, {14, 84}, {24, 72}, {24, 78}, {34, 72}, {34, 84}}) {
                m.set(at[0], 1, at[1], Blocks.HAY_BLOCK.defaultBlockState());
                m.set(at[0], 2, at[1], Blocks.TARGET.defaultBlockState());
            }
            m.set(4, 1, 70, Blocks.BARREL.defaultBlockState());
            m.set(4, 1, 86, Blocks.BARREL.defaultBlockState());
        }

        if (m.touches(-37, -4, 68, -2, 3, 91)) {
            // The lake with its pier.
            for (int x = -36; x <= -8; x++) {
                for (int z = 69; z <= 90; z++) {
                    double e = Math.pow((x + 22) / 14.0D, 2) + Math.pow((z - 79.5D) / 10.5D, 2);
                    if (e > 1.0D) {
                        continue;
                    }
                    m.set(x, 0, z, Blocks.WATER.defaultBlockState());
                    for (int y = -1; y >= -3; y--) {
                        m.set(x, y, z, e < 0.6D && y > -3 ? Blocks.WATER.defaultBlockState()
                                : Blocks.SAND.defaultBlockState());
                    }
                }
            }
            m.fill(-21, 0, 80, -10, 0, 80, Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int x : new int[]{-12, -17, -21}) {
                m.fill(x, -3, 81, x, 1, 81, Blocks.SPRUCE_LOG.defaultBlockState());
            }
            m.set(-21, 2, 81, Blocks.LANTERN.defaultBlockState());
            // The dig: suspicious sand in a sandy bed, posts with lights at its corners.
            m.fill(-9, -1, 70, -3, 0, 76, Blocks.SAND.defaultBlockState());
            for (int[] at : new int[][]{{-7, 72}, {-5, 74}, {-4, 71}, {-6, 75}, {-8, 70}}) {
                m.suspicious(at[0], 0, at[1], "archaeology/trail_ruins_common");
            }
            for (int[] at : new int[][]{{-10, 69}, {-2, 69}, {-10, 77}, {-2, 77}}) {
                m.set(at[0], 1, at[1], Blocks.SPRUCE_FENCE.defaultBlockState());
                m.set(at[0], 2, at[1], Blocks.LANTERN.defaultBlockState());
            }
        }

        // Lights along the ways.
        for (int z : new int[]{72, 84, 110, 120}) {
            lampPost(m, -3, z);
            lampPost(m, 3, z);
        }
        for (int x : new int[]{-30, -18, 18, 30}) {
            lampPost(m, x, 92);
        }
    }

    // ------------------------------------------------------------------ the arena

    private static final int ARENA_X = -56;
    private static final int ARENA_Z = 88;

    /**
     * The arena (docs/universidade-arquitetura-design.md, 2): the ring of duels, a round wall open to the sky with
     * arches all round and a parapet, the stands in five steps inside it, the floor of the duels walled low with a
     * grate in its middle; four towers with cones at its diagonals; the gate east, under the stands. Below the floor,
     * the pit of creatures, round and dark, a spawner of zombies in its middle, a hatch by the gate and a ladder down
     * (the creatures open no hatch): with four of them round, the game feels the lesson of impediunt (surrounded).
     */
    static void arena(Masonry m) {
        ground(m, -72, 70, -38, 106, 2);
        int cx = ARENA_X;
        int cz = ARENA_Z;
        Tower ring = tower(cx, cz, 15, 9, new int[0], new int[0][], 4, true, 2);
        Tower[] towers = new Tower[4];
        int i = 0;
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                towers[i++] = tower(cx + sx * 11, cz + sz * 11, 3, 18, new int[]{6, 11},
                        new int[][]{{8, 9}, {13, 14}}, 4, false, 3);
            }
        }
        for (Tower t : towers) {
            draw(m, t, ring);
        }
        if (!m.touches(cx - 17, -8, cz - 17, cx + 17, 12, cz + 17)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        // The ring's wall: on a plinth, a band at its top, corbels and a parapet, a battered foot; arches all round.
        for (int dx = -17; dx <= 17; dx++) {
            for (int dz = -17; dz <= 17; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                boolean inTower = false;
                for (Tower t : towers) {
                    inTower |= t.holds(x, z);
                }
                if (inTower || within(dx, dz, 14.5D)) {
                    continue;
                }
                if (within(dx, dz, 15.5D)) {
                    for (int y = 0; y <= 9; y++) {
                        m.set(x, y, z, y <= 1 ? CampusHouse.plinth(m, x, y, z) : y == 9
                                ? Blocks.POLISHED_ANDESITE.defaultBlockState() : m.stone(x, y, z));
                    }
                } else if (within(dx, dz, 16.5D)) {
                    Direction in = outward(dx, dz).getOpposite();
                    m.set(x, 0, z, stoneBricks);
                    m.set(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
                    m.set(x, 2, z, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, in));
                    m.set(x, 8, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, in));
                    m.set(x, 9, z, stoneBricks);
                    m.set(x, 10, z, stoneBricks);
                    if (Math.floorMod(dx + dz, 2) == 0) {
                        m.set(x, 11, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    }
                }
            }
        }
        for (int k = 0; k < 28; k++) {
            double angle = (k + 0.5D) * 2.0D * Math.PI / 28;
            int dx = (int) Math.round(15 * Math.cos(angle));
            int dz = (int) Math.round(15 * Math.sin(angle));
            boolean inTower = false;
            for (Tower t : towers) {
                inTower |= t.holds(cx + dx, cz + dz) || within(cx + dx - t.cx(), cz + dz - t.cz(), 3.5D);
            }
            if (!inTower && within(dx, dz, 15.5D) && !within(dx, dz, 14.5D)) {
                m.fill(cx + dx, 4, cz + dz, cx + dx, 6, cz + dz, air);
                m.set(cx + dx, 3, cz + dz, Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        // The stands, five steps of seats round the wall facing in; the floor of the duels, walled low, the grate.
        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -15; dz <= 15; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                if (d > 14.5D) {
                    continue;
                }
                boolean inTower = false;
                for (Tower t : towers) {
                    inTower |= within(x - t.cx(), z - t.cz(), 3.5D);
                }
                if (inTower) {
                    continue;
                }
                boolean gate = Math.abs(dz) <= 1 && dx > 0;
                if (d > 9.5D) {
                    if (gate) {
                        m.fill(x, 1, z, x, 4, z, air);
                        m.set(x, 0, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                        continue;
                    }
                    int row = Math.min(5, (int) Math.floor(d - 9.5D) + 1);
                    if (row > 1) {
                        m.fill(x, 1, z, x, row - 1, z, stoneBricks);
                    }
                    m.set(x, row, z, row == 5 && d > 13.5D ? stoneBricks
                            : Masonry.stairs(Blocks.SPRUCE_STAIRS, outward(dx, dz)));
                    m.fill(x, row + 1, z, x, 8, z, air);
                } else {
                    m.set(x, 0, z, d <= 1.5D ? Blocks.IRON_BARS.defaultBlockState()
                            : Math.floorMod((int) Math.floor(d), 3) == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState()
                            : Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                    m.fill(x, 1, z, x, 8, z, air);
                    if (d > 8.5D && !gate) {
                        m.set(x, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    }
                }
            }
        }
        // The gate, east, under the stands.
        for (int x = cx + 10; x <= cx + 16; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                m.fill(x, 1, cz + dz, x, 3, cz + dz, air);
                m.set(x, 0, cz + dz, Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            m.set(cx + 15, 4, cz + dz, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        }
        for (int dz : new int[]{-2, 2}) {
            m.fill(cx + 16, 1, cz + dz, cx + 16, 2, cz + dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(cx + 16, 3, cz + dz, Blocks.LANTERN.defaultBlockState());
        }
        // The pit of creatures: round and dark, seven deep, the spawner in its middle.
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                if (!within(dx, dz, 7.5D)) {
                    continue;
                }
                m.set(cx + dx, -7, cz + dz, stoneBricks);
                for (int y = -6; y <= -1; y++) {
                    m.set(cx + dx, y, cz + dz, within(dx, dz, 6.5D) ? air : m.stone(cx + dx, y, cz + dz));
                }
            }
        }
        if (m.set(0 + cx, -6, cz, Blocks.SPAWNER.defaultBlockState())
                && m.level.getBlockEntity(m.at(cx, -6, cz))
                instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
            spawner.setEntityId(net.minecraft.world.entity.EntityType.ZOMBIE, m.level.getRandom());
        }
        // The way down: a hatch in the floor by the gate, a ladder down the pit's wall.
        m.set(cx + 6, 0, cz, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.Half.TOP));
        m.fill(cx + 6, -6, cz, cx + 6, -1, cz, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
        // Lights on the stands' top, and in the towers.
        for (int k = 0; k < 12; k++) {
            double angle = k * Math.PI / 6.0D;
            int x = cx + (int) Math.round(14 * Math.cos(angle));
            int z = cz + (int) Math.round(14 * Math.sin(angle));
            boolean nearTower = false;
            for (Tower t : towers) {
                nearTower |= within(x - t.cx(), z - t.cz(), 4.5D);
            }
            if (!nearTower) {
                m.set(x, 6, z, Blocks.LANTERN.defaultBlockState());
            }
        }
        for (Tower t : towers) {
            m.chandelier(t.cx(), 18, t.cz(), 3);
        }
    }

    // ------------------------------------------------------------------ the laboratory and the stables

    /**
     * The laboratory (docs/universidade-arquitetura-design.md, 2; Conversion, the lesson of vertere): a tower roofed in
     * copper that greens with the years, the vessel of conversion in its middle, cauldrons round it, stands of brews on
     * the counters, shelves up the ladder; and the greenhouse beside it, round, of glass between ribs of copper under a
     * dome of glass, its beds of nether wart, carrots, potatoes and cane growing what the brews need. One goes in by
     * the greenhouse, from the west.
     */
    static void laboratorio(Masonry m) {
        ground(m, 40, 66, 66, 90, 2);
        Tower lab = new Tower(56, 78, 6, 14, new int[]{7}, new int[][]{{3, 5}, {9, 11}}, 8, false, 2,
                Blocks.EXPOSED_CUT_COPPER.defaultBlockState(), Blocks.EXPOSED_CUT_COPPER_SLAB.defaultBlockState());
        Tower green = new Tower(46, 76, 5, 7, new int[0], new int[0][], 4, false, 2, Blocks.GLASS.defaultBlockState(),
                Blocks.CUT_COPPER_SLAB.defaultBlockState());
        if (!m.touches(39, 0, 68, 64, 40, 88)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        // The greenhouse: glass between copper ribs on a plinth, a roof of glass, a dome of glass (no stone in it).
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int x = 46 + dx;
                int z = 76 + dz;
                if (within(dx, dz, 4.5D)) {
                    m.set(x, 0, z, Blocks.DIRT.defaultBlockState());
                    m.fill(x, 1, z, x, 6, z, air);
                    m.set(x, 7, z, Blocks.GLASS.defaultBlockState());
                } else if (within(dx, dz, 5.5D) && !lab.holds(x, z)) {
                    boolean rib = Math.abs(dx) == Math.abs(dz) || dx == 0 || dz == 0;
                    for (int y = 0; y <= 7; y++) {
                        m.set(x, y, z, y <= 1 ? CampusHouse.plinth(m, x, y, z) : y == 7
                                ? Blocks.CUT_COPPER.defaultBlockState()
                                : (rib ? Blocks.CUT_COPPER : Blocks.GLASS).defaultBlockState());
                    }
                }
            }
        }
        m.cone(46, 8, 76, 6, 2, Blocks.GLASS.defaultBlockState(), Blocks.CUT_COPPER_SLAB.defaultBlockState());
        m.set(46, 8 + 7 * 2 - 1, 76, Blocks.LIGHTNING_ROD.defaultBlockState());
        // The laboratory's tower after it, so its wall stands where the dome would lean on it.
        draw(m, lab, green);
        // Its beds: nether wart on soul sand to the west, carrots and potatoes on wet farmland to the east, cane by
        // water, a path down the middle.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (!within(dx, dz, 4.5D) || dz == 0) {
                    continue;
                }
                int x = 46 + dx;
                int z = 76 + dz;
                if (dx < -1) {
                    m.set(x, 0, z, Blocks.SOUL_SAND.defaultBlockState());
                    m.set(x, 1, z, Blocks.NETHER_WART.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 3));
                } else if (dx > 1) {
                    m.set(x, 0, z, Blocks.FARMLAND.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
                    m.set(x, 1, z, (Math.floorMod(dx + dz, 2) == 0 ? Blocks.CARROTS : Blocks.POTATOES)
                            .defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
                }
            }
        }
        m.fill(41, 0, 76, 51, 0, 76, Blocks.STONE_BRICKS.defaultBlockState());
        m.set(46, 0, 72, Blocks.WATER.defaultBlockState());
        m.set(45, 0, 72, Blocks.SAND.defaultBlockState());
        m.set(45, 1, 72, Blocks.SUGAR_CANE.defaultBlockState());
        m.set(47, 0, 72, Blocks.SAND.defaultBlockState());
        m.set(47, 1, 72, Blocks.SUGAR_CANE.defaultBlockState());
        door(m, 41, 1, 76, Blocks.SPRUCE_DOOR, Direction.WEST);
        m.set(41, 3, 76, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.set(40, 0, 76, Blocks.POLISHED_ANDESITE.defaultBlockState());
        m.chandelier(46, 7, 76, 1);
        // The laboratory: the vessel of conversion, cauldrons round it, the stands of brews, barrels, the ladder.
        m.set(56, 1, 78, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.set(56, 2, 78, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        for (int[] at : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            m.set(56 + at[0], 1, 78 + at[1], Blocks.CAULDRON.defaultBlockState());
        }
        for (int[] at : new int[][]{{0, -4}, {4, 0}, {0, 4}}) {
            m.set(56 + at[0], 1, 78 + at[1], Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP));
            m.set(56 + at[0], 2, 78 + at[1], Blocks.BREWING_STAND.defaultBlockState());
        }
        for (int[] at : new int[][]{{3, -3}, {3, 3}, {-3, 4}, {-3, -4}}) {
            m.set(56 + at[0], 1, 78 + at[1], Blocks.BARREL.defaultBlockState());
        }
        m.fill(61, 1, 78, 61, 7, 78, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (within(dx, dz, 4.5D) && !within(dx, dz, 3.5D) && dx < 3) {
                    m.fill(56 + dx, 8, 78 + dz, 56 + dx, 9, 78 + dz, Blocks.BOOKSHELF.defaultBlockState());
                }
            }
        }
        m.chandelier(56, 7, 80, 2);
        m.chandelier(56, 14, 78, 2);
    }

    /**
     * The stables (Binding, the lesson of ligabis: to tame, to lead, to ride, to breed): a round house under a low cone
     * of planks, eight stalls round its wall, gates of fence, hay; a yard of hay in the middle with a trough; the
     * dovecote, a slim tower whose wall is all holes for the birds over the house's post at its foot; and the pasture
     * east, fenced, with its trough. Horses in the stalls, cows and sheep in the pasture (people).
     */
    static void estabulos(Masonry m) {
        ground(m, 40, 92, 74, 124, 2);
        Tower house = new Tower(54, 106, 8, 5, new int[0], new int[][]{{3, 3}}, 16, false, 1,
                Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_SLAB.defaultBlockState());
        Tower dove = tower(44, 119, 3, 14, new int[]{5}, new int[0][], 8, false, 3);
        draw(m, house);
        draw(m, dove);
        BlockState air = Blocks.AIR.defaultBlockState();
        if (m.touches(44, 0, 96, 64, 6, 116)) {
            BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    if (within(dx, dz, 7.5D)) {
                        m.set(54 + dx, 0, 106 + dz, Blocks.COARSE_DIRT.defaultBlockState());
                    }
                }
            }
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4.0D;
                for (int s = 5; s <= 7; s++) {
                    int x = 54 + (int) Math.round(s * Math.cos(a));
                    int z = 106 + (int) Math.round(s * Math.sin(a));
                    m.fill(x, 1, z, x, 2, z, fence);
                }
                double b = a + Math.PI / 8.0D;
                int gx = 54 + (int) Math.round(5 * Math.cos(b));
                int gz = 106 + (int) Math.round(5 * Math.sin(b));
                m.set(gx, 1, gz, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, outward(gx - 54, gz - 106)));
                m.set(54 + (int) Math.round(7 * Math.cos(b)), 1, 106 + (int) Math.round(7 * Math.sin(b)),
                        Blocks.HAY_BLOCK.defaultBlockState());
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    m.set(54 + dx, 1, 106 + dz, dx == 0 && dz == 0
                            ? Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)
                            : Blocks.HAY_BLOCK.defaultBlockState());
                }
            }
            // The way in, west, wide enough for a horse.
            m.fill(46, 1, 105, 46, 3, 107, air);
            m.fill(45, 0, 105, 46, 0, 107, Blocks.POLISHED_ANDESITE.defaultBlockState());
            m.fill(45, 1, 105, 45, 3, 107, air);
            m.chandelier(54, 5, 106, 1);
            m.chandelier(54, 5, 102, 1);
            m.chandelier(54, 5, 110, 1);
        }
        if (m.touches(39, 0, 114, 49, 20, 124)) {
            // The dovecote: holes for the birds all round its upper wall, the post at its foot.
            for (int y : new int[]{7, 9, 11, 13}) {
                for (int k = 0; k < 8; k++) {
                    double a = (k + (y % 4 == 1 ? 0.5D : 0.0D)) * Math.PI / 4.0D;
                    m.set(44 + (int) Math.round(3 * Math.cos(a)), y, 119 + (int) Math.round(3 * Math.sin(a)), air);
                }
            }
            door(m, 44, 1, 116, Blocks.SPRUCE_DOOR, Direction.NORTH);
            m.set(44, 3, 116, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            m.set(45, 1, 120, Blocks.BARREL.defaultBlockState());
            m.lectern(43, 1, 120, Direction.NORTH, () -> net.minecraft.world.item.ItemStack.EMPTY);
            m.fill(46, 1, 119, 46, 5, 119, Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
            m.chandelier(44, 5, 118, 1);
            m.chandelier(44, 14, 119, 2);
        }
        if (m.touches(62, 0, 94, 73, 3, 122)) {
            // The pasture: fenced, a gate toward the stables, a trough, lights at its corners.
            for (int x = 63; x <= 72; x++) {
                for (int z : new int[]{95, 121}) {
                    m.set(x, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
            for (int z = 95; z <= 121; z++) {
                for (int x : new int[]{63, 72}) {
                    m.set(x, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
            m.set(63, 1, 106, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.EAST));
            m.fill(70, 0, 100, 70, 0, 103, Blocks.WATER.defaultBlockState());
            for (int[] at : new int[][]{{63, 95}, {72, 95}, {63, 121}, {72, 121}}) {
                m.set(at[0], 2, at[1], Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    /** An animal of {@code type} at {@code (x, 1, z)}, kept: it does not wander off the world. */
    private static void animal(Masonry m, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type,
                               int x, int z) {
        net.minecraft.core.BlockPos at = m.at(x, 1, z);
        if (!m.chunk.isInside(at)) {
            return;
        }
        net.minecraft.world.entity.Mob mob = type.create(m.level.getLevel());
        if (mob == null) {
            return;
        }
        mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, m.level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(m.level, m.level.getCurrentDifficultyAt(at),
                net.minecraft.world.entity.MobSpawnType.STRUCTURE, null, null);
        mob.setPersistenceRequired();
        m.level.addFreshEntityWithPassengers(mob);
    }

    // ------------------------------------------------------------------ the auditorium

    private static final int AUDITORIUM_X = 40;
    private static final int AUDITORIUM_Z = -96;
    /** Where the master stands on the dais, the rows' arcs round it. */
    private static final int DAIS_X = 29;

    /**
     * The auditorium (docs/universidade-arquitetura-design.md, 2), north of the core, out of its north gate: a great
     * rotunda under battlements and a broad cone, two turrets with cones at its back. Inside, a theatre: rows in arcs
     * round the master's dais, eight steps rising away from it, an aisle down the middle and one each side; before the
     * dais a floor of carved stone where demonstrations are read; tall windows all round; a gallery round the back wall
     * over the top rows, up the turrets' ladders; the doors at the front, beside the dais.
     */
    static void auditorio(Masonry m) {
        ground(m, 18, -114, 58, -77, 2);
        int cx = AUDITORIUM_X;
        int cz = AUDITORIUM_Z;
        Tower hall = tower(cx, cz, 14, 16, new int[0], new int[][]{{9, 13}}, 14, true, 2);
        Tower north = tower(cx + 10, cz - 12, 3, 22, new int[]{10}, new int[][]{{13, 14}, {17, 19}}, 4, false, 3);
        Tower south = tower(cx + 10, cz + 12, 3, 22, new int[]{10}, new int[][]{{13, 14}, {17, 19}}, 4, false, 3);
        draw(m, north, hall);
        draw(m, south, hall);
        draw(m, hall, north, south);
        // The way from the core's north gate to the south door.
        pave(m, 19, -80, 21, -77);
        pave(m, 19, -82, 33, -80);
        pave(m, 32, -83, 34, -81);
        if (!m.touches(cx - 16, 0, cz - 16, cx + 16, 17, cz + 16)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        // The theatre.
        for (int dx = -13; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (!within(dx, dz, 13.5D) || x <= DAIS_X + 4) {
                    continue;
                }
                double rho = Math.sqrt((x - DAIS_X) * (x - DAIS_X) + dz * dz);
                int row = Math.min(8, (int) Math.floor((rho - 6.0D) / 2.0D) + 1);
                if (row < 1) {
                    continue;
                }
                boolean aisle = Math.abs(dz) <= 1 || Math.abs(dz) == 7;
                boolean seat = Math.floorMod((int) Math.floor(rho - 6.0D), 2) == 0;
                if (row > 1) {
                    m.fill(x, 1, z, x, row - 1, z, planks);
                }
                m.set(x, row, z, aisle || !seat ? planks
                        : Masonry.stairs(Blocks.SPRUCE_STAIRS, outward(x - DAIS_X, dz)));
            }
        }
        // The dais, the master's lectern, banners; the carved floor before it.
        for (int x = DAIS_X - 3; x <= DAIS_X + 1; x++) {
            for (int z = cz - 5; z <= cz + 5; z++) {
                if (within(x - cx, z - cz, 13.5D)) {
                    m.set(x, 1, z, Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
                }
            }
        }
        m.lectern(DAIS_X, 1, cz, Direction.EAST, () -> RuneGuides.familyGuide(RuneGuides.Family.FUNCTIONS));
        for (int dz : new int[]{-4, 4}) {
            m.set(DAIS_X - 2, 2, cz + dz, Blocks.PURPLE_BANNER.defaultBlockState());
        }
        for (int x = DAIS_X + 2; x <= DAIS_X + 5; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                m.set(x, 0, z, Math.floorMod(x + z, 2) == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                        : Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        // The doors at the front, north and south, beside the dais.
        for (int side : new int[]{-1, 1}) {
            int z = cz + side * 12;
            door(m, DAIS_X + 4, 1, z, Blocks.DARK_OAK_DOOR, side < 0 ? Direction.NORTH : Direction.SOUTH);
            m.set(DAIS_X + 4, 3, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            m.fill(DAIS_X + 4, 1, z + side, DAIS_X + 4, 2, z + side, air);
            m.set(DAIS_X + 4, 0, z + side, Blocks.POLISHED_ANDESITE.defaultBlockState());
            m.fill(DAIS_X + 4, 1, z - side, DAIS_X + 4, 3, z - side, air);
        }
        // The gallery round the back wall at the turrets' floor, a railing on its edge.
        for (int dx = 4; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                if (!within(dx, dz, 13.5D) || within(dx, dz, 11.5D)) {
                    continue;
                }
                m.set(cx + dx, 10, cz + dz, planks);
                if (within(dx, dz, 12.5D)) {
                    m.set(cx + dx, 11, cz + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
        }
        for (Tower t : new Tower[]{north, south}) {
            m.fill(t.cx() + 2, 1, t.cz(), t.cx() + 2, 10, t.cz(), Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
            m.chandelier(t.cx(), 22, t.cz(), 3);
        }
        m.chandelier(cx, 16, cz, 6);
        for (int[] at : new int[][]{{-6, -6}, {-6, 6}, {5, -6}, {5, 6}}) {
            m.chandelier(cx + at[0], 16, cz + at[1], 4);
        }
    }

    // ------------------------------------------------------------------ the core

    /** The walled core: the rectory, the library and the research tower, all of towers, the short wall and its gate. */
    static void nucleo(Masonry m) {
        ground(m, -60, -70, 54, -12, 14);
        // The ways inside: from the Great Hall's portal to the gate; from the Library's door and the research tower's.
        pave(m, -2, -31, 2, -12);
        pave(m, -33, -45, -28, -43);
        pave(m, -30, -45, -28, -14);
        pave(m, -30, -16, 0, -14);
        pave(m, 30, -49, 34, -47);
        pave(m, 30, -49, 32, -14);
        pave(m, 0, -16, 32, -14);
        reitoria(m);
        biblioteca(m);
        pesquisa(m);
        UniversityPiece.garden(m, 36, 50, -32, -20);
        UniversityPiece.tree(m, -45, -16);
        UniversityPiece.tree(m, 14, -20);
        wall(m, -60, -70, 54, -12);
        // The north gate, out to the auditorium, and the way to it between the rectory and the research tower.
        if (m.touches(18, 0, -71, 22, 6, -68)) {
            m.fill(19, 1, -70, 21, 4, -69, Blocks.AIR.defaultBlockState());
            m.set(18, 3, -68, Blocks.LANTERN.defaultBlockState());
            m.set(22, 3, -68, Blocks.LANTERN.defaultBlockState());
        }
        pave(m, 19, -70, 21, -16);
        for (int z = -26; z >= -60; z -= 8) {
            lampPost(m, -27, z);
            lampPost(m, 29, z);
        }
        for (int x = -26; x <= 26; x += 8) {
            lampPost(m, x, -17);
        }
    }

    /** The short wall round the core, two thick and nine high, round towers at its corners and halfway, the gate. */
    private static void wall(Masonry m, int x0, int z0, int x1, int z1) {
        int height = 9;
        int gateX = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z : new int[]{z0, z1}) {
                int in = z == z0 ? 1 : -1;
                boolean gate = z == z1 && Math.abs(x - gateX) <= 3;
                if (gate) {
                    m.fill(x, 1, z, x, 6, z + in, Blocks.AIR.defaultBlockState());
                    m.fill(x, 7, z, x, height, z + in, Blocks.STONE_BRICKS.defaultBlockState());
                } else {
                    for (int y = 0; y <= height; y++) {
                        m.set(x, y, z, m.stone(x, y, z));
                        m.set(x, y, z + in, m.stone(x, y, z + in));
                    }
                }
                if ((x & 1) == 0) {
                    m.set(x, height + 1, z, m.stone(x, height + 1, z));
                }
            }
        }
        for (int z = z0; z <= z1; z++) {
            for (int x : new int[]{x0, x1}) {
                int in = x == x0 ? 1 : -1;
                for (int y = 0; y <= height; y++) {
                    m.set(x, y, z, m.stone(x, y, z));
                    m.set(x + in, y, z, m.stone(x + in, y, z));
                }
                if ((z & 1) == 0) {
                    m.set(x, height + 1, z, m.stone(x, height + 1, z));
                }
            }
        }
        for (int[] at : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            wallTower(m, at[0], at[1], 5, 17, null);
        }
        wallTower(m, (x0 + x1) / 2, z0, 4, 14, null);
        wallTower(m, x0, (z0 + z1) / 2, 4, 14, null);
        wallTower(m, x1, (z0 + z1) / 2, 4, 14, null);
        wallTower(m, gateX - 7, z1, 4, 18, Direction.NORTH);
        wallTower(m, gateX + 7, z1, 4, 18, Direction.NORTH);
        m.fill(gateX - 3, 6, z1, gateX + 3, 6, z1, Blocks.IRON_BARS.defaultBlockState());
        m.set(gateX - 2, 6, z1 - 1, hanging());
        m.set(gateX + 2, 6, z1 - 1, hanging());
        for (int i = x0 + 4; i <= x1 - 4; i += 8) {
            for (int z : new int[]{z0 + 1, z1 - 1}) {
                if (m.get(i, height + 1, z).isAir()) {
                    m.set(i, height + 1, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }

    // ------------------------------------------------------------------ the rectory and the library, of towers

    /**
     * The rectory, the Great Hall made of towers (docs/universidade-arquitetura-design.md, 6.2): the council's
     * rotunda, broad under its battlements, the round table in the middle and the chairs round it, a carpet from the
     * door, the dais of the Rector to the north with its seat and lectern; two spired towers either side of the
     * portal, the rose window between them over the double door; and the Rector's tower behind, the tallest of the
     * core, its rooms up a ladder.
     */
    static void reitoria(Masonry m) {
        Tower hall = tower(0, -44, 12, 20, new int[0], new int[][]{{4, 8}, {13, 16}}, 16, true, 2);
        int[][] frontRows = {{11, 13}, {19, 21}, {26, 28}};
        Tower west = tower(-7, -33, 3, 30, new int[]{8, 16, 24}, frontRows, 4, false, 3);
        Tower east = tower(7, -33, 3, 30, new int[]{8, 16, 24}, frontRows, 4, false, 3);
        Tower rector = tower(0, -58, 5, 34, new int[]{8, 14, 20, 26},
                new int[][]{{10, 12}, {16, 18}, {22, 24}, {28, 31}}, 6, false, 3);
        draw(m, west, hall);
        draw(m, east, hall);
        draw(m, rector, hall);
        draw(m, hall, west, east, rector);
        if (!m.touches(-14, 0, -64, 14, 22, -30)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        // The portal: double doors under a pointed head, the rose window over it.
        for (int dx = -1; dx <= 1; dx++) {
            m.fill(dx, 1, -32, dx, dx == 0 ? 5 : 4, -32, air);
        }
        m.door(-1, -32, Direction.SOUTH, Blocks.DARK_OAK_DOOR, false);
        m.door(1, -32, Direction.SOUTH, Blocks.DARK_OAK_DOOR, true);
        m.set(-1, 4, -32, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
        m.set(1, 4, -32, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        m.set(0, 5, -32, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.fill(-1, 0, -31, 1, 0, -31, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 3.5D) {
                    m.set(dx, 13 + dy, -32, d > 2.5D ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                            : (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy))
                            ? Blocks.STONE_BRICKS.defaultBlockState()
                            : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                }
            }
        }
        // The council's round table, the chairs round it facing in; the carpet from the door.
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (within(dx, dz, 3.5D) && !within(dx, dz, 2.5D)) {
                    m.set(dx, 1, -44 + dz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                } else if (within(dx, dz, 4.5D) && !within(dx, dz, 3.5D) && Math.floorMod(dx + dz, 2) == 0
                        && Math.abs(dx) > 1) {
                    m.set(dx, 1, -44 + dz, Masonry.stairs(Blocks.DARK_OAK_STAIRS, outward(dx, dz)));
                }
            }
        }
        m.fill(0, 0, -40, 0, 0, -33, Blocks.PURPLE_WOOL.defaultBlockState());
        // The dais: the Rector's seat, its lectern, the house's banners.
        m.fill(-4, 1, -55, 4, 1, -52, Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
        m.set(0, 1, -54, Blocks.POLISHED_ANDESITE.defaultBlockState());
        m.set(0, 2, -54, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
        m.lectern(2, 1, -52, Direction.SOUTH, () -> RuneGuides.familyGuide(RuneGuides.Family.FONTS));
        for (int x : new int[]{-3, 3}) {
            m.set(x, 2, -55, Blocks.PURPLE_BANNER.defaultBlockState());
        }
        for (int[] at : new int[][]{{-7, -44}, {7, -44}, {0, -50}, {0, -38}}) {
            m.chandelier(at[0], 20, at[1], 5);
        }
        m.chandelier(0, 20, -44, 8);
        // The Rector's tower: a ladder up its rooms.
        m.fill(0, 1, -62, 0, 26, -62, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
        for (int floor : new int[]{8, 14, 20, 26}) {
            m.chandelier(2, floor + 6, -58, 2);
        }
        m.set(-2, 15, -58, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.set(-2, 15, -57, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        m.fill(-4, 21, -59, -4, 22, -57, Blocks.BOOKSHELF.defaultBlockState());
        BlockState bed = Blocks.PURPLE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        m.set(3, 27, -60, bed.setValue(BedBlock.PART, BedPart.HEAD));
        m.set(3, 27, -59, bed.setValue(BedBlock.PART, BedPart.FOOT));
    }

    /**
     * The library, the Archive made of towers: the great round library, its galleries rings round a well in the
     * middle, the spiral up the well and a bridge to each gallery, shelves from floor to ceiling round every level,
     * stacks on the floor; the Archive's closed room in a tower behind an iron door; and the reading room in a tower
     * before it, tables and lecterns with guides.
     */
    static void biblioteca(Masonry m) {
        Tower main = tower(-44, -44, 10, 19, new int[]{6, 12}, new int[][]{{3, 4}, {8, 10}, {14, 16}}, 12, true, 2);
        Tower vault = tower(-44, -58, 5, 10, new int[0], new int[][]{{6, 7}}, 4, false, 3);
        Tower reading = tower(-44, -30, 5, 12, new int[0], new int[][]{{3, 6}, {9, 10}}, 8, false, 3);
        draw(m, vault);
        draw(m, reading, main);
        draw(m, main, reading);
        if (!m.touches(-56, 0, -64, -32, 20, -24)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState shelf = Blocks.BOOKSHELF.defaultBlockState();
        // The well: the galleries are rings round the wall, a railing on their edge.
        for (int floor : new int[]{6, 12}) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (within(dx, dz, 5.5D)) {
                        m.set(-44 + dx, floor, -44 + dz, air);
                    } else if (within(dx, dz, 6.5D)) {
                        m.set(-44 + dx, floor + 1, -44 + dz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                    }
                }
            }
        }
        // Shelves round the wall on every level; the door, the reading room and the gallery's bridges left free.
        for (int floor : new int[]{0, 6, 12}) {
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    if (within(dx, dz, 8.5D) || !within(dx, dz, 9.5D)) {
                        continue;
                    }
                    if (Math.abs(dx) <= 1 && (floor == 0 || dz > 0) || Math.abs(dz) <= 1 && dx > 0 && floor == 0) {
                        continue;
                    }
                    m.fill(-44 + dx, floor + 1, -44 + dz, -44 + dx, floor + (floor == 12 ? 6 : 5), -44 + dz, shelf);
                }
            }
        }
        // The spiral up the well, and a bridge from it to each gallery.
        spiral(m, -44, -44, 12, new int[]{6, 12});
        int[][] ring = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int floor : new int[]{6, 12}) {
            int[] out = ring[(floor - 1) % 8];
            for (int k = 2; within(out[0] * k, out[1] * k, 6.5D); k++) {
                int x = -44 + out[0] * k;
                int z = -44 + out[1] * k;
                m.set(x, floor, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                m.fill(x, floor + 1, z, x, floor + 2, z, air);
            }
        }
        // The stacks on the floor, along the diagonals.
        for (int[] way : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            for (int k = 5; k <= 7; k++) {
                m.fill(-44 + way[0] * k, 1, -44 + way[1] * k, -44 + way[0] * k, 3, -44 + way[1] * k, shelf);
            }
        }
        for (int floor : new int[]{0, 6, 12}) {
            for (int[] at : new int[][]{{-37, -44}, {-51, -44}, {-44, -37}, {-44, -51}}) {
                m.chandelier(at[0], floor + 6, at[1], 2);
            }
        }
        towerEntrance(m, -34, -44, Direction.EAST, Blocks.DARK_OAK_DOOR);
        // The closed room: the Archive's, behind an iron door.
        m.fill(-44, 1, -54, -44, 2, -54, air);
        BlockState iron = Blocks.IRON_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.SOUTH);
        m.set(-44, 1, -53, iron.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        m.set(-44, 2, -53, iron.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        m.set(-44, 1, -58, Blocks.ENCHANTING_TABLE.defaultBlockState());
        m.chest(-47, 1, -59, Direction.NORTH, "chests/university_archive");
        m.chest(-41, 1, -59, Direction.NORTH, "chests/university_archive");
        m.fill(-46, 1, -62, -42, 3, -62, shelf);
        m.chandelier(-44, 10, -57, 3);
        // The reading room.
        for (int z : new int[]{-31, -28}) {
            m.fill(-46, 1, z, -42, 1, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        }
        m.lectern(-48, 1, -30, Direction.EAST, () -> UniversityPiece.runeGuide(1));
        m.lectern(-40, 1, -30, Direction.WEST, () -> UniversityPiece.runeGuide(6));
        m.chandelier(-44, 12, -30, 4);
    }

    // ------------------------------------------------------------------ the research tower

    /** Where the researcher of the Atlas stands, behind its counter; the librarian, among the shelves of spells. */
    private static final int[] ATLAS_KEEPER = {46, 1, -48};
    private static final int[] LIBRARIAN = {44, 8, -46};

    /**
     * The research tower, in the place of the old observatory (docs/torre-pesquisa-design.md): the tower of the Atlas,
     * the great spiral round its column, a floor to each thing the researchers do; at the foot their counter and the
     * board of the house's asks; the library of spells; the room of maps; the room of pure siglistics and the pós,
     * with a bed of purple for the ways to Nahvá; at the top, glass all round between eight pillars, the telescope (the
     * station of the lesson of surgit). Beside it the laboratory of measures under its copper cone, the vessel where a
     * thing comes apart into its particles and the instruments round it; and a slim watch turret.
     */
    static void pesquisa(Masonry m) {
        Tower atlas = tower(42, -48, 6, 33, new int[]{7, 14, 21, 28},
                new int[][]{{3, 5}, {10, 12}, {17, 19}, {24, 26}}, 8, false, 3);
        Tower lab = new Tower(45, -58, 5, 12, new int[]{7}, new int[][]{{3, 5}, {9, 10}}, 6, false, 3,
                Blocks.EXPOSED_CUT_COPPER.defaultBlockState(), Blocks.EXPOSED_CUT_COPPER_SLAB.defaultBlockState());
        Tower watch = tower(34, -53, 2, 24, new int[]{7, 14, 21}, new int[][]{{10, 11}, {17, 18}, {22, 23}}, 4, false,
                3);
        draw(m, lab, atlas);
        draw(m, watch, atlas);
        draw(m, atlas, lab, watch);
        if (!m.touches(30, 0, -65, 52, 36, -40)) {
            return;
        }
        // The observatory at the top: glass all round between eight pillars, under the cone.
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (within(dx, dz, 5.5D) || !within(dx, dz, 6.5D)) {
                    continue;
                }
                boolean pillar = Math.abs(dx) == Math.abs(dz) || dx == 0 || dz == 0;
                m.fill(42 + dx, 29, -48 + dz, 42 + dx, 32, -48 + dz, pillar
                        ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.GLASS.defaultBlockState());
            }
        }
        towerEntrance(m, 36, -48, Direction.WEST, Blocks.DARK_OAK_DOOR);
        spiral(m, 42, -48, 28, new int[]{7, 14, 21, 28});
        // The foot: the researchers' counter, the barrels of the house's asks behind it.
        m.fill(45, 1, -51, 45, 1, -45, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(
                net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
        m.set(45, 2, -50, Blocks.LANTERN.defaultBlockState());
        m.fill(47, 1, -51, 47, 2, -51, Blocks.BARREL.defaultBlockState());
        m.fill(47, 1, -45, 47, 2, -45, Blocks.BARREL.defaultBlockState());
        // The library of spells; the room of maps.
        shelvesRound(m, 42, -48, 7, 2);
        shelvesRound(m, 42, -48, 14, 1);
        m.fill(39, 15, -45, 40, 15, -44, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        m.fill(44, 15, -52, 45, 15, -51, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        // Pure siglistics and the pós: a desk, a stand of brews, the bed for the ways to Nahvá.
        BlockState bed = Blocks.PURPLE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        m.set(39, 22, -51, bed.setValue(BedBlock.PART, BedPart.HEAD));
        m.set(39, 22, -50, bed.setValue(BedBlock.PART, BedPart.FOOT));
        m.set(45, 22, -45, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.set(44, 22, -45, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST));
        m.set(45, 22, -51, Blocks.BREWING_STAND.defaultBlockState());
        // The top: the telescope on its stand, the table of the stars.
        m.set(45, 29, -48, Blocks.SPRUCE_FENCE.defaultBlockState());
        m.set(45, 30, -48, Blocks.COPPER_BLOCK.defaultBlockState());
        m.set(46, 31, -48, Blocks.LIGHTNING_ROD.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, Direction.EAST));
        m.set(39, 29, -48, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        for (int floor : new int[]{0, 7, 14, 21}) {
            m.chandelier(40, floor + 7, -46, 2);
        }
        m.chandelier(42, 33, -45, 2);
        // The laboratory of measures: the vessel on its plinth, the instruments round it, a ladder to the store.
        m.set(45, 1, -60, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.set(45, 2, -60, Blocks.CAULDRON.defaultBlockState());
        for (int[] at : new int[][]{{43, -60}, {47, -60}, {45, -62}}) {
            m.set(at[0], 1, at[1], Blocks.POLISHED_ANDESITE.defaultBlockState());
            m.set(at[0], 2, at[1], Blocks.LODESTONE.defaultBlockState());
        }
        m.set(48, 1, -57, Blocks.BARREL.defaultBlockState());
        m.set(48, 1, -56, Blocks.BARREL.defaultBlockState());
        m.fill(49, 1, -60, 49, 7, -60, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
        m.chandelier(45, 7, -58, 2);
        m.chandelier(45, 12, -58, 2);
        // The watch turret: a ladder to its lookout.
        m.fill(33, 1, -53, 33, 21, -53, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        m.chandelier(34, 24, -53, 2);
    }

    /** Shelves round a tower's wall on a floor, a gap at each quarter, a lectern with a guide there. */
    private static void shelvesRound(Masonry m, int cx, int cz, int floor, int high) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (within(dx, dz, 4.5D) || !within(dx, dz, 5.5D) || Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
                    continue;
                }
                m.fill(cx + dx, floor + 1, cz + dz, cx + dx, floor + high, cz + dz,
                        Blocks.BOOKSHELF.defaultBlockState());
            }
        }
        for (Direction toward : Direction.Plane.HORIZONTAL) {
            int index = Math.floorMod(floor * 7 + toward.ordinal() * 11, 40);
            m.lectern(cx - toward.getStepX() * 4, floor + 1, cz - toward.getStepZ() * 4, toward,
                    () -> UniversityPiece.runeGuide(index));
        }
    }

    /**
     * A tower of the core's wall, in the house's way: hollow, a floor every six, windows, the battered foot, the band,
     * corbels under an eave and a slate cone with its rod; a light on every floor (nothing is born in it). With
     * {@code door}, a door at its foot that way, into the core.
     */
    private static void wallTower(Masonry m, int cx, int cz, int r, int top, Direction door) {
        Tower t = tower(cx, cz, r, top, top > 13 ? new int[]{6, 12} : new int[]{6},
                top > 13 ? new int[][]{{8, 9}, {14, 15}} : new int[][]{{8, 10}}, r > 4 ? 6 : 4, false, 3);
        draw(m, t);
        if (!m.touches(cx - r - 1, 0, cz - r - 1, cx + r + 1, top, cz + r + 1)) {
            return;
        }
        for (int floor : top > 13 ? new int[]{0, 6, 12} : new int[]{0, 6}) {
            m.chandelier(cx, Math.min(floor + 6, top), cz, 2);
        }
        if (door != null) {
            int x = cx + door.getStepX() * r;
            int z = cz + door.getStepZ() * r;
            door(m, x, 1, z, Blocks.SPRUCE_DOOR, door);
            m.set(x, 3, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            m.fill(x + door.getStepX(), 1, z + door.getStepZ(), x + door.getStepX(), 2, z + door.getStepZ(),
                    Blocks.AIR.defaultBlockState());
            // A ladder up its floors, on the wall opposite the door.
            int lx = cx - door.getStepX() * (r - 1);
            int lz = cz - door.getStepZ() * (r - 1);
            m.fill(lx, 1, lz, lx, top > 13 ? 12 : 6, lz, Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, door));
        }
    }

    // ------------------------------------------------------------------ the courtyard

    /** The courtyard before the core's gate: paved, the fountain in the middle, lamp posts round it. */
    static void patio(Masonry m) {
        ground(m, -17, -11, 17, 11, 8);
        pave(m, -16, -10, 16, 10);
        m.ring(0, 0, 0, 5, Blocks.STONE_BRICKS.defaultBlockState(), true);
        m.ring(0, 1, 0, 5, Blocks.STONE_BRICKS.defaultBlockState(), true);
        m.ring(0, 1, 0, 4, Blocks.WATER.defaultBlockState(), true);
        m.ring(0, 2, 0, 5, Blocks.STONE_BRICK_SLAB.defaultBlockState(), false);
        // The column, and a bowl on it with water, a lantern over the spout.
        m.fill(0, 1, 0, 0, 3, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.ring(0, 4, 0, 2, Blocks.STONE_BRICKS.defaultBlockState(), true);
        m.ring(0, 5, 0, 2, Blocks.STONE_BRICK_WALL.defaultBlockState(), false);
        m.ring(0, 5, 0, 1, Blocks.WATER.defaultBlockState(), true);
        m.set(0, 5, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.set(0, 6, 0, Blocks.LANTERN.defaultBlockState());
        // Benches facing it, east and west (the way north to south is left free); flower beds in the corners.
        for (int side : new int[]{-1, 1}) {
            for (int dz = -1; dz <= 1; dz++) {
                m.set(side * 7, 1, dz, Masonry.stairs(Blocks.SPRUCE_STAIRS, side < 0 ? Direction.WEST : Direction.EAST));
            }
            for (int sz : new int[]{-1, 1}) {
                Block[] flowers = {Blocks.POPPY, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY};
                for (int dx = 11; dx <= 13; dx++) {
                    for (int dz = 5; dz <= 7; dz++) {
                        int x = side * dx;
                        int z = sz * dz;
                        m.set(x, 0, z, Blocks.GRASS_BLOCK.defaultBlockState());
                        m.set(x, 1, z, flowers[Math.floorMod(x * 3 + z, flowers.length)].defaultBlockState());
                    }
                }
            }
        }
        for (int[] at : new int[][]{{-15, -9}, {15, -9}, {-15, 9}, {15, 9}, {-8, -9}, {8, -9}, {-8, 9}, {8, 9},
                {-15, 0}, {15, 0}}) {
            lampPost(m, at[0], at[1]);
        }
    }

    // ------------------------------------------------------------------ the teaching building

    /**
     * A course of the teaching building: the floor of a wing it has. The wing lies west ({@code sign} -1) or east
     * (1) of the keep along x, or south along z; {@code chair} is the master who sits there, if one does yet (until
     * each course has its Magister, the masters of the old disciplines give the courses their chairs).
     */
    private record Course(int cx, int cz, boolean alongX, int sign, int floor, Discipline chair) {

        /** The place {@code u} from the wing's middle away from the keep and {@code v} across. */
        int[] at(int u, int v) {
            return alongX ? new int[]{cx + sign * u, cz + v} : new int[]{cx + v, cz + sign * u};
        }

        Direction towardKeep() {
            return alongX ? (sign < 0 ? Direction.EAST : Direction.WEST) : Direction.NORTH;
        }
    }

    private static final int WING_TOP = 18;
    private static final int[] WING_FLOORS = {0, 6, 12};

    /**
     * The nine courses, a floor of a wing each (docs/universidade-arquitetura-design.md, 7): the Wing of Force west
     * (Evocação, Guarda, Conversão), of the Senses east (Visão, Vinculação, Contramagia), of Craft south (Ritual,
     * Oneirologia, Artificiaria).
     */
    private static final List<Course> COURSES = List.of(
            new Course(-18, 30, true, -1, 0, Discipline.FONTES),
            new Course(-18, 30, true, -1, 6, Discipline.FUNCOES),
            new Course(-18, 30, true, -1, 12, Discipline.FILTROS),
            new Course(18, 30, true, 1, 0, Discipline.FATOS),
            new Course(18, 30, true, 1, 6, Discipline.VITA),
            new Course(18, 30, true, 1, 12, Discipline.NAHVA),
            new Course(0, 48, false, 1, 0, Discipline.GALDRARIA),
            new Course(0, 48, false, 1, 6, null),
            new Course(0, 48, false, 1, 12, null));

    /**
     * The teaching building, made of towers (docs/universidade-arquitetura-design.md, 6.2): the bell tower at the
     * front, the tallest, its portal to the courtyard and the bell in open arches under its cone; the keep behind it,
     * the hall of entrance, the great spiral round its post, the rooms of study and the house's library above; a wing
     * on each other side, a broad tower whose every floor is a course, the class toward the keep and the master's
     * room beyond a wall; and a slim turret at the end of each wing, a ladder up to a lookout over its roof. The
     * wings open into the keep on every floor, so the one stair serves them all.
     */
    static void aulas(Masonry m) {
        ground(m, -37, 12, 37, 63, 2);
        Tower keep = tower(0, 30, 10, 29, new int[]{6, 12, 18, 24},
                new int[][]{{3, 4}, {8, 10}, {14, 16}, {20, 22}, {26, 27}}, 12, true, 2);
        Tower gate = tower(0, 17, 4, 37, new int[]{6, 12, 18, 24, 30},
                new int[][]{{8, 10}, {14, 16}, {20, 22}, {26, 28}}, 6, false, 3);
        int[][] rows = {{3, 4}, {8, 10}, {14, 16}};
        Tower west = tower(-18, 30, 9, WING_TOP, new int[]{6, 12}, rows, 10, true, 2);
        Tower east = tower(18, 30, 9, WING_TOP, new int[]{6, 12}, rows, 10, true, 2);
        Tower south = tower(0, 48, 9, WING_TOP, new int[]{6, 12}, rows, 10, true, 2);
        int[][] turretRows = {{8, 9}, {14, 15}, {20, 22}};
        Tower westTurret = tower(-28, 30, 3, 26, new int[]{6, 12, 18}, turretRows, 4, false, 3);
        Tower eastTurret = tower(28, 30, 3, 26, new int[]{6, 12, 18}, turretRows, 4, false, 3);
        Tower southTurret = tower(0, 58, 3, 26, new int[]{6, 12, 18}, turretRows, 4, false, 3);
        draw(m, gate, keep);
        draw(m, westTurret, west);
        draw(m, eastTurret, east);
        draw(m, southTurret, south);
        draw(m, west, keep, westTurret);
        draw(m, east, keep, eastTurret);
        draw(m, south, keep, southTurret);
        draw(m, keep, gate, west, east, south);
        belfry(m);
        if (!m.touches(-37, 0, 12, 37, WING_TOP + 12, 63)) {
            return;
        }
        towerEntrance(m, 0, 13, Direction.NORTH, Blocks.DARK_OAK_DOOR);
        // The keep: the great spiral round its post; the hall below, rooms of study, the house's library at the top.
        spiral(m, 0, 30, 24, new int[]{6, 12, 18, 24});
        for (int floor : new int[]{0, 6, 12, 18, 24}) {
            for (int[] at : new int[][]{{-5, 30}, {5, 30}, {0, 25}, {0, 35}}) {
                m.chandelier(at[0], floor + 6, at[1], 2);
            }
        }
        for (int side : new int[]{-1, 1}) {
            for (int z : new int[]{24, 36}) {
                m.set(side * 6, 1, z, Masonry.stairs(Blocks.DARK_OAK_STAIRS, side < 0 ? Direction.WEST : Direction.EAST));
            }
        }
        for (int floor : new int[]{6, 12}) {
            for (int sx : new int[]{-1, 1}) {
                for (int sz : new int[]{-1, 1}) {
                    int x = sx * 5;
                    int z = 30 + sz * 5;
                    m.set(x, floor + 1, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    m.set(x, floor + 2, z, Blocks.CANDLE.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
                    m.set(x + sx, floor + 1, z, Masonry.stairs(Blocks.SPRUCE_STAIRS, sx < 0 ? Direction.EAST : Direction.WEST));
                    m.set(x - sx, floor + 1, z, Masonry.stairs(Blocks.SPRUCE_STAIRS, sx < 0 ? Direction.WEST : Direction.EAST));
                }
            }
        }
        for (int floor : new int[]{18, 24}) {
            for (int dx = -9; dx <= 9; dx++) {
                for (int dz = -9; dz <= 9; dz++) {
                    // Round the wall, the way to the gate tower's alcove left free.
                    if (within(dx, dz, 9.5D) && !within(dx, dz, 8.5D) && !(dz < 0 && Math.abs(dx) <= 3)) {
                        m.fill(dx, floor + 1, 30 + dz, dx, floor + 3, 30 + dz, Blocks.BOOKSHELF.defaultBlockState());
                    }
                }
            }
            int index = Math.floorMod(floor * 13, 40);
            m.lectern(0, floor + 1, 36, Direction.NORTH, () -> UniversityPiece.runeGuide(index));
        }
        // The courses.
        for (Course course : COURSES) {
            course(m, course);
        }
        // The turrets: a ladder up to the lookout over the wing's roof; the gate tower's up to the belfry.
        BlockState ladder = Blocks.LADDER.defaultBlockState();
        m.fill(-30, 1, 30, -30, WING_TOP, 30, ladder.setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        m.fill(30, 1, 30, 30, WING_TOP, 30, ladder.setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
        m.fill(0, 1, 60, 0, WING_TOP, 60, ladder.setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
        for (int[] at : new int[][]{{-28, 30}, {28, 30}, {0, 58}}) {
            m.chandelier(at[0], 26, at[1], 3);
        }
        m.fill(0, 25, 14, 0, 30, 14, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
        for (int floor : new int[]{0, 6, 12, 18, 24}) {
            m.chandelier(0, floor + 6, 18, 2);
        }
    }

    /** The gate tower's belfry: pointed arches three wide on its four faces, a railing, the bell on its floor. */
    private static void belfry(Masonry m) {
        if (!m.touches(-5, 30, 12, 5, 36, 22)) {
            return;
        }
        for (Direction face : Direction.Plane.HORIZONTAL) {
            for (int s = -1; s <= 1; s++) {
                int x = face.getStepX() * 4 + (face.getStepZ() != 0 ? s : 0);
                int z = 17 + face.getStepZ() * 4 + (face.getStepX() != 0 ? s : 0);
                m.fill(x, 32, z, x, s == 0 ? 35 : 34, z, Blocks.AIR.defaultBlockState());
                m.set(x, 31, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            }
        }
        m.set(0, 31, 17, Blocks.BELL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BellBlock.FACING, Direction.NORTH)
                .setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT,
                        net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR));
        m.chandelier(0, 37, 17, 2);
    }

    /** A spiral stair round a post at {@code (cx, cz)}, a step a cell from y = 1 up to {@code to}; holes in the floors. */
    private static void spiral(Masonry m, int cx, int cz, int to, int[] floors) {
        int[][] ring = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int floor : floors) {
            for (int[] cell : ring) {
                m.set(cx + cell[0], floor, cz + cell[1], Blocks.AIR.defaultBlockState());
            }
        }
        m.fill(cx, 0, cz, cx, to + 4, cz, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
        for (int y = 1; y <= to; y++) {
            int[] here = ring[(y - 1) % 8];
            int[] next = ring[y % 8];
            Direction way = next[0] > here[0] ? Direction.EAST : next[0] < here[0] ? Direction.WEST
                    : next[1] > here[1] ? Direction.SOUTH : Direction.NORTH;
            m.set(cx + here[0], y, cz + here[1], Masonry.stairs(Blocks.DARK_OAK_STAIRS, way));
        }
    }

    /**
     * A course's floor: the wall across the wing with its door; the class toward the keep, benches facing the
     * master's lectern by that wall, an aisle down the middle; beyond it the master's room, the desk and its chair,
     * shelves round the wall.
     */
    private static void course(Masonry m, Course c) {
        int y = c.floor() + 1;
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        for (int v = -8; v <= 8; v++) {
            int[] at = c.at(0, v);
            if (within(at[0] - c.cx(), at[1] - c.cz(), 8.5D)) {
                m.fill(at[0], y, at[1], at[0], y + 4, at[1], v == 0 ? Blocks.AIR.defaultBlockState() : wall);
            }
        }
        int[] doorway = c.at(0, 0);
        m.set(doorway[0], y + 2, doorway[1], wall);
        Direction toward = c.towardKeep();
        RuneGuides.Family family = c.chair() == null ? null : c.chair().family().orElse(null);
        int index = Math.floorMod(c.cx() * 31 + c.cz() * 17 + c.floor(), 40);
        java.util.function.Supplier<net.minecraft.world.item.ItemStack> book = family != null
                ? () -> RuneGuides.familyGuide(family) : () -> UniversityPiece.runeGuide(index);
        int[] lectern = c.at(-1, 3);
        m.lectern(lectern[0], y, lectern[1], toward, book);
        for (int u = -3; u >= -7; u -= 2) {
            for (int v = -6; v <= 6; v++) {
                int[] at = c.at(u, v);
                if (Math.abs(v) > 1 && within(at[0] - c.cx(), at[1] - c.cz(), 7.5D)) {
                    m.set(at[0], y, at[1], Masonry.stairs(Blocks.SPRUCE_STAIRS, toward));
                }
            }
        }
        int[] desk = c.at(4, 0);
        int[] chair = c.at(5, 0);
        m.set(desk[0], y, desk[1], Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.set(desk[0], y + 1, desk[1], Blocks.CANDLE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        m.set(chair[0], y, chair[1], Masonry.stairs(Blocks.DARK_OAK_STAIRS, toward));
        for (int u = 2; u <= 8; u++) {
            for (int v = -6; v <= 6; v++) {
                int[] at = c.at(u, v);
                int dx = at[0] - c.cx();
                int dz = at[1] - c.cz();
                // Round the wall, the way to the turret left free.
                if (within(dx, dz, 8.5D) && !within(dx, dz, 7.5D) && Math.abs(v) > 1) {
                    m.fill(at[0], y, at[1], at[0], y + 2, at[1], Blocks.BOOKSHELF.defaultBlockState());
                }
            }
        }
        int[] classLight = c.at(-4, 0);
        int[] masterLight = c.at(4, 2);
        m.chandelier(classLight[0], c.floor() + 6, classLight[1], 2);
        m.chandelier(masterLight[0], c.floor() + 6, masterLight[1], 2);
    }

    // ------------------------------------------------------------------ the dormitories

    /** The dormitory tower: its middle, its radius (the middle of its wall), its levels and their height. */
    static final int DORM_X = -53;
    static final int DORM_Z = 38;
    private static final int DORM_R = 8;
    private static final int DORM_LEVELS = 8;
    private static final int DORM_STOREY = 5;

    /**
     * The dormitories, a round tower of eight levels (docs/universidade-arquitetura-design.md, 6.2): on the ground the
     * common room with its hearth and the hall of entrance; above, on each level, a wall across the middle making half a
     * room for two students (west) and half a hall with the stair (east); on the top level the dream's room, purple glass
     * and purple beds (docs/universidade-cursos-design.md, 8.2). Outside: a battered foot, a band at each floor, windows
     * all round, the hearth's chimney up the west face, corbels and battlements, a steep slate cone with lit windows.
     */
    static void dormitorios(Masonry m) {
        int r = DORM_R;
        int h = DORM_LEVELS * DORM_STOREY;
        ground(m, -63, 28, -43, 52, 2);
        if (!m.touches(-64, 0, 27, -42, h + 3 * r + 3, 52)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState bricks = Blocks.BRICKS.defaultBlockState();
        // The shell, its battered foot, its bands, its crown; the floors and the wall across each level.
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                int x = DORM_X + dx;
                int z = DORM_Z + dz;
                if (within(dx, dz, r - 0.5)) {
                    for (int i = 0; i <= DORM_LEVELS; i++) {
                        int f = i * DORM_STOREY;
                        m.set(x, f, z, i == DORM_LEVELS ? Blocks.DARK_OAK_SLAB.defaultBlockState()
                                : Blocks.SPRUCE_PLANKS.defaultBlockState());
                        if (i < DORM_LEVELS) {
                            m.fill(x, f + 1, z, x, f + DORM_STOREY - 1, z, dx == 0 && i == 0 ? stoneBricks : air);
                        }
                    }
                } else if (within(dx, dz, r + 0.5)) {
                    for (int y = 0; y <= h; y++) {
                        m.set(x, y, z, y <= 2 ? CampusHouse.plinth(m, x, y, z) : y % DORM_STOREY == 0
                                ? Blocks.POLISHED_ANDESITE.defaultBlockState() : m.stone(x, y, z));
                    }
                } else if (within(dx, dz, r + 1.5)) {
                    Direction out = outward(dx, dz);
                    m.set(x, 0, z, stoneBricks);
                    m.set(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
                    m.set(x, 2, z, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, out.getOpposite()));
                    m.set(x, h - 1, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, out.getOpposite()));
                    m.set(x, h, z, stoneBricks);
                    m.set(x, h + 1, z, stoneBricks);
                    if (Math.floorMod(dx + dz, 2) == 0) {
                        m.set(x, h + 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    }
                }
                if (within(dx, dz, r + 0.5)) {
                    m.set(x, h, z, stoneBricks);
                }
            }
        }
        // The windows all round, framed in polished stone (a sill would stick out askew from a round wall), of whole
        // glass (a pane on a round wall has its neighbours on the diagonal, joins nothing, and stands as a stick); on
        // the ground a block higher, over the batter.
        int[][] windows = {{-7, -4}, {-7, 4}, {-4, -7}, {-4, 7}, {4, -7}, {4, 7}, {7, -4}, {7, 4}};
        for (int i = 0; i < DORM_LEVELS; i++) {
            int f = i * DORM_STOREY;
            int low = i == 0 ? 3 : f + 2;
            BlockState glass = (i == DORM_LEVELS - 1 ? Blocks.PURPLE_STAINED_GLASS : Blocks.GLASS).defaultBlockState();
            for (int[] w : windows) {
                dormWindow(m, w[0], w[1], low, f + 4, glass);
            }
            if (i > 0) {
                dormWindow(m, r, 0, low, f + 4, glass);
            }
        }
        // The cone, steep, four lit windows in it: a block of purple glass, a slab over it (the author's, 27/10/2026).
        m.cone(DORM_X, h + 1, DORM_Z, r - 1, 3, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
        // The finial in place of the cone's top slab, on the full block under it, and the rod on the finial.
        m.set(DORM_X, h + 3 * r, DORM_Z, Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
        m.set(DORM_X, h + 1 + 3 * r, DORM_Z, Blocks.LIGHTNING_ROD.defaultBlockState());
        for (int[] c : new int[][]{{r - 1, 0}, {-(r - 1), 0}, {0, r - 1}, {0, -(r - 1)}}) {
            m.set(DORM_X + c[0], h + 2, DORM_Z + c[1], Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
            m.set(DORM_X + c[0], h + 3, DORM_Z + c[1], Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
            m.set(DORM_X + c[0] - Integer.signum(c[0]), h + 2, DORM_Z + c[1] - Integer.signum(c[1]),
                    Blocks.SHROOMLIGHT.defaultBlockState());
        }
        for (int i = 0; i < DORM_LEVELS; i++) {
            dormLevel(m, i);
        }
        stairTurret(m);
        // The common room's hearth and its hood; the flue up from it inside the west wall of every flat (a chimney
        // breast in each), out through the cone's slope and a few blocks above it, a cap of stairs round the fire at
        // its top, the smoke rising out of it.
        int hx = DORM_X - 7;
        int fx = DORM_X - 6;
        m.set(hx, 1, DORM_Z, Blocks.CAMPFIRE.defaultBlockState());
        for (int dz : new int[]{-1, 1}) {
            m.set(hx, 1, DORM_Z + dz, bricks);
            m.set(hx, 2, DORM_Z + dz, bricks);
        }
        m.fill(hx, 3, DORM_Z - 1, hx, 4, DORM_Z + 1, bricks);
        int stackTop = h + 10;
        m.fill(fx, 4, DORM_Z, fx, stackTop, DORM_Z, bricks);
        m.set(fx, stackTop + 1, DORM_Z, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, false));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            m.set(fx + side.getStepX(), stackTop + 1, DORM_Z + side.getStepZ(),
                    Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, side.getOpposite()));
        }
        // The common room: benches before the hearth, two tables, shelves round the curve, lanterns.
        for (int dz = -1; dz <= 1; dz++) {
            m.set(DORM_X - 5, 1, DORM_Z + dz, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
        }
        for (int s : new int[]{-1, 1}) {
            m.set(DORM_X - 4, 1, DORM_Z + s * 3, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
            m.set(DORM_X - 2, 1, DORM_Z + s * 4, Blocks.SPRUCE_PLANKS.defaultBlockState());
            m.set(DORM_X - 3, 1, DORM_Z + s * 4, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
        }
        for (int dx = -r; dx < 0; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (within(dx, dz, r - 0.5) && dx * dx + dz * dz > 42 && Math.abs(dz) >= 4) {
                    for (int y = 1; y <= 3; y++) {
                        if (m.get(DORM_X + dx, y, DORM_Z + dz).isAir()) {
                            m.set(DORM_X + dx, y, DORM_Z + dz, Blocks.BOOKSHELF.defaultBlockState());
                        }
                    }
                }
            }
        }
        for (int dz : new int[]{-4, 0, 4}) {
            m.chandelier(DORM_X - 3, DORM_STOREY, DORM_Z + dz, 1);
        }
        // The way in, east: the door in the wall, posts with lanterns either side, a hood, the house's banners.
        int ex = DORM_X + r;
        m.fill(ex + 1, 1, DORM_Z, ex + 1, 3, DORM_Z, air);
        dormDoor(m, ex, 1, DORM_Z);
        m.set(ex, 3, DORM_Z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.set(ex + 1, 0, DORM_Z, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int dz : new int[]{-1, 1}) {
            m.set(ex + 1, 1, DORM_Z + dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(ex + 1, 2, DORM_Z + dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(ex + 1, 3, DORM_Z + dz, Blocks.LANTERN.defaultBlockState());
        }
        for (int dz = -1; dz <= 1; dz++) {
            m.set(ex + 1, 4, DORM_Z + dz, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        }
        for (int dz : new int[]{-2, 2}) {
            m.set(ex + 1, 6, DORM_Z + dz, Blocks.PURPLE_WALL_BANNER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.EAST));
        }
    }

    /**
     * A level of the dormitory tower. The ground is the common room (west) and the hall (east): before the dividing wall
     * the grimoire of the floors on its lectern, a page to each floor ({@code ego aN_code ubis vocant}, read by aiming
     * surgit at the lectern open at it), and on the floor by the door the scroll one arrives on when coming down
     * ({@code a0_code}). Above the ground each level is a flat, for there is no stair to climb: a mage reads
     * their way up. On its floor lies the scroll one arrives on ({@code aN_code}), whose text sends them back down;
     * to each student a bed by the wall, a chest, a desk with its chair and candle, a barrel, a shelf; a table, shelves
     * by the east window, a carpet, lanterns; in the south wall the iron door to the stair turret, its button inside.
     */
    private static void dormLevel(Masonry m, int i) {
        int f = i * DORM_STOREY;
        int x = DORM_X;
        int z = DORM_Z;
        String code = m.houseCode();
        // The button of the iron door into the stair turret (the door is set with the turret, after its wall): the
        // door opens only from within.
        if (i > 0) {
            m.set(x + 1, f + 2, z + 7, Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ButtonBlock.FACE,
                            net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.NORTH));
        }
        for (int s : new int[]{-1, 1}) {
            m.chandelier(x + 4, f + DORM_STOREY, z + s * 5, 1);
        }
        if (i == 0) {
            for (int s : new int[]{-1, 1}) {
                for (int dx = 2; dx <= 4; dx++) {
                    m.set(x + dx, f + 1, z + s * 6, Masonry.stairs(Blocks.SPRUCE_STAIRS,
                            s < 0 ? Direction.NORTH : Direction.SOUTH));
                }
            }
            m.set(x + 1, f + 1, z - 7, Blocks.POTTED_AZURE_BLUET.defaultBlockState());
            dormDoor(m, x, f + 1, z);
            // The grimoire of the floors, chained to a lectern before the dividing wall: a page to each floor (ego
            // aN_code ubis vocant), read by aiming surgit at the lectern open at one's own; a sign over it says so.
            m.lectern(x + 2, 1, z - 3, Direction.EAST, () -> floorsGrimoire(code));
            sign(m, x + 1, 3, z - 3, Direction.EAST, "Grimório", "dos andares:", "abra no seu andar", "e leia (surgit)");
            placedScroll(m, x + 5, 0, z, Direction.UP, "", Porter.arrival(code, 0));
            return;
        }
        boolean dream = i == DORM_LEVELS - 1;
        // The scroll one arrives on, in the middle of the flat; reading it takes one down.
        placedScroll(m, x + 3, f, z, Direction.UP, "ego " + Porter.arrival(code, 0) + " ubis vocant",
                Porter.arrival(code, i));
        sign(m, x + 2, f + 2, z + 7, Direction.NORTH, "Para descer:", Porter.arrival(code, i), "surgit");
        for (int s : new int[]{-1, 1}) {
            bed(m, x - 6, f + 1, z + s * 4, Direction.EAST, dream);
            m.set(x - 6, f + 1, z + s * 2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
            m.set(x - 2, f + 1, z + s * 6, Blocks.SPRUCE_PLANKS.defaultBlockState());
            m.set(x - 3, f + 1, z + s * 6, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
            m.set(x - 2, f + 2, z + s * 6, Blocks.CANDLE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
            m.set(x - 1, f + 1, z + s * 7, Blocks.BARREL.defaultBlockState());
            m.set(x - 1, f + 1, z + s * 2, Blocks.BOOKSHELF.defaultBlockState());
            // By the east window, shelves either side of it.
            m.fill(x + 7, f + 1, z + s * 2, x + 7, f + 2, z + s * 2, Blocks.BOOKSHELF.defaultBlockState());
        }
        // A table with a chair either side, its candle; a wardrobe; a plant.
        m.set(x + 4, f + 1, z - 3, Blocks.SPRUCE_PLANKS.defaultBlockState());
        m.set(x + 4, f + 2, z - 3, Blocks.CANDLE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        m.set(x + 3, f + 1, z - 3, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
        m.set(x + 5, f + 1, z - 3, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
        m.set(x + 5, f + 1, z + 5, Blocks.BARREL.defaultBlockState());
        m.set(x + 6, f + 1, z + 4, Blocks.BARREL.defaultBlockState());
        m.set(x + 4, f + 1, z + 6, (dream ? Blocks.POTTED_ALLIUM : Blocks.POTTED_FERN).defaultBlockState());
        BlockState carpet = (dream ? Blocks.PURPLE_CARPET : Blocks.LIGHT_BLUE_CARPET).defaultBlockState();
        for (int dx = -4; dx <= -2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx + 3) * (dx + 3) + dz * dz <= 2) {
                    m.set(x + dx, f + 1, z + dz, carpet);
                }
            }
        }
        m.chandelier(x - 3, f + DORM_STOREY, z, 1);
    }

    /**
     * A scroll placed on the face {@code face} of the block at {@code (x, y, z)}, with {@code text} written on it and
     * {@code mark} on it: read with {@code <mark> surgit}, and found by the mark. Laid in the chunk its block is in.
     */
    private static void placedScroll(Masonry m, int x, int y, int z, Direction face, String text, String mark) {
        net.minecraft.core.BlockPos at = m.at(x, y, z);
        if (!m.chunk.isInside(at)) {
            return;
        }
        net.minecraft.world.item.ItemStack page = com.elderlexicon.mod.spelling.item.SpellScrollItem.of(text);
        com.elderlexicon.mod.spell.function.MarkHelper.applyMark(page, mark);
        m.level.addFreshEntity(new com.elderlexicon.mod.spelling.entity.PlacedScrollEntity(m.level.getLevel(), at,
                m.turn(face), page));
    }

    /** Who wrote the house's grimoires: the house, not any reader of them. */
    private static final java.util.UUID HOUSE = java.util.UUID.nameUUIDFromBytes(
            "elderlexicon:university".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /** The grimoire of the floors of the house of {@code code}: a page to each, named for it, chained to its lectern. */
    private static net.minecraft.world.item.ItemStack floorsGrimoire(String code) {
        net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(
                com.elderlexicon.mod.ElderLexicon.GRIMOIRE.get());
        net.minecraft.nbt.CompoundTag tag = book.getOrCreateTag();
        net.minecraft.nbt.ListTag pages = new net.minecraft.nbt.ListTag();
        net.minecraft.nbt.ListTag names = new net.minecraft.nbt.ListTag();
        for (int n = 1; n <= Porter.FLOORS; n++) {
            pages.add(net.minecraft.nbt.StringTag.valueOf("ego " + Porter.arrival(code, n) + " ubis vocant"));
            names.add(net.minecraft.nbt.StringTag.valueOf(n + "º andar" + (n == Porter.FLOORS ? ", o do sonho" : "")));
        }
        tag.put("pages", pages);
        tag.put(com.elderlexicon.mod.spelling.item.GrimoireItem.PAGE_NAMES_TAG, names);
        tag.putString(com.elderlexicon.mod.spelling.item.GrimoireItem.AUTHOR_TAG, "A Universidade");
        tag.putUUID(com.elderlexicon.mod.spelling.item.GrimoireItem.AUTHOR_ID_TAG, HOUSE);
        tag.putBoolean(com.elderlexicon.mod.spelling.LecternGrimoires.CHAINED_TAG, true);
        com.elderlexicon.mod.spelling.item.GrimoireItem.setLeaves(book, Porter.FLOORS);
        com.elderlexicon.mod.spell.block.WrittenTexts.mark(tag);
        book.setHoverName(net.minecraft.network.chat.Component.literal("Grimório dos andares"));
        return book;
    }

    /** A waxed spruce sign on the wall at {@code (x, y, z)}, facing {@code facing}, with up to four lines. */
    private static void sign(Masonry m, int x, int y, int z, Direction facing, String... lines) {
        if (!m.set(x, y, z, Blocks.SPRUCE_WALL_SIGN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallSignBlock.FACING, facing))) {
            return;
        }
        if (m.level.getBlockEntity(m.at(x, y, z))
                instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            net.minecraft.world.level.block.entity.SignText text = new net.minecraft.world.level.block.entity.SignText();
            for (int line = 0; line < Math.min(4, lines.length); line++) {
                text = text.setMessage(line, net.minecraft.network.chat.Component.literal(lines[line]));
            }
            // Written as a saved sign is read: while the world is being made the sign has no level yet, and setText
            // would tell a level that is not there (a crash, 21/10/2026).
            net.minecraft.nbt.CompoundTag saved = sign.saveWithoutMetadata();
            net.minecraft.world.level.block.entity.SignText.DIRECT_CODEC
                    .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, text).result()
                    .ifPresent(written -> saved.put("front_text", written));
            saved.putBoolean("is_waxed", true);
            sign.load(saved);
        }
    }

    /**
     * The stair turret against the dormitory tower's south face, for need: a ladder up inside it, a landing with a
     * lantern at each floor before the iron door into the flat (which opens only from within: one who climbs does not
     * walk into another's room), a door out at its foot, slits in its wall, a little cone over it.
     */
    private static void stairTurret(Masonry m) {
        int h = DORM_LEVELS * DORM_STOREY;
        int tx = DORM_X;
        int tz = DORM_Z + DORM_R + 2;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = tx + dx;
                int z = tz + dz;
                if (within(dx, dz, 1.5)) {
                    m.set(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
                    m.fill(x, 1, z, x, h + 3, z, air);
                } else if (within(dx, dz, 2.5)) {
                    for (int y = 0; y <= h + 3; y++) {
                        m.set(x, y, z, y <= 1 ? CampusHouse.plinth(m, x, y, z) : y % DORM_STOREY == 0
                                ? Blocks.POLISHED_ANDESITE.defaultBlockState() : m.stone(x, y, z));
                    }
                }
            }
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST);
        for (int y = 1; y <= h - DORM_STOREY + 2; y++) {
            m.set(tx - 1, y, tz, ladder);
        }
        for (int i = 0; i < DORM_LEVELS; i++) {
            int f = i * DORM_STOREY;
            if (i > 0) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (!(dx == -1 && dz == 0)) {
                            m.set(tx + dx, f, tz + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
                        }
                    }
                }
                // The way from the landing to the door, and the door, in the tower's wall.
                m.fill(tx, f + 1, tz - 1, tx, f + 2, tz - 1, air);
                door(m, tx, f + 1, DORM_Z + DORM_R, Blocks.IRON_DOOR, Direction.SOUTH);
            }
            m.set(tx + 1, f + 1, tz + 1, Blocks.LANTERN.defaultBlockState());
            // Slits east and south (the west wall holds the ladder).
            m.set(tx + 2, f + 3, tz, Blocks.GLASS.defaultBlockState());
            m.set(tx, f + 3, tz + 2, Blocks.GLASS.defaultBlockState());
        }
        // The door out at its foot, a step before it.
        door(m, tx, 1, tz + 2, Blocks.SPRUCE_DOOR, Direction.SOUTH);
        m.fill(tx, 1, tz + 3, tx, 2, tz + 3, air);
        m.set(tx, 0, tz + 3, Blocks.POLISHED_ANDESITE.defaultBlockState());
        m.cone(tx, h + 4, tz, 2, 2, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
        m.set(tx, h + 9, tz, Blocks.LIGHTNING_ROD.defaultBlockState()); // in place of the cone's top slab
    }

    /** A door of {@code block} at {@code (x, y, z)}, facing {@code facing}. */
    private static void door(Masonry m, int x, int y, int z, Block block, Direction facing) {
        BlockState door = block.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, facing);
        m.set(x, y, z, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        m.set(x, y + 1, z, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }

    /** A window of the dormitory tower at {@code (dx, dz)} of its ring, glazed from {@code low} to {@code high}. */
    private static void dormWindow(Masonry m, int dx, int dz, int low, int high, BlockState glass) {
        int x = DORM_X + dx;
        int z = DORM_Z + dz;
        for (int y = low; y <= high; y++) {
            m.set(x, y, z, glass);
        }
        m.set(x, low - 1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
    }

    /** A spruce door at {@code (x, y, z)} opening to the east. */
    private static void dormDoor(Masonry m, int x, int y, int z) {
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.EAST);
        m.set(x, y, z, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        m.set(x, y + 1, z, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }

    /** Whether {@code (dx, dz)} from a round tower's middle is within {@code radius}. */
    private static boolean within(int dx, int dz, double radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /** The way a cell of a round wall at {@code (dx, dz)} faces out: its larger offset's way. */
    private static Direction outward(int dx, int dz) {
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static void bed(Masonry m, int x, int y, int z, Direction toward, boolean dream) {
        BlockState bed = (dream ? Blocks.PURPLE_BED : Blocks.BLUE_BED).defaultBlockState()
                .setValue(BedBlock.FACING, toward.getOpposite());
        m.set(x, y, z, bed.setValue(BedBlock.PART, BedPart.HEAD));
        m.set(x + toward.getStepX(), y, z, bed.setValue(BedBlock.PART, BedPart.FOOT));
    }

    // ------------------------------------------------------------------ the houses of use, made of towers

    /**
     * A round tower of the house, the mod's mark (the dormitory tower's model; docs/universidade-arquitetura-design.md,
     * 6.2): the houses of use are each a few of them of different heights, joined, open into one another.
     *
     * @param cx      its middle, along x
     * @param cz      its middle, along z
     * @param r       the radius of its wall
     * @param top     the top of its wall, where its roof is
     * @param floors  the heights of the floors within it
     * @param rows    each {low, high}: a row of windows all round, glazed from low to high
     * @param n       how many windows to a row
     * @param parapet whether it is crowned with a parapet and battlements (the cone within), or its cone overhangs an eave
     * @param step    how many rows of the cone to each step in (3 steep, 2 lower)
     * @param cone    what its cone is of
     * @param edge    what the cone's open edges are of (a slab: nothing is born on a slab)
     */
    private record Tower(int cx, int cz, int r, int top, int[] floors, int[][] rows, int n, boolean parapet,
                         int step, BlockState cone, BlockState edge) {

        int coneRadius() {
            return parapet ? r - 1 : r + 1;
        }

        int apex() {
            return top + 1 + (coneRadius() + 1) * step - 1;
        }

        /** The height of the cone's surface over {@code (dx, dz)} from its middle. */
        int coneTop(int dx, int dz) {
            int ring = (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) - 0.5D);
            return top + 1 + (coneRadius() - ring) * step + step - 1;
        }

        /** Whether {@code (x, z)} is within its wall. */
        boolean holds(int x, int z) {
            return within(x - cx, z - cz, r - 0.5D);
        }
    }

    /** A tower with a slate cone. */
    private static Tower tower(int cx, int cz, int r, int top, int[] floors, int[][] rows, int n, boolean parapet,
                               int step) {
        return new Tower(cx, cz, r, top, floors, rows, n, parapet, step, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
    }

    /**
     * Draws {@code t}: its floor and the air within, its floors, its roof; its wall of stone on a plinth of rough stone,
     * a band at each floor and at the top; its battered foot; corbels and a parapet, or corbels under the eave; its
     * windows all round, of whole glass framed in polished stone; its cone with the rod in place of the top slab. Where
     * its wall would stand within one of {@code open} it is left out below the lower of the two roofs: there the towers
     * open into each other, and above it the taller one's wall rises on.
     */
    private static void draw(Masonry m, Tower t, Tower... open) {
        int reach = t.r() + 2;
        if (!m.touches(t.cx() - reach, 0, t.cz() - reach, t.cx() + reach, t.apex() + 12, t.cz() + reach)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = t.cx() + dx;
                int z = t.cz() + dz;
                int opened = openedTo(t, x, z, open);
                if (within(dx, dz, t.r() - 0.5D)) {
                    m.set(x, 0, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    m.fill(x, 1, z, x, t.top() - 1, z, air);
                    for (int floor : t.floors()) {
                        m.set(x, floor, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    }
                    m.set(x, t.top(), z, stoneBricks);
                } else if (within(dx, dz, t.r() + 0.5D)) {
                    for (int y = opened + 1; y <= t.top(); y++) {
                        boolean band = y == t.top();
                        for (int floor : t.floors()) {
                            band |= y == floor;
                        }
                        m.set(x, y, z, y <= 1 ? CampusHouse.plinth(m, x, y, z) : band
                                ? Blocks.POLISHED_ANDESITE.defaultBlockState() : m.stone(x, y, z));
                    }
                } else if (within(dx, dz, t.r() + 1.5D) && opened < 0) {
                    Direction in = outward(dx, dz).getOpposite();
                    m.set(x, 0, z, stoneBricks);
                    m.set(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
                    m.set(x, 2, z, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, in));
                    m.set(x, t.top() - (t.parapet() ? 1 : 0), z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, in));
                    if (t.parapet()) {
                        m.set(x, t.top(), z, stoneBricks);
                        m.set(x, t.top() + 1, z, stoneBricks);
                        if (Math.floorMod(dx + dz, 2) == 0) {
                            m.set(x, t.top() + 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                        }
                    }
                }
            }
        }
        for (int[] row : t.rows()) {
            for (int k = 0; k < t.n(); k++) {
                double angle = (k + 0.5D) * 2.0D * Math.PI / t.n();
                int dx = (int) Math.round(t.r() * Math.cos(angle));
                int dz = (int) Math.round(t.r() * Math.sin(angle));
                if (within(dx, dz, t.r() - 0.5D) || !within(dx, dz, t.r() + 0.5D)) {
                    continue;
                }
                if (openedTo(t, t.cx() + dx, t.cz() + dz, open) >= row[0]) {
                    continue;
                }
                for (int y = row[0]; y <= row[1]; y++) {
                    m.set(t.cx() + dx, y, t.cz() + dz, Blocks.GLASS.defaultBlockState());
                }
                m.set(t.cx() + dx, row[0] - 1, t.cz() + dz, Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        m.cone(t.cx(), t.top() + 1, t.cz(), t.coneRadius(), t.step(), t.cone(), t.edge());
        m.set(t.cx(), t.apex(), t.cz(), Blocks.LIGHTNING_ROD.defaultBlockState()); // in place of the top slab
    }

    /** How high {@code t}'s wall is left open at {@code (x, z)}, within one of {@code open}: below the lower roof. */
    private static int openedTo(Tower t, int x, int z, Tower... open) {
        int opened = -1;
        for (Tower other : open) {
            if (other.holds(x, z)) {
                opened = Math.max(opened, Math.min(t.top(), other.top()) - 1);
            }
        }
        return opened;
    }

    /**
     * A hearth in {@code t} at {@code (x, z)}, its mouth to {@code mouth}: the fire, bricks either side, and the flue up
     * from it out through the cone's slope a few blocks above it, capped with stairs round a fire whose smoke rises.
     */
    private static void hearth(Masonry m, Tower t, int x, int z, Direction mouth, Block fire) {
        BlockState bricks = Blocks.BRICKS.defaultBlockState();
        m.set(x, 1, z, fire.defaultBlockState());
        boolean sidesAlongZ = mouth.getAxis() == Direction.Axis.X;
        int stackTop = t.coneTop(x - t.cx(), z - t.cz()) + 3;
        m.fill(x, 3, z, x, stackTop, z, bricks);
        for (int side : new int[]{-1, 1}) {
            int sx = x + (sidesAlongZ ? 0 : side);
            int sz = z + (sidesAlongZ ? side : 0);
            m.fill(sx, 1, sz, sx, 3, sz, bricks);
        }
        m.set(x, stackTop + 1, z, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, false));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            m.set(x + side.getStepX(), stackTop + 1, z + side.getStepZ(),
                    Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, side.getOpposite()));
        }
    }

    /** The way into a tower at {@code (x, z)} of its wall, opening to {@code outward}: the door, posts, a hood. */
    private static void towerEntrance(Masonry m, int x, int z, Direction outward, Block block) {
        int ox = outward.getStepX();
        int oz = outward.getStepZ();
        boolean alongX = oz != 0;
        door(m, x, 1, z, block, outward);
        m.set(x, 3, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.fill(x + ox, 1, z + oz, x + ox, 2, z + oz, Blocks.AIR.defaultBlockState());
        m.set(x + ox, 0, z + oz, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int side = -1; side <= 1; side++) {
            int px = x + ox + (alongX ? side : 0);
            int pz = z + oz + (alongX ? 0 : side);
            if (side != 0) {
                m.set(px, 1, pz, Blocks.STONE_BRICK_WALL.defaultBlockState());
                m.set(px, 2, pz, Blocks.STONE_BRICK_WALL.defaultBlockState());
                m.set(px, 3, pz, Blocks.LANTERN.defaultBlockState());
            }
            m.set(px, 4, pz, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, outward.getOpposite()));
        }
    }

    private static BlockState stairs(Direction facing) {
        return Masonry.stairs(Blocks.SPRUCE_STAIRS, facing);
    }

    /**
     * The refectory, three towers (the plans, sheet 05, made of towers): the hall, low and broad under its battlements,
     * its long tables either side of the way from the door to the hearth, its pantry above; the kitchen, tall and
     * slim to the south, its hearth and stoves (what the lesson of vertere uses: to cook, to eat); and the stair
     * turret to the north-east, a ladder up to the pantry and on to a lookout.
     */
    static void refeitorio(Masonry m) {
        ground(m, -65, -12, -43, 17, 2);
        Tower kitchen = tower(-54, 11, 4, 24, new int[0], new int[][]{{3, 4}, {9, 11}, {16, 18}}, 6, false, 3);
        Tower hall = tower(-54, 0, 8, 14, new int[]{8}, new int[][]{{3, 6}, {10, 12}}, 10, true, 2);
        Tower stair = tower(-48, -7, 2, 20, new int[]{8, 14}, new int[][]{{11, 12}, {16, 18}}, 4, false, 3);
        draw(m, kitchen, hall);
        draw(m, stair, hall);
        draw(m, hall, kitchen, stair);
        if (!m.touches(-66, 0, -13, -42, 44, 18)) {
            return;
        }
        towerEntrance(m, -46, 0, Direction.EAST, Blocks.SPRUCE_DOOR);
        for (int z : new int[]{-3, 3}) {
            m.fill(-59, 1, z, -49, 1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int x = -59; x <= -49; x += 2) {
                m.set(x, 1, z - 1, stairs(Direction.NORTH));
                m.set(x, 1, z + 1, stairs(Direction.SOUTH));
            }
            for (int x : new int[]{-57, -53, -51}) {
                m.set(x, 2, z, Blocks.CANDLE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
            }
        }
        hearth(m, hall, -61, 0, Direction.EAST, Blocks.CAMPFIRE);
        for (int[] at : new int[][]{{-54, -4}, {-54, 4}, {-58, 0}, {-50, 0}}) {
            m.chandelier(at[0], 8, at[1], 2);
        }
        // The kitchen.
        hearth(m, kitchen, -54, 14, Direction.NORTH, Blocks.CAMPFIRE);
        m.set(-56, 1, 12, Blocks.SMOKER.defaultBlockState().setValue(
                net.minecraft.world.level.block.SmokerBlock.FACING, Direction.EAST));
        m.set(-56, 1, 10, Blocks.FURNACE.defaultBlockState().setValue(
                net.minecraft.world.level.block.FurnaceBlock.FACING, Direction.EAST));
        m.set(-52, 1, 12, Blocks.SMOKER.defaultBlockState().setValue(
                net.minecraft.world.level.block.SmokerBlock.FACING, Direction.WEST));
        m.set(-52, 1, 10, Blocks.BARREL.defaultBlockState());
        m.set(-55, 1, 13, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        m.set(-53, 1, 13, Blocks.CRAFTING_TABLE.defaultBlockState());
        m.chandelier(-54, 24, 11, 18);
        // The pantry above the hall, up the stair turret's ladder.
        BlockState ladder = Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH);
        m.fill(-48, 1, -8, -48, 14, -8, ladder);
        for (int[] at : new int[][]{{-58, -5}, {-50, -5}, {-58, 5}, {-50, 5}, {-60, -2}, {-60, 2}, {-48, 2}}) {
            m.set(at[0], 9, at[1], Blocks.BARREL.defaultBlockState());
        }
        m.set(-56, 9, 6, Blocks.HAY_BLOCK.defaultBlockState());
        m.set(-52, 9, 6, Blocks.HAY_BLOCK.defaultBlockState());
        m.chandelier(-54, 14, 0, 2);
        m.chandelier(-48, 20, -7, 3);
    }

    /**
     * The workshop, three towers (the plans, sheet 05, made of towers): the workshop, low and broad under its
     * battlements, the tables of galdraria, the bench of invocation, the Artificer's counter, a loft above; the forge,
     * the tallest, its cone of copper that greens with the years, the hearth of glowing stone under its chimney, the
     * anvil, the blast furnace; and the store, low, its chests round the wall.
     */
    static void oficina(Masonry m) {
        ground(m, 41, 15, 64, 39, 2);
        Tower forge = new Tower(57, 22, 4, 21, new int[0], new int[][]{{3, 4}, {10, 12}, {16, 18}}, 6, false, 3,
                Blocks.EXPOSED_CUT_COPPER.defaultBlockState(), Blocks.EXPOSED_CUT_COPPER_SLAB.defaultBlockState());
        Tower shop = tower(50, 28, 7, 13, new int[]{8}, new int[][]{{3, 6}, {10, 11}}, 10, true, 2);
        Tower store = tower(56, 34, 3, 9, new int[0], new int[][]{{3, 4}}, 4, false, 3);
        draw(m, forge, shop);
        draw(m, store, shop);
        draw(m, shop, forge, store);
        if (!m.touches(41, 0, 15, 64, 42, 39)) {
            return;
        }
        towerEntrance(m, 43, 28, Direction.WEST, Blocks.OAK_DOOR);
        Block table = Galdraria.TABLE.get();
        for (int[] at : new int[][]{{47, 25}, {47, 31}, {51, 25}, {51, 31}}) {
            m.set(at[0], 1, at[1], table.defaultBlockState());
        }
        m.set(53, 1, 28, com.elderlexicon.mod.invocation.Invocations.BENCH.get().defaultBlockState());
        m.fill(55, 1, 26, 55, 1, 30, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(
                net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
        m.set(55, 2, 28, Blocks.LANTERN.defaultBlockState());
        for (int z : new int[]{23, 33}) {
            m.fill(48, 1, z, 52, 2, z, Blocks.BOOKSHELF.defaultBlockState());
        }
        for (int[] at : new int[][]{{47, 28}, {50, 24}, {50, 32}}) {
            m.chandelier(at[0], 8, at[1], 2);
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH);
        m.fill(50, 1, 22, 50, 8, 22, ladder);
        for (int[] at : new int[][]{{46, 25}, {46, 31}, {54, 31}, {47, 33}, {53, 33}}) {
            m.set(at[0], 9, at[1], Blocks.BARREL.defaultBlockState());
        }
        m.chandelier(50, 13, 28, 2);
        // The forge.
        hearth(m, forge, 60, 21, Direction.WEST, Blocks.MAGMA_BLOCK);
        m.set(57, 1, 22, Blocks.ANVIL.defaultBlockState());
        m.set(55, 1, 20, Blocks.BLAST_FURNACE.defaultBlockState().setValue(
                net.minecraft.world.level.block.BlastFurnaceBlock.FACING, Direction.SOUTH));
        m.set(59, 1, 24, Blocks.SMITHING_TABLE.defaultBlockState());
        m.set(55, 1, 24, Blocks.GRINDSTONE.defaultBlockState());
        m.set(56, 1, 25, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        m.chandelier(57, 21, 22, 16);
        // The store.
        for (int[] at : new int[][]{{54, 35}, {56, 36}, {58, 35}, {58, 33}}) {
            m.chest(at[0], 1, at[1], Direction.NORTH, "chests/wizard_house");
        }
        m.chandelier(56, 9, 34, 2);
    }

    /**
     * The infirmary, three towers: the ward, low and broad under its battlements, white beds in two rows with their
     * heads to the wall and the way down the middle, the curers' stands at its end; the herb tower, the tallest, a bench
     * of brews below and a loft where the herbs dry; and a slim watch turret with a light in its high window.
     */
    static void enfermaria(Masonry m) {
        ground(m, 41, 40, 62, 59, 2);
        Tower herbs = tower(57, 44, 3, 19, new int[]{8}, new int[][]{{3, 4}, {10, 12}, {15, 17}}, 4, false, 3);
        Tower watch = tower(44, 55, 2, 16, new int[0], new int[][]{{8, 9}, {13, 14}}, 4, false, 3);
        Tower ward = tower(50, 49, 7, 12, new int[0], new int[][]{{3, 6}, {9, 10}}, 10, true, 2);
        draw(m, herbs, ward);
        draw(m, watch, ward);
        draw(m, ward, herbs, watch);
        if (!m.touches(41, 0, 40, 62, 36, 59)) {
            return;
        }
        towerEntrance(m, 43, 49, Direction.WEST, Blocks.BIRCH_DOOR);
        BlockState bed = Blocks.WHITE_BED.defaultBlockState();
        for (int x = 46; x <= 54; x += 2) {
            m.set(x, 1, 44, bed.setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
            m.set(x, 1, 45, bed.setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
            m.set(x, 1, 54, bed.setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
            m.set(x, 1, 53, bed.setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
        }
        m.set(55, 1, 48, Blocks.BREWING_STAND.defaultBlockState());
        m.set(55, 1, 50, Blocks.BREWING_STAND.defaultBlockState());
        m.set(56, 1, 49, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        for (int[] at : new int[][]{{47, 49}, {51, 49}, {49, 46}, {49, 52}}) {
            m.chandelier(at[0], 12, at[1], 4);
        }
        // The herb tower.
        BlockState ladder = Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH);
        m.fill(57, 1, 42, 57, 8, 42, ladder);
        m.set(58, 1, 45, Blocks.BREWING_STAND.defaultBlockState());
        m.set(56, 1, 45, Blocks.BARREL.defaultBlockState());
        for (int[] at : new int[][]{{56, 44}, {58, 44}, {57, 46}}) {
            m.set(at[0], 9, at[1], Blocks.HAY_BLOCK.defaultBlockState());
        }
        m.set(56, 9, 45, Blocks.POTTED_ALLIUM.defaultBlockState());
        m.set(58, 9, 45, Blocks.POTTED_CORNFLOWER.defaultBlockState());
        m.chandelier(57, 8, 44, 1);
        m.chandelier(57, 19, 44, 3);
        // The herb bed outside the door; the watch turret's light.
        for (int z = 45; z <= 47; z++) {
            m.set(41, 1, z, Blocks.ALLIUM.defaultBlockState());
            m.set(41, 1, z + 6, Blocks.AZURE_BLUET.defaultBlockState());
        }
        m.chandelier(44, 16, 55, 3);
    }

    /**
     * The gatehouse where the avenue leaves the campus for the village: two round towers with slate cones, the house's
     * mark, each with its door on the campus's side and a ladder up to its room; the pointed arch over the road between
     * them, the walk across its top, the house's banners on its outer face.
     */
    static void arco(Masonry m) {
        ground(m, 59, -10, 69, 10, 14);
        if (!m.touches(59, 0, -10, 69, 30, 10)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        m.fill(60, 0, -3, 68, 0, 3, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int side : new int[]{-1, 1}) {
            int cz = side * 6;
            roundTower(m, 64, cz, 3, 12, new int[]{6},
                    new int[][]{{0, side * 3, 8, 10}, {3, 0, 8, 10}, {-3, 0, 8, 10}, {0, side * 3, 2, 4}},
                    null, Blocks.AIR, null, Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
            door(m, 61, 1, cz, Blocks.SPRUCE_DOOR, Direction.WEST);
            m.fill(60, 1, cz, 60, 2, cz, air);
            BlockState ladder = Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST);
            m.fill(66, 1, cz, 66, 6, cz, ladder);
            m.chandelier(64, 6, cz, 1);
            m.chandelier(64, 13, cz, 2);
        }
        // The arch over the road, pointed, on both faces; the walk across its top.
        for (int x : new int[]{62, 66}) {
            m.fill(x, 6, -3, x, 10, 3, Blocks.STONE_BRICKS.defaultBlockState());
            m.fill(x, 6, -2, x, 6, 2, air);
            m.fill(x, 7, -1, x, 7, 1, air);
            m.set(x, 6, -3, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
            m.set(x, 6, 3, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
            m.set(x, 7, -2, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
            m.set(x, 7, 2, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
            m.set(x, 8, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        }
        m.fill(63, 9, -2, 65, 9, 2, Blocks.STONE_BRICKS.defaultBlockState());
        m.fill(63, 10, -2, 65, 10, 2, Blocks.SPRUCE_SLAB.defaultBlockState());
        for (int z = -2; z <= 2; z++) {
            m.set(62, 11, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(66, 11, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
        }
        for (int z = -2; z <= 2; z += 2) {
            m.set(64, 8, z, hanging());
        }
        for (int z : new int[]{-2, 2}) {
            m.set(67, 9, z, Blocks.PURPLE_WALL_BANNER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.EAST));
            m.set(61, 9, z, Blocks.PURPLE_WALL_BANNER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.WEST));
        }
    }

    // ------------------------------------------------------------------ the round towers of the houses

    /**
     * A round tower at the corner of a house, the mod's mark (the dormitory tower's model; docs/universidade-arquitetura
     * -design.md, 6.2): a plinth of rough stone, a band at each floor and at the top, windows of whole glass, corbels
     * under an eave, a steep cone over it of {@code cone} (its edge of {@code edge}) with the rod in place of its top
     * slab. Where its ring falls within {@code host} (if any) it is not built: there the tower opens into the house. With a
     * hearth, at {@code hearth} from its middle, the fire is {@code fire} and the flue goes up inside and out through
     * the cone's slope, a few blocks above it, capped with stairs round a fire whose smoke rises out of it.
     *
     * @param windows each {dx, dz, low, high}: a window of the ring, glazed from low to high
     */
    private static void roundTower(Masonry m, int cx, int cz, int r, int top, int[] floors, int[][] windows,
                                   int[] hearth, Block fire, CampusHouse host, BlockState cone, BlockState edge) {
        int outer = r + 1;
        int step = 3;
        int apex = top + 1 + (outer + 1) * step - 1;
        if (!m.touches(cx - outer, 0, cz - outer, cx + outer, apex + 6, cz + outer)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                boolean inHost = host != null && x > host.x0() && x < host.x1() && z > host.z0() && z < host.z1();
                if (within(dx, dz, r - 0.5)) {
                    m.set(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
                    m.fill(x, 1, z, x, top, z, air);
                    for (int floor : floors) {
                        m.set(x, floor, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    }
                } else if (within(dx, dz, r + 0.5)) {
                    // Open into the house up to its height; above it, the tower's wall rises out of its roof.
                    for (int y = inHost ? host.height() + 1 : 0; y <= top; y++) {
                        boolean band = y == top;
                        for (int floor : floors) {
                            band |= y == floor;
                        }
                        m.set(x, y, z, y <= 1 ? CampusHouse.plinth(m, x, y, z) : band
                                ? Blocks.POLISHED_ANDESITE.defaultBlockState() : m.stone(x, y, z));
                    }
                } else if (within(dx, dz, outer + 0.5) && !inHost) {
                    // The eave's corbels, outside the wall under the cone's edge.
                    m.set(x, top, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, outward(dx, dz).getOpposite()));
                }
            }
        }
        for (int[] window : windows) {
            for (int y = window[2]; y <= window[3]; y++) {
                m.set(cx + window[0], y, cz + window[1], Blocks.GLASS.defaultBlockState());
            }
            m.set(cx + window[0], window[2] - 1, cz + window[1], Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        m.cone(cx, top + 1, cz, outer, step, cone, edge);
        m.set(cx, apex, cz, Blocks.LIGHTNING_ROD.defaultBlockState()); // in place of the cone's top slab
        if (hearth == null) {
            return;
        }
        int hx = cx + hearth[0];
        int hz = cz + hearth[1];
        m.set(hx, 1, hz, fire.defaultBlockState());
        // Where the cone's slope is over the flue, and the stack three blocks above it.
        int ring = (int) Math.ceil(Math.sqrt(hearth[0] * hearth[0] + hearth[1] * hearth[1]) - 0.5D);
        int stackTop = top + 1 + (outer - ring) * step + step - 1 + 3;
        BlockState bricks = Blocks.BRICKS.defaultBlockState();
        m.fill(hx, 3, hz, hx, stackTop, hz, bricks);
        boolean alongZ = Math.abs(hearth[0]) >= Math.abs(hearth[1]);
        for (int side : new int[]{-1, 1}) {
            int sx = hx + (alongZ ? 0 : side);
            int sz = hz + (alongZ ? side : 0);
            m.set(sx, 2, sz, bricks);
            m.set(sx, 3, sz, bricks);
        }
        m.set(hx, stackTop + 1, hz, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, false));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            m.set(hx + side.getStepX(), stackTop + 1, hz + side.getStepZ(),
                    Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, side.getOpposite()));
        }
    }

    // ------------------------------------------------------------------ who lives there

    /** The Auditor who keeps the dormitory tower, in its hall: it sends up whoever cannot read their way. */
    private static void porter(Masonry m) {
        net.minecraft.core.BlockPos at = m.at(DORM_X + 4, 1, DORM_Z + 3);
        if (!m.chunk.isInside(at)) {
            return;
        }
        com.elderlexicon.mod.wizard.Wizard keeper = com.elderlexicon.mod.ElderLexicon.WIZARD.get()
                .create(m.level.getLevel());
        if (keeper == null) {
            return;
        }
        keeper.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        keeper.finalizeSpawn(m.level, m.level.getCurrentDifficultyAt(at),
                net.minecraft.world.entity.MobSpawnType.STRUCTURE, null, null);
        keeper.joinUniversity(Rank.AUDITOR, null, 4);
        keeper.keepDoor(m.houseCode());
        m.level.addFreshEntityWithPassengers(keeper);
    }

    /** A researcher of the research tower, at {@code post}. */
    private static void researcher(Masonry m, int[] where, String post) {
        net.minecraft.core.BlockPos at = m.at(where[0], where[1], where[2]);
        if (!m.chunk.isInside(at)) {
            return;
        }
        com.elderlexicon.mod.wizard.Wizard keeper = com.elderlexicon.mod.ElderLexicon.WIZARD.get()
                .create(m.level.getLevel());
        if (keeper == null) {
            return;
        }
        keeper.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        keeper.finalizeSpawn(m.level, m.level.getCurrentDifficultyAt(at),
                net.minecraft.world.entity.MobSpawnType.STRUCTURE, null, null);
        keeper.joinUniversity(Rank.LECTOR, null, 3);
        keeper.serveAt(post);
        m.level.addFreshEntityWithPassengers(keeper);
    }

    static void people(Masonry m, CampusPiece.Kind kind) {
        switch (kind) {
            case NUCLEO -> {
                UniversityPiece.wizard(m, 0, 2, -52, Rank.RECTOR, Discipline.REITOR, 14);
                UniversityPiece.wizard(m, -44, 1, -30, Rank.MAGISTER, Discipline.ARQUIVO, 12);
                UniversityPiece.wizard(m, -46, 1, -33, Rank.LECTOR, null, 30);
                UniversityPiece.villager(m, -40, -40, VillagerProfession.LIBRARIAN);
                UniversityPiece.villager(m, -48, -40, VillagerProfession.LIBRARIAN);
                researcher(m, ATLAS_KEEPER, com.elderlexicon.mod.wizard.Wizard.POST_ATLAS);
                researcher(m, LIBRARIAN, com.elderlexicon.mod.wizard.Wizard.POST_LIBRARY);
            }
            case PATIO -> {
                UniversityPiece.wizard(m, 9, 1, 6, Rank.AUDITOR, null, 60);
                UniversityPiece.wizard(m, -9, 1, 6, Rank.AUDITOR, null, 60);
                UniversityPiece.wizard(m, 8, 1, -6, Rank.SCRIBA, null, 60);
                UniversityPiece.wizard(m, -8, 1, -6, Rank.SCRIBA, null, 60);
            }
            case AULAS -> {
                for (Course course : COURSES) {
                    if (course.chair() != null) {
                        int[] at = course.at(-2, 2);
                        UniversityPiece.wizard(m, at[0], course.floor() + 1, at[1], Rank.MAGISTER, course.chair(), 5);
                    }
                }
            }
            case DORMITORIOS -> porter(m);
            case ESTABULOS -> {
                for (int k : new int[]{0, 2, 4, 6}) {
                    double b = k * Math.PI / 4.0D + Math.PI / 8.0D;
                    animal(m, net.minecraft.world.entity.EntityType.HORSE, 54 + (int) Math.round(6 * Math.cos(b)),
                            106 + (int) Math.round(6 * Math.sin(b)));
                }
                animal(m, net.minecraft.world.entity.EntityType.COW, 66, 100);
                animal(m, net.minecraft.world.entity.EntityType.COW, 68, 112);
                animal(m, net.minecraft.world.entity.EntityType.SHEEP, 70, 108);
                animal(m, net.minecraft.world.entity.EntityType.SHEEP, 66, 116);
            }
            case ENFERMARIA -> {
                UniversityPiece.villager(m, 48, 47, VillagerProfession.CLERIC);
                UniversityPiece.villager(m, 48, 51, VillagerProfession.CLERIC);
            }
            default -> {
            }
        }
    }
}
