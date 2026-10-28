package com.elderlexicon.mod.university;

import com.elderlexicon.mod.galdraria.Galdraria;
import com.elderlexicon.mod.library.RuneGuides;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
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

import java.util.ArrayList;
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

    /**
     * A walk through {@code points} (x, z pairs), {@code half} to each side of its line: straight or slanting between
     * them, its turns rounded, paved like {@link #pave}.
     */
    private static void walk(Masonry m, int half, int... points) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i + 1 < points.length; i += 2) {
            minX = Math.min(minX, points[i]);
            maxX = Math.max(maxX, points[i]);
            minZ = Math.min(minZ, points[i + 1]);
            maxZ = Math.max(maxZ, points[i + 1]);
        }
        int reach = half + 1;
        if (!m.touches(minX - reach, 0, minZ - reach, maxX + reach, 0, maxZ + reach)) {
            return;
        }
        double width = half + 0.5D;
        for (int x = minX - reach; x <= maxX + reach; x++) {
            for (int z = minZ - reach; z <= maxZ + reach; z++) {
                boolean on = false;
                for (int i = 0; i + 3 < points.length && !on; i += 2) {
                    double ax = points[i];
                    double az = points[i + 1];
                    double bx = points[i + 2] - ax;
                    double bz = points[i + 3] - az;
                    double length = bx * bx + bz * bz;
                    double t = length == 0.0D ? 0.0D
                            : Math.max(0.0D, Math.min(1.0D, ((x - ax) * bx + (z - az) * bz) / length));
                    double ox = x - (ax + t * bx);
                    double oz = z - (az + t * bz);
                    on = ox * ox + oz * oz <= width * width;
                }
                if (on) {
                    m.set(x, 0, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                            : Blocks.STONE_BRICKS.defaultBlockState());
                }
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
        ground(m, -38, 67, 38, 122, 2);
        // The ways: in from the ring road down the middle, and across.
        pave(m, -1, 67, 1, 120);
        pave(m, -36, 93, 36, 95);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        Direction[] ringWays = {Direction.EAST, Direction.SOUTH, Direction.SOUTH, Direction.WEST, Direction.WEST,
                Direction.NORTH, Direction.NORTH, Direction.EAST};

        // The fall tower: inside, the spiral up to its top room; out of its north face, a board over the hay.
        Tower fall = tower(0, 102, 3, 27, new int[0], new int[][]{{6, 7}, {14, 15}}, 4, false, 3);
        draw(m, fall);
        if (m.touches(-5, 0, 86, 5, 30, 108)) {
            m.fill(0, 1, 98, 0, 2, 99, air); // the way in, through the foot of the wall too
            m.fill(-2, 22, 100, 2, 22, 104, Blocks.SPRUCE_PLANKS.defaultBlockState());
            slabStair(m, 0, 102, 22, new int[]{22});
            m.fill(0, 23, 99, 0, 24, 99, air);
            m.fill(0, 22, 96, 0, 22, 98, Blocks.SPRUCE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP));
            // Hay under all the way the jump can land, two deep.
            m.fill(-2, -1, 89, 2, 0, 98, Blocks.HAY_BLOCK.defaultBlockState());
            m.chandelier(-2, 27, 102, 2);
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
            footway(m, 23, 113);
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
            footway(m, 8, 111);
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

    /** The middle of the arena, between the escarp and the avenue down the fields' west side. */
    private static final int ARENA_X = -54;
    private static final int ARENA_Z = 88;
    /** Half the length of the arena's spine, north to south: the floor and the stands are round it. */
    private static final int ARENA_SPINE = 8;
    /** How far from the spine the floor reaches; the podium, the rows, the walk and the wall come after it. */
    private static final double ARENA_FLOOR = 5.5D;
    private static final int ARENA_ROWS = 4;
    private static final double ARENA_WALK = ARENA_FLOOR + 1 + ARENA_ROWS + 1;
    private static final double ARENA_WALL = ARENA_WALK + 1;
    private static final int ARENA_TOP = 10;
    private static final int ARENA_TOWER_R = 4;
    private static final int[] ARENA_TOWER_FLOORS = {ARENA_ROWS + 1, 11};

    /** How far {@code (x, z)} is from the arena's spine. */
    private static double arenaReach(int x, int z) {
        int dz = Math.max(0, Math.abs(z - ARENA_Z) - ARENA_SPINE);
        return Math.sqrt((x - ARENA_X) * (x - ARENA_X) + dz * dz);
    }

    /** The way out from the arena's spine at {@code (x, z)}. */
    private static Direction arenaOutward(int x, int z) {
        int dz = z - ARENA_Z;
        dz = dz > ARENA_SPINE ? dz - ARENA_SPINE : dz < -ARENA_SPINE ? dz + ARENA_SPINE : 0;
        return outward(x - ARENA_X, dz);
    }

    /**
     * The arena (docs/universidade-arquitetura-design.md, 2): an oval, long north to south, open to the sky. The floor
     * of the duels, of sandstone in bands, a grate in its middle; round it a podium with its rail and lanterns; the
     * stands, one ring of four rows all round, seats facing in; behind the top row a walk, and the outer wall with its
     * arches and battlements. A tower at each end, outside the wall: its door out, the stair up round its post, a door
     * onto the walk, and at its top the box, its balcony out over the walk looking down the floor. The gate east, a
     * tunnel under the stands. Below the floor, the pit of creatures, round and dark, a spawner of zombies in its middle,
     * a hatch by the gate and a ladder down (the creatures open no hatch): with four of them round, the game feels the
     * lesson of impediunt (surrounded).
     */
    static void arena(Masonry m) {
        int cx = ARENA_X;
        int cz = ARENA_Z;
        int spine = ARENA_SPINE;
        int reach = (int) Math.ceil(ARENA_WALL + 1.5D);
        int ends = spine + (int) Math.round(ARENA_WALL) + ARENA_TOWER_R - 1;
        // The ground, short of the escarp's towers west (the wall's foot lays its own).
        ground(m, cx - 10, cz - ends - ARENA_TOWER_R - 2, cx + reach - 1, cz + ends + ARENA_TOWER_R + 2, 2);
        Tower[] towers = {
                tower(cx, cz - ends, ARENA_TOWER_R, 19, ARENA_TOWER_FLOORS, new int[][]{{2, 3}, {7, 9}, {13, 15}}, 6,
                        true, 3),
                tower(cx, cz + ends, ARENA_TOWER_R, 19, ARENA_TOWER_FLOORS, new int[][]{{2, 3}, {7, 9}, {13, 15}}, 6,
                        true, 3)};
        for (Tower t : towers) {
            draw(m, t);
        }
        // From the towers' doors round to the avenue down the arena's east side, on along it to the fields' cross way.
        int north = cz - ends - ARENA_TOWER_R - 1;
        int south = cz + ends + ARENA_TOWER_R + 1;
        walk(m, 1, cx, north, cx + 2, north - 3, -42, north - 3, -38, north + 1, -38, 63);
        walk(m, 1, cx, south, cx + 2, south + 3, -42, south + 3, -38, south - 1, -38, 89);
        if (!m.touches(cx - reach, -8, cz - spine - reach, cx + reach, 14, cz + spine + reach)) {
            arenaTowers(m, towers);
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState andesite = Blocks.POLISHED_ANDESITE.defaultBlockState();
        int walk = ARENA_ROWS + 1;
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - spine - reach; z <= cz + spine + reach; z++) {
                double d = arenaReach(x, z);
                boolean inTower = false;
                for (Tower t : towers) {
                    inTower |= within(x - t.cx(), z - t.cz(), t.r() + 0.5D);
                }
                if (d > ARENA_WALL + 1.0D || inTower) {
                    continue;
                }
                Direction out = arenaOutward(x, z);
                boolean gate = Math.abs(z - cz) <= 1 && x > cx;
                if (d <= ARENA_FLOOR) {
                    // The floor: sandstone in bands, the grate over the pit.
                    boolean grate = within(x - cx, z - cz, 1.5D);
                    boolean edge = d > ARENA_FLOOR - 1.0D;
                    m.set(x, 0, z, grate ? Blocks.IRON_BARS.defaultBlockState()
                            : edge ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                            : Math.floorMod((int) Math.floor(d), 3) == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState()
                            : Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                    m.fill(x, 1, z, x, 8, z, air);
                } else if (d <= ARENA_FLOOR + 1.0D) {
                    // The barrier round the floor: a low wall of stone capped with polished stone, bars over it, a
                    // pillar with a lantern now and then; the spectators see through it, the duel stays on its side.
                    m.set(x, 0, z, gate ? andesite : stoneBricks);
                    m.fill(x, 1, z, x, 8, z, air);
                    if (!gate) {
                        m.set(x, 1, z, m.stone(x, 1, z));
                        m.set(x, 2, z, andesite);
                        m.set(x, 3, z, Blocks.IRON_BARS.defaultBlockState());
                    }
                } else if (d <= ARENA_FLOOR + 1.0D + ARENA_ROWS) {
                    // The stands: each row a block over the one before, a bench of slabs on stone (slabs, not
                    // stairs: round the curves stairs would meet at odd angles).
                    int row = (int) Math.floor(d - ARENA_FLOOR - 1.0D) + 1;
                    m.fill(x, 0, z, x, row, z, stoneBricks);
                    m.set(x, row + 1, z, Blocks.SPRUCE_SLAB.defaultBlockState());
                    m.fill(x, row + 2, z, x, 9, z, air);
                } else if (d <= ARENA_WALK) {
                    // The walk behind the top row.
                    m.fill(x, 0, z, x, walk - 1, z, stoneBricks);
                    m.set(x, walk, z, andesite);
                    m.fill(x, walk + 1, z, x, 9, z, air);
                } else if (d <= ARENA_WALL) {
                    // The outer wall, its band and battlements.
                    for (int y = 0; y <= ARENA_TOP; y++) {
                        m.set(x, y, z, y <= 1 ? CampusHouse.plinth(m, x, y, z) : y == ARENA_TOP - 1 ? andesite
                                : m.stone(x, y, z));
                    }
                    if (Math.floorMod(x + z, 2) == 0) {
                        m.set(x, ARENA_TOP + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    }
                    if (gate) {
                        m.set(x, 4, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
                    }
                } else {
                    // Its battered foot.
                    m.set(x, 0, z, stoneBricks);
                    m.set(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
                    m.set(x, 2, z, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, out.getOpposite()));
                }
                if (gate && d > ARENA_FLOOR) {
                    // The gate: a tunnel under the stands, the walk over it.
                    m.set(x, 0, z, andesite);
                    m.fill(x, 1, z, x, 3, z, air);
                }
            }
        }
        // The arches of the outer wall onto the campus from the walk, every fourth block along it, a lantern in every
        // other one; none by the gate or the towers.
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - spine - reach; z <= cz + spine + reach; z++) {
                double d = arenaReach(x, z);
                if (d <= ARENA_WALK || d > ARENA_WALL || Math.abs(z - cz) <= 2 && x > cx) {
                    continue;
                }
                boolean nearTower = false;
                for (Tower t : towers) {
                    nearTower |= within(x - t.cx(), z - t.cz(), t.r() + 2.5D);
                }
                int along = Math.abs(z - cz) <= spine ? z
                        : (int) Math.round(Math.atan2(x - cx, z - cz) * 8.0D);
                if (nearTower || Math.floorMod(along, 4) != 0) {
                    continue;
                }
                m.fill(x, walk + 1, z, x, walk + 2, z, air);
                m.set(x, walk + 3, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                if (Math.floorMod(along, 8) == 0) {
                    m.set(x, walk + 1, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
        // Lanterns on the podium's rail, all round.
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - spine - reach; z <= cz + spine + reach; z++) {
                double d = arenaReach(x, z);
                if (d > ARENA_FLOOR && d <= ARENA_FLOOR + 1.0D && Math.floorMod(x * 7 + z * 3, 9) == 0
                        && !(Math.abs(z - cz) <= 1 && x > cx)) {
                    m.set(x, 3, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                    m.set(x, 4, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
        // The gate's lanterns, inside on the podium and outside on posts.
        for (int dz : new int[]{-2, 2}) {
            m.set(cx + 6, 3, cz + dz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            m.set(cx + 6, 4, cz + dz, Blocks.LANTERN.defaultBlockState());
            m.fill(cx + reach, 1, cz + dz, cx + reach, 2, cz + dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(cx + reach, 3, cz + dz, Blocks.LANTERN.defaultBlockState());
        }
        // The pit of creatures: round and dark, seven deep, the spawner in its middle.
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (!within(dx, dz, 5.5D)) {
                    continue;
                }
                m.set(cx + dx, -7, cz + dz, stoneBricks);
                for (int y = -6; y <= -1; y++) {
                    m.set(cx + dx, y, cz + dz, within(dx, dz, 4.5D) ? air : m.stone(cx + dx, y, cz + dz));
                }
            }
        }
        if (m.set(cx, -6, cz, Blocks.SPAWNER.defaultBlockState())
                && m.level.getBlockEntity(m.at(cx, -6, cz))
                instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
            spawner.setEntityId(net.minecraft.world.entity.EntityType.ZOMBIE, m.level.getRandom());
        }
        // The way down: a hatch in the floor by the gate, a ladder down the pit's wall.
        m.set(cx + 4, 0, cz, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.Half.TOP));
        m.fill(cx + 4, -6, cz, cx + 4, -1, cz, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
        arenaTowers(m, towers);
    }

    /**
     * The arena's towers inside: the door out, away from the floor; the stair round the post up to the walk's floor and
     * the box's; the door onto the walk; and the box, open toward the floor onto its balcony out over the walk on
     * corbels, its rail, the masters' seats, the house's banners, a lantern on every floor.
     */
    private static void arenaTowers(Masonry m, Tower[] towers) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int walk = ARENA_ROWS + 1;
        int box = ARENA_TOWER_FLOORS[1];
        for (Tower t : towers) {
            int r = t.r();
            if (!m.touches(t.cx() - r - 3, 0, t.cz() - r - 3, t.cx() + r + 3, t.top(), t.cz() + r + 3)) {
                continue;
            }
            int s = t.cz() < ARENA_Z ? -1 : 1;
            Direction away = s < 0 ? Direction.NORTH : Direction.SOUTH;
            Direction toward = away.getOpposite();
            int x = t.cx();
            int door = t.cz() + s * r;
            door(m, x, 1, door, Blocks.SPRUCE_DOOR, away);
            m.set(x, 3, door, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            footway(m, x, door + s);
            slabStair(m, x, t.cz(), box, ARENA_TOWER_FLOORS);
            int inner = t.cz() - s * r;
            door(m, x, walk + 1, inner, Blocks.SPRUCE_DOOR, toward);
            m.set(x, walk + 3, inner, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            // The box: its front open onto the balcony.
            for (int dx = -2; dx <= 2; dx++) {
                int z = t.cz() - s * (Math.abs(dx) == 2 ? r - 1 : r);
                m.fill(x + dx, box + 1, z, x + dx, box + 3, z, air);
                m.set(x + dx, box + 4, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, away));
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int k = 1; k <= 2; k++) {
                    int z = t.cz() - s * (r + k);
                    m.set(x + dx, box, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                    m.set(x + dx, box - 1, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, away));
                    m.fill(x + dx, box + 1, z, x + dx, box + 3, z, air);
                    if (k == 2 || Math.abs(dx) == 2) {
                        m.set(x + dx, box + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    }
                }
            }
            for (int dx : new int[]{-2, -1, 1, 2}) {
                m.set(x + dx, box + 1, t.cz() - s * 2, Masonry.stairs(Blocks.DARK_OAK_STAIRS, away));
            }
            for (int dx : new int[]{-3, 3}) {
                m.set(x + dx, box + 2, t.cz(), Blocks.PURPLE_WALL_BANNER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING,
                                dx < 0 ? Direction.EAST : Direction.WEST));
            }
            m.chandelier(x + 2, walk, t.cz() + s * 2, 1);
            m.chandelier(x + 2, box, t.cz() + s * 2, 1);
            m.chandelier(x + 2, t.top(), t.cz() + s * 2, 4);
            m.set(x - 2, box + 1, t.cz() + s * 2, Blocks.LANTERN.defaultBlockState());
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
        ground(m, 40, 67, 66, 90, 2);
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
        // Water under the path in the middle of the beds (a slab under water, flush with the path), within reach of all
        // the farmland, so none of it dries.
        m.set(49, 0, 76, Blocks.STONE_BRICK_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP)
                .setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED, true));
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
            m.set(56 + at[0], 1, 78 + at[1], Blocks.POLISHED_ANDESITE.defaultBlockState());
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
     * of planks, an aisle from the door to the far wall and three stalls either side of it, gates of fence, hay; the
     * dovecote, a slim tower whose wall is all holes for the birds over the house's post at its foot; and the pasture
     * east, fenced, with its trough. Horses in the stalls, cows and sheep in the pasture (people).
     */
    static void estabulos(Masonry m) {
        ground(m, 40, 92, 69, 124, 2);
        Tower house = new Tower(54, 106, 8, 5, new int[0], new int[][]{{3, 3}}, 16, false, 1,
                Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_SLAB.defaultBlockState());
        Tower dove = tower(44, 119, 3, 14, new int[]{5}, new int[0][], 8, false, 3);
        draw(m, house);
        draw(m, dove);
        BlockState air = Blocks.AIR.defaultBlockState();
        if (m.touches(44, 0, 96, 64, 6, 116)) {
            // The floor: the aisle of cobbles from the door to the far wall, a line of stone down its middle; straw
            // in the stalls.
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    int x = 54 + dx;
                    int z = 106 + dz;
                    if (!within(dx, dz, 7.5D)) {
                        continue;
                    }
                    m.set(x, 0, z, Math.abs(dz) > 1
                            ? (m.chance(x, 0, z) < 70 ? Blocks.HAY_BLOCK : Blocks.COARSE_DIRT).defaultBlockState()
                            : dz == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                            : (m.chance(x, 0, z) < 25 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE)
                            .defaultBlockState());
                }
            }
            BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
            for (int side : new int[]{-1, 1}) {
                // The front on the aisle, one straight fence, so it joins.
                for (int dx = -7; dx <= 7; dx++) {
                    if (within(dx, 2 * side, 7.5D)) {
                        m.set(54 + dx, 1, 106 + 2 * side, fence);
                    }
                }
                // The partitions, a board below and a rail above, straight from the front to the wall; a post where
                // each meets the front, up to the ceiling.
                for (int px : new int[]{-2, 2}) {
                    for (int k = 3; within(px, k * side, 7.5D); k++) {
                        m.set(54 + px, 1, 106 + k * side, Blocks.SPRUCE_PLANKS.defaultBlockState());
                        m.set(54 + px, 2, 106 + k * side, fence);
                    }
                    m.fill(54 + px, 1, 106 + 2 * side, 54 + px, 4, 106 + 2 * side,
                            Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                }
                // Each stall: a gate in the middle of its front, the hay and the trough at the back, away from where
                // the horse stands.
                for (int i = 0; i < 3; i++) {
                    m.set(STALL_GATES[i], 1, 106 + 2 * side, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING,
                                    side < 0 ? Direction.NORTH : Direction.SOUTH));
                    m.set(54 + STALL_HAY[i][0], 1, 106 + STALL_HAY[i][1] * side, Blocks.HAY_BLOCK.defaultBlockState());
                    m.set(54 + STALL_TROUGH[i][0], 1, 106 + STALL_TROUGH[i][1] * side,
                            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
                }
            }
            // The aisle's far end: hay stacked, barrels of tack, a chest; lanterns down the aisle.
            for (int dz : new int[]{-1, 1}) {
                m.fill(61, 1, 106 + dz, 61, 2, 106 + dz, Blocks.HAY_BLOCK.defaultBlockState());
            }
            m.set(61, 1, 106, Blocks.BARREL.defaultBlockState());
            m.set(60, 1, 105, Blocks.BARREL.defaultBlockState());
            m.chest(60, 1, 107, Direction.WEST, "chests/wizard_house");
            for (int dx : new int[]{-4, 0, 4}) {
                m.chandelier(54 + dx, 5, 106, 1);
            }
            // The way in, west, wide enough for a horse.
            m.fill(46, 1, 105, 46, 3, 107, air);
            m.fill(45, 0, 105, 46, 0, 107, Blocks.POLISHED_ANDESITE.defaultBlockState());
            m.fill(45, 1, 105, 45, 3, 107, air);
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
            walk(m, 1, 43, 107, 43, 114);
            m.set(44, 3, 116, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            footway(m, 44, 115);
            m.set(45, 1, 120, Blocks.BARREL.defaultBlockState());
            m.lectern(43, 1, 120, Direction.NORTH, () -> net.minecraft.world.item.ItemStack.EMPTY);
            m.fill(46, 1, 119, 46, 5, 119, Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
            m.chandelier(44, 5, 118, 1);
            m.chandelier(44, 14, 119, 2);
        }
        if (m.touches(62, 0, 94, 70, 3, 122)) {
            // The pasture: fenced, a gate toward the stables, a trough, lights at its corners.
            for (int x = 63; x <= 69; x++) {
                for (int z : new int[]{95, 121}) {
                    m.set(x, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
            for (int z = 95; z <= 121; z++) {
                for (int x : new int[]{63, 69}) {
                    m.set(x, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
            m.set(63, 1, 106, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.EAST));
            m.fill(67, 0, 100, 67, 0, 103, Blocks.WATER.defaultBlockState());
            for (int[] at : new int[][]{{63, 95}, {69, 95}, {63, 121}, {69, 121}}) {
                m.set(at[0], 2, at[1], Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    /**
     * The stables' stalls north of the aisle (those south mirrored): where each one's gate is, its hay, its trough;
     * and where the horses stand, free all round (a horse is wider than a block).
     */
    private static final int[] STALL_GATES = {49, 54, 59};
    private static final int[][] STALL_HAY = {{-3, -6}, {-1, -7}, {3, -6}};
    private static final int[][] STALL_TROUGH = {{-4, -6}, {1, -7}, {4, -6}};
    private static final int[][] STALL_HORSES = {{-4, -4}, {4, -4}, {0, 5}, {4, 4}};

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
     * over the top rows; the doors at the front, beside the dais. The two turrets are its stairs: a door at the foot
     * of each from outside, the stair round its post up to a landing level with the top row, to the gallery and to a
     * lookout over the roof. Lanterns hang over the rows, under the gallery and on its rail, in every turret floor.
     */
    static void auditorio(Masonry m) {
        ground(m, 18, -113, 57, -77, 2);
        int cx = AUDITORIUM_X;
        int cz = AUDITORIUM_Z;
        int[] landings = {8, 12, 17};
        int gallery = landings[1];
        Tower hall = tower(cx, cz, 14, 16, new int[0], new int[][]{{9, 11}, {13, 15}}, 14, true, 2);
        int[][] turretRows = {{3, 5}, {9, 10}, {13, 15}, {18, 20}};
        Tower north = tower(cx + 10, cz - 12, 3, 22, landings, turretRows, 4, false, 3);
        Tower south = tower(cx + 10, cz + 12, 3, 22, landings, turretRows, 4, false, 3);
        draw(m, hall, north, south);
        draw(m, north, hall);
        draw(m, south, hall);
        // The way from the core's north gate to the south door, and on along the front to the south turret's door.
        pave(m, 32, -83, 34, -78);
        pave(m, 34, -79, 51, -78);
        walk(m, 1, 51, -78, 57, -84, 57, -107, 51, -113, 50, -113);
        // Out to the way from the square, where the auditorium stands in the layout of the axis.
        pave(m, 40, -83, 42, -78);
        pave(m, cx + 9, cz + 15, cx + 11, cz + 17);
        if (!m.touches(cx - 16, 0, cz - 16, cx + 16, 23, cz + 16)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        Tower[] turrets = {north, south};
        // The theatre.
        for (int dx = -13; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (!within(dx, dz, 13.5D) || x <= DAIS_X + 4 || north.holds(x, z) || south.holds(x, z)) {
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
                // Benches of slabs (stairs would meet at odd angles round the arcs).
                m.set(x, row, z, aisle || !seat ? planks : Blocks.SPRUCE_SLAB.defaultBlockState());
            }
        }
        // The dais, a block high, a step along its front; the master's lectern turned to the master, banners, a lamp
        // at each front corner; the carved floor before it.
        for (int x = DAIS_X - 3; x <= DAIS_X + 1; x++) {
            for (int z = cz - 5; z <= cz + 5; z++) {
                if (within(x - cx, z - cz, 13.5D)) {
                    m.set(x, 1, z, x > DAIS_X ? Masonry.stairs(Blocks.POLISHED_ANDESITE_STAIRS, Direction.WEST)
                            : Blocks.POLISHED_ANDESITE.defaultBlockState());
                }
            }
        }
        m.lectern(DAIS_X, 2, cz, Direction.WEST, () -> RuneGuides.familyGuide(RuneGuides.Family.FUNCTIONS));
        for (int dz : new int[]{-4, 4}) {
            m.set(DAIS_X - 1, 2, cz + dz, Blocks.PURPLE_BANNER.defaultBlockState());
            m.fill(DAIS_X, 2, cz + dz + Integer.signum(dz), DAIS_X, 3, cz + dz + Integer.signum(dz),
                    Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(DAIS_X, 4, cz + dz + Integer.signum(dz), Blocks.LANTERN.defaultBlockState());
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
        // The gallery round the back wall at the turrets' second landing, a railing on its edge, a lantern on every
        // fourth post and one hanging under its edge over the top rows.
        for (int dx = 4; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                if (!within(dx, dz, 13.5D) || within(dx, dz, 9.5D)) {
                    continue;
                }
                m.set(cx + dx, gallery, cz + dz, planks);
                m.fill(cx + dx, gallery + 1, cz + dz, cx + dx, 15, cz + dz, air);
                if (within(dx, dz, 10.5D) && !north.holds(cx + dx, cz + dz) && !south.holds(cx + dx, cz + dz)) {
                    m.set(cx + dx, gallery + 1, cz + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
                    if (Math.floorMod(dx * 5 + dz * 3, 4) == 0) {
                        m.set(cx + dx, gallery + 2, cz + dz, Blocks.LANTERN.defaultBlockState());
                        m.chandelier(cx + dx, gallery, cz + dz, 1);
                    }
                }
            }
        }
        // The turrets: the door in from outside, away from the hall; the stair round the post to the landing by the
        // top row, the gallery and the lookout; a lantern on every floor.
        for (Tower t : turrets) {
            int s = t.cz() < cz ? -1 : 1;
            Direction away = s < 0 ? Direction.NORTH : Direction.SOUTH;
            int door = t.cz() + s * t.r();
            door(m, t.cx(), 1, door, Blocks.SPRUCE_DOOR, away);
            m.set(t.cx(), 3, door, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            footway(m, t.cx(), door + s);
            m.fill(t.cx() - 1, 1, t.cz() + s * 2, t.cx() + 1, 3, t.cz() + s * 2, air);
            slabStair(m, t.cx(), t.cz(), landings[2], landings);
            for (int landing : landings) {
                m.chandelier(t.cx() + 2, landing, t.cz(), 1);
            }
            m.chandelier(t.cx() + 2, t.top(), t.cz(), 2);
        }
        // Over the rows: the great chandelier, a ring of lesser ones round it; over the carved floor two more.
        m.chandelier(cx, 16, cz, 6);
        for (int k = 0; k < 8; k++) {
            double angle = k * Math.PI / 4.0D;
            m.chandelier(cx + (int) Math.round(8.0D * Math.cos(angle)), 16, cz + (int) Math.round(8.0D * Math.sin(angle)),
                    5);
        }
        for (int dz : new int[]{-6, 6}) {
            m.chandelier(DAIS_X + 3, 16, cz + dz, 7);
        }
    }

    // ------------------------------------------------------------------ the core

    /** The walled core: the rectory, the library and the research tower, all of towers, the short wall and its gate. */
    static void nucleo(Masonry m) {
        ground(m, -60, -70, 54, -10, 14);
        // The ways inside: from the Great Hall's portal to the gate and on to the stair down to the courtyard; from the
        // Library's door and the research tower's.
        pave(m, -2, -31, 2, -10);
        pave(m, -33, -45, -28, -43);
        pave(m, -30, -45, -28, -23);
        pave(m, -30, -25, 0, -23);
        pave(m, 30, -49, 34, -47);
        pave(m, 30, -49, 32, -23);
        pave(m, 0, -25, 32, -23);
        // The rectory, the library and the research tower are pieces of their own (a layout may move each).
        UniversityPiece.garden(m, 36, 50, -32, -22);
        UniversityPiece.tree(m, -45, -26);
        UniversityPiece.tree(m, 18, -31);
        // The wall stands back from the core's edge, so its towers stand whole on the core's ground (over the edge,
        // the courtyard below would clear them away); between it and the edge, a walk with a parapet.
        wall(m, -60, -70, 54, CORE_WALL_SOUTH);
        if (m.touches(-60, 0, -10, 54, 2, -10)) {
            for (int x = -59; x <= 53; x++) {
                if (Math.abs(x) > 3) {
                    m.set(x, 1, -10, Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
            }
            for (int x : new int[]{-4, 4}) {
                m.set(x, 2, -10, Blocks.LANTERN.defaultBlockState());
            }
        }
        // The north gate, out to the auditorium, and the way to it between the rectory and the research tower.
        if (m.touches(18, 0, -71, 22, 6, -68)) {
            m.fill(19, 1, -70, 21, 4, -69, Blocks.AIR.defaultBlockState());
            m.set(18, 3, -68, Blocks.LANTERN.defaultBlockState());
            m.set(22, 3, -68, Blocks.LANTERN.defaultBlockState());
        }
        pave(m, 19, -70, 21, -25);
        for (int z = -34; z >= -60; z -= 8) {
            lampPost(m, -27, z);
            lampPost(m, 29, z);
        }
        for (int x : new int[]{-22, -14, -6, 6, 14, 22}) {
            lampPost(m, x, -26);
        }
    }

    /** The line of the core's south wall: back from the core's edge (z -10) by more than its towers reach. */
    private static final int CORE_WALL_SOUTH = -17;

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


    // ------------------------------------------------------------------ the layout of the axis (docs/.., 5.5)

    /**
     * The acropolis of the layout of the axis: the highest terrace, walled on its three inner sides (on the fourth, west,
     * the plateau's own rampart), its gate on the axis between two towers, the parvis before the Great Hall's portal,
     * the way round to the research tower, a garden south of the Hall, two trees.
     */
    static void acropole(Masonry m) {
        int height = 9;
        // East, with the gate on the axis; north and south, out near the terrace's edges, room for the Hall and the
        // research tower inside.
        acropolisWall(m, -18, -40, -18, 42, -1, 0, height);
        acropolisWall(m, -67, -40, -18, -40, 0, 1, height);
        acropolisWall(m, -67, 42, -18, 42, 0, -1, height);
        if (m.touches(-19, 0, -4, -18, 8, 4)) {
            m.fill(-19, 1, -3, -18, 6, 3, Blocks.AIR.defaultBlockState());
            m.fill(-18, 6, -3, -18, 6, 3, Blocks.IRON_BARS.defaultBlockState());
            m.set(-19, 6, -2, hanging());
            m.set(-19, 6, 2, hanging());
        }
        wallTower(m, -18, -40, 3, 17, null);
        wallTower(m, -18, 42, 3, 17, null);
        wallTower(m, -42, -40, 3, 14, null);
        wallTower(m, -42, 42, 3, 14, null);
        wallTower(m, -18, -7, 4, 18, Direction.WEST);
        wallTower(m, -18, 7, 4, 18, Direction.WEST);
        // The way from the stair's head through the gate to the portal, the parvis before it.
        pave(m, -17, -2, -12, 2);
        pave(m, -19, -3, -18, 3);
        pave(m, -27, -6, -20, 6);
        pave(m, -33, -2, -28, 2);
        // Round to the research tower's door, north of the Hall.
        pave(m, -24, -18, -20, -7);
        pave(m, -51, -18, -22, -16);
        UniversityPiece.garden(m, -64, -36, 20, 36);
        UniversityPiece.tree(m, -32, -30);
        UniversityPiece.tree(m, -28, 28);
        for (int[] at : new int[][]{{-21, -8}, {-21, 8}, {-27, -8}, {-27, 8}, {-36, -19}}) {
            lampPost(m, at[0], at[1]);
        }
    }

    /**
     * A run of the acropolis's wall from {@code (x0, z0)} to {@code (x1, z1)} (along x or along z), two thick (the second
     * course toward {@code (inX, inZ)}), battlements on top, a lantern every eight inside.
     */
    private static void acropolisWall(Masonry m, int x0, int z0, int x1, int z1, int inX, int inZ, int height) {
        if (!m.touches(Math.min(x0, x0 + inX), 0, Math.min(z0, z0 + inZ), Math.max(x1, x1 + inX), height + 1,
                Math.max(z1, z1 + inZ))) {
            return;
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 0; y <= height; y++) {
                    m.set(x, y, z, m.stone(x, y, z));
                    m.set(x + inX, y, z + inZ, m.stone(x + inX, y, z + inZ));
                }
                if (((x + z) & 1) == 0) {
                    m.set(x, height + 1, z, m.stone(x, height + 1, z));
                }
                if (Math.floorMod(x + z, 8) == 4 && m.get(x + inX * 2, 2, z + inZ * 2).isAir()) {
                    m.set(x + inX * 2, 2, z + inZ * 2, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }

    /**
     * The square of learning (the layout of the axis): between the teaching building, north, and the library, south; a
     * covered arcade round it, a court of grass inside, the reflecting pool down the axis split by a low bridge where the
     * way from the teaching building's door to the library's crosses it, a walk either side of the pool. Out of it, the
     * ways at its level: north-east past the teaching building to the auditorium, north-west under the acropolis's wall
     * to the refectory and the dormitories.
     */
    static void praca(Masonry m) {
        BlockState air = Blocks.AIR.defaultBlockState();
        // The walks either side of the pool, from the stair down to the gate court to the stair up to the acropolis;
        // the way across, from door to door.
        pave(m, -10, -5, 33, -3);
        pave(m, -10, 3, 33, 5);
        pave(m, -5, -2, -5, 2);
        pave(m, 28, -2, 33, 2);
        pave(m, 10, -39, 12, 19);
        // The reflecting pool: two deep, its rim flush with the walks, chiselled at the corners.
        if (m.touches(2, -2, -2, 27, 1, 2)) {
            for (int x = 2; x <= 27; x++) {
                for (int z = -2; z <= 2; z++) {
                    boolean rim = x == 2 || x == 27 || Math.abs(z) == 2;
                    boolean bridge = x >= 10 && x <= 12;
                    if (rim || bridge) {
                        m.set(x, 0, z, (x == 2 || x == 27) && Math.abs(z) == 2
                                ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                                : Blocks.POLISHED_ANDESITE.defaultBlockState());
                        continue;
                    }
                    m.set(x, -2, z, Blocks.STONE_BRICKS.defaultBlockState());
                    m.set(x, -1, z, Blocks.WATER.defaultBlockState());
                    m.set(x, 0, z, Blocks.WATER.defaultBlockState());
                }
            }
            for (int z : new int[]{-2, 2}) {
                m.set(10, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                m.set(12, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            }
        }
        // The arcade: a covered walk three wide round the court, open on both sides between piers, arches of stairs
        // turned under its beams, a flat roof; no pier where a way goes through it.
        int ax0 = -4;
        int az0 = -38;
        int ax1 = 32;
        int az1 = 18;
        if (m.touches(ax0, 0, az0, ax1, 7, az1)) {
            for (int x = ax0; x <= ax1; x++) {
                for (int z = az0; z <= az1; z++) {
                    int fromOut = Math.min(Math.min(x - ax0, ax1 - x), Math.min(z - az0, az1 - z));
                    if (fromOut > 2) {
                        continue;
                    }
                    boolean alongX = Math.min(z - az0, az1 - z) <= 2 && Math.min(x - ax0, ax1 - x) > 2;
                    boolean corner = Math.min(x - ax0, ax1 - x) <= 2 && Math.min(z - az0, az1 - z) <= 2;
                    int along = alongX ? x : z;
                    boolean through = alongX ? Math.abs(x - 11) <= 2 : Math.abs(z) <= 5;
                    m.set(x, 0, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                    m.fill(x, 1, z, x, 4, z, air);
                    m.set(x, 5, z, Blocks.STONE_BRICKS.defaultBlockState());
                    if (fromOut == 0) {
                        m.set(x, 6, z, Blocks.STONE_BRICK_SLAB.defaultBlockState());
                    }
                    if (fromOut == 1 && !corner) {
                        continue;
                    }
                    // The two faces of the walk (and its corners, all piers).
                    if (corner && fromOut != 1) {
                        boolean edgeX = Math.min(x - ax0, ax1 - x) == 0 || Math.min(x - ax0, ax1 - x) == 2;
                        boolean edgeZ = Math.min(z - az0, az1 - z) == 0 || Math.min(z - az0, az1 - z) == 2;
                        if (edgeX && edgeZ) {
                            m.fill(x, 1, z, x, 4, z, Blocks.STONE_BRICKS.defaultBlockState());
                        }
                        continue;
                    }
                    if (corner || through) {
                        if (through && !corner) {
                            m.set(x, 4, z, Blocks.STONE_BRICK_SLAB.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP));
                        }
                        continue;
                    }
                    Direction forward = alongX ? Direction.EAST : Direction.SOUTH;
                    switch (Math.floorMod(along, 4)) {
                        case 0 -> m.fill(x, 1, z, x, 4, z, Blocks.STONE_BRICKS.defaultBlockState());
                        case 1 -> m.set(x, 4, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS,
                                forward.getOpposite()));
                        case 3 -> m.set(x, 4, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, forward));
                        default -> m.set(x, 4, z, Blocks.STONE_BRICK_SLAB.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP));
                    }
                }
            }
            // Lanterns under the roof, down the middle of the walk.
            for (int x = ax0 + 1; x <= ax1 - 1; x += 6) {
                m.chandelier(x, 5, az0 + 1, 1);
                m.chandelier(x, 5, az1 - 1, 1);
            }
            for (int z = az0 + 7; z <= az1 - 7; z += 6) {
                if (Math.abs(z) > 5) {
                    m.chandelier(ax0 + 1, 5, z, 1);
                    m.chandelier(ax1 - 1, 5, z, 1);
                }
            }
        }
        for (int[] at : new int[][]{{0, -34}, {28, -34}, {0, 14}, {28, 14}}) {
            lampPost(m, at[0], at[1]);
        }
        // The ways out at this level, winding: north-east, past the teaching building, to the auditorium's door.
        walk(m, 1, 31, -39, 31, -44, 45, -58, 45, -81);
        // North-west, under the acropolis's wall, to the refectory's door, the dormitories' and their stair tower's.
        walk(m, 1, 2, -39, 2, -41, -3, -46, -24, -46, -29, -51, -29, -92, -34, -97, -43, -97);
        walk(m, 1, -29, -68, -39, -68);
        walk(m, 1, -29, -86, -52, -86);
        for (int[] at : new int[][]{{-15, -48}, {-31, -60}, {-31, -84}, {47, -62}, {47, -74}}) {
            lampPost(m, at[0], at[1]);
        }
    }

    // ------------------------------------------------------------------ the rectory and the library, of towers

    /**
     * The rectory, the Great Hall made of towers (docs/universidade-arquitetura-design.md, 6.2): the council's
     * rotunda, broad under its battlements. Its portal is the caster's pose, narrow below and opening above like arms
     * raised to cast. Inside, the round table in the middle with the house's crystal and candles, the chairs round it
     * facing in, a carpet from the door, the dais of the Rector to the north with its seat and lectern; a gallery all
     * round at the height of the towers' first floor, its rail on corbels, shelves of the council's records against
     * the wall, lanterns under it and over it; banners between the windows below. The two spired towers either side of
     * the portal hold the stairs up to the gallery and, over the hall's roof, the rooms of the records; the Rector's
     * tower behind, the tallest of the core, the dais in its foot, its rooms up a ladder.
     */
    static void reitoria(Masonry m) {
        int gallery = 8;
        Tower hall = tower(0, -44, 12, 20, new int[0], new int[][]{{3, 6}, {11, 16}}, 16, true, 2);
        int[] frontFloors = {gallery, 21};
        int[][] frontRows = {{11, 13}, {23, 26}};
        Tower west = tower(-7, -33, 3, 30, frontFloors, frontRows, 4, false, 3);
        Tower east = tower(7, -33, 3, 30, frontFloors, frontRows, 4, false, 3);
        int[] rectorFloors = {gallery, 20, 26};
        Tower rector = tower(0, -58, 5, 34, rectorFloors, new int[][]{{10, 12}, {22, 24}, {28, 31}}, 6, false, 3);
        draw(m, hall, west, east, rector);
        draw(m, west, hall);
        draw(m, east, hall);
        draw(m, rector, hall);
        if (!m.touches(-14, -1, -64, 14, 34, -29)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        Tower[] towers = {west, east, rector};
        // The portal: three wide and three high, shouldered by corbels, the keystone over its middle; the step out of
        // it clear of the hall's battered foot.
        m.fill(-1, 1, -32, 1, 3, -32, air);
        m.set(0, 4, -32, air);
        m.set(-1, 4, -32, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
        m.set(1, 4, -32, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        m.set(0, 5, -32, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) {
            footway(m, dx, -31);
        }
        // The rose window over it.
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
        // The floor: a ring of dark oak round the council's place.
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                if (within(dx, dz, 6.5D) && !within(dx, dz, 5.5D)) {
                    m.set(dx, 0, -44 + dz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                }
            }
        }
        // The council's round table, the chairs round it facing in, candles round its edge, a lantern in its middle;
        // the carpet from the door.
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (within(dx, dz, 3.5D)) {
                    m.set(dx, 1, -44 + dz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    if (!within(dx, dz, 2.5D) && Math.floorMod(dx * 3 + dz * 5, 4) == 0) {
                        m.set(dx, 2, -44 + dz, Blocks.CANDLE.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
                    }
                } else if (within(dx, dz, 4.5D) && !within(dx, dz, 3.5D) && Math.floorMod(dx + dz, 2) == 0
                        && Math.abs(dx) > 1) {
                    m.set(dx, 1, -44 + dz, Masonry.stairs(Blocks.DARK_OAK_STAIRS, outward(dx, dz)));
                }
            }
        }
        m.set(0, 2, -44, Blocks.LANTERN.defaultBlockState());
        m.fill(0, 0, -40, 0, 0, -33, Blocks.PURPLE_WOOL.defaultBlockState());
        // The dais, in the foot of the Rector's tower: a block high, a step along its front, a lamp either side. On it
        // the Rector's seat against the wall, its high back, the house's banner over it, candles either side; the
        // lectern before it, turned to the seat; the council's records round the wall; lanterns over it, under the
        // gallery.
        for (int dx = -4; dx <= 4; dx++) {
            for (int z = -62; z <= -54; z++) {
                if (within(dx, z + 58, 4.5D)) {
                    m.set(dx, 1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                    m.fill(dx, 2, z, dx, gallery - 1, z, air);
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            m.set(dx, 1, -53, Masonry.stairs(Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH));
        }
        for (int x : new int[]{-4, 4}) {
            m.fill(x, 1, -53, x, 2, -53, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(x, 3, -53, Blocks.LANTERN.defaultBlockState());
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int z = -62; z <= -58; z++) {
                if (within(dx, z + 58, 4.5D) && !within(dx, z + 58, 3.5D) && Math.abs(dx) > 1
                        && !(dx == 4 && z == -58)) {
                    m.fill(dx, 2, z, dx, 4, z, Blocks.BOOKSHELF.defaultBlockState());
                }
            }
        }
        m.fill(0, 2, -62, 0, 5, -62, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.set(0, 2, -61, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
        for (int dx : new int[]{-1, 1}) {
            m.set(dx, 2, -62, Blocks.POLISHED_ANDESITE.defaultBlockState());
            m.set(dx, 3, -62, Blocks.CANDLE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        }
        m.set(0, 5, -61, Blocks.PURPLE_WALL_BANNER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.SOUTH));
        m.lectern(0, 2, -57, Direction.NORTH, () -> RuneGuides.familyGuide(RuneGuides.Family.FONTS));
        for (int dx : new int[]{-2, 2}) {
            m.chandelier(dx, gallery, -56, 1);
            m.chandelier(dx, gallery, -60, 1);
        }
        // The gallery: its floor, the rail on corbels at its edge, the records against the wall; lanterns under its
        // edge and on its rail.
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                if (!within(dx, dz, 11.5D) || within(dx, dz, 7.5D)) {
                    continue;
                }
                int x = dx;
                int z = -44 + dz;
                boolean inTower = false;
                for (Tower t : towers) {
                    inTower |= t.holds(x, z);
                }
                m.set(x, gallery, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                if (!within(dx, dz, 8.5D)) {
                    if (!within(dx, dz, 10.5D) && !inTower && !(Math.abs(dx) <= 4 && dz > 0)) {
                        m.fill(x, gallery + 1, z, x, gallery + 2, z, Blocks.BOOKSHELF.defaultBlockState());
                    }
                    continue;
                }
                m.set(x, gallery + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
                m.set(x, gallery - 1, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, outward(dx, dz)));
            }
        }
        for (int k = 0; k < 16; k++) {
            double angle = k * Math.PI / 8.0D;
            int ux = (int) Math.round(9.5D * Math.cos(angle));
            int uz = (int) Math.round(9.5D * Math.sin(angle));
            m.chandelier(ux, gallery, -44 + uz, 1);
            int rx = (int) Math.round(8.0D * Math.cos(angle));
            int rz = (int) Math.round(8.0D * Math.sin(angle));
            if (within(rx, rz, 8.5D) && !within(rx, rz, 7.5D)) {
                m.set(rx, gallery + 2, -44 + rz, Blocks.LANTERN.defaultBlockState());
            }
            int sx = (int) Math.round(11.0D * Math.cos(angle + Math.PI / 16.0D));
            int sz = (int) Math.round(11.0D * Math.sin(angle + Math.PI / 16.0D));
            boolean shelf = within(sx, sz, 11.5D) && !within(sx, sz, 10.5D) && !(Math.abs(sx) <= 4 && sz > 0);
            for (Tower t : towers) {
                shelf &= !t.holds(sx, -44 + sz);
            }
            if (shelf) {
                m.set(sx, gallery + 3, -44 + sz, Blocks.LANTERN.defaultBlockState());
            }
        }
        // Banners between the windows below the gallery, but where the towers open in and over the portal.
        for (int k = 0; k < 16; k++) {
            double angle = k * Math.PI / 8.0D;
            int bx = (int) Math.round(11.0D * Math.cos(angle));
            int bz = (int) Math.round(11.0D * Math.sin(angle));
            if (!within(bx, bz, 11.5D) || Math.abs(bx) <= 3 && bz > 0) {
                continue;
            }
            boolean nearTower = false;
            for (Tower t : towers) {
                nearTower |= within(bx - t.cx(), -44 + bz - t.cz(), t.r() + 1.5D);
            }
            if (!nearTower) {
                m.set(bx, 4, -44 + bz, Blocks.PURPLE_WALL_BANNER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING,
                                outward(bx, bz).getOpposite()));
            }
        }
        // The great chandelier over the table, four lesser ones round it.
        m.chandelier(0, 20, -44, 8);
        for (int[] at : new int[][]{{-5, -44}, {5, -44}, {0, -49}, {0, -39}}) {
            m.chandelier(at[0], 20, at[1], 7);
        }
        // The towers of the portal: the stair up round the post to the gallery and the rooms of the records over the
        // hall's roof, their shelves away from the hall.
        for (Tower t : new Tower[]{west, east}) {
            slabStair(m, t.cx(), t.cz(), frontFloors[1], frontFloors);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = 1; dz <= 2; dz++) {
                    if (within(dx, dz, 2.5D) && !within(dx, dz, 1.5D)) {
                        m.fill(t.cx() + dx, frontFloors[1] + 1, t.cz() + dz, t.cx() + dx, frontFloors[1] + 2,
                                t.cz() + dz, Blocks.BOOKSHELF.defaultBlockState());
                    }
                }
            }
            m.chandelier(t.cx() + 2 * Integer.signum(t.cx()), t.top(), t.cz() - 1, 3);
            m.chandelier(t.cx() + 2 * Integer.signum(t.cx()), frontFloors[0], t.cz() - 1, 2);
            m.chandelier(t.cx() + 2 * Integer.signum(t.cx()), frontFloors[1], t.cz() - 1, 5);
        }
        // The Rector's tower: a ladder up its rooms from the dais; over the dais, at the gallery, his study, its desk
        // and books; above it more books, at the top his bed.
        m.fill(4, 2, -58, 4, 26, -58, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
        m.chandelier(-2, 20, -58, 8);
        m.chandelier(2, 26, -58, 2);
        for (int dx = -4; dx <= 2; dx++) {
            for (int z = -62; z <= -59; z++) {
                if (within(dx, z + 58, 4.5D) && !within(dx, z + 58, 3.5D)) {
                    m.fill(dx, gallery + 1, z, dx, gallery + 2, z, Blocks.BOOKSHELF.defaultBlockState());
                }
            }
        }
        m.set(0, gallery + 1, -60, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.set(0, gallery + 2, -60, Blocks.CANDLE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        m.set(0, gallery + 1, -59, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        m.chandelier(2, 34, -58, 3);
        m.set(-2, 21, -58, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.set(-2, 21, -57, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        m.set(-2, 22, -58, Blocks.CANDLE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
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
        // The stair of slabs up the well, and a bridge to each gallery from where it comes up to it.
        slabStair(m, -44, -44, 12, new int[]{6, 12});
        int[][] ring = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int floor : new int[]{6, 12}) {
            int[] out = ring[slabStairCell(floor)];
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
        // Last, nothing stands behind a window: the shelves there are taken away.
        unblock(m, main, reading);
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
        slabStair(m, 42, -48, 28, new int[]{7, 14, 21, 28});
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
    static void wallTower(Masonry m, int cx, int cz, int r, int top, Direction door) {
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
        ground(m, -17, -9, 17, 11, 8);
        pave(m, -16, -9, 16, 10);
        m.ring(0, 0, 0, 5, Blocks.STONE_BRICKS.defaultBlockState(), true);
        m.ring(0, 1, 0, 5, Blocks.STONE_BRICKS.defaultBlockState(), true);
        m.ring(0, 1, 0, 4, Blocks.WATER.defaultBlockState(), true);
        m.ring(0, 2, 0, 5, Blocks.STONE_BRICK_SLAB.defaultBlockState(), false);
        // The column, and a bowl on it with water, a lantern over the spout.
        m.fill(0, 1, 0, 0, 3, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.ring(0, 4, 0, 2, Blocks.STONE_BRICKS.defaultBlockState(), true);
        // The bowl's rim of wall at its corners, open in the middle of each side, where its water runs over and
        // falls into the basin.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx != 0 && dz != 0 && within(dx, dz, 2.5D) && !within(dx, dz, 1.5D)) {
                    m.set(dx, 5, dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
            }
        }
        m.ring(0, 5, 0, 1, Blocks.WATER.defaultBlockState(), true);
        // Set flowing: water laid while the world is made stands still until something stirs it.
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            m.spring(d[0], 5, d[1]);
        }
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
        for (int[] at : new int[][]{{-15, -9}, {15, -9}, {-15, 9}, {15, 9}, {-8, -9}, {8, -9}, {-8, 9}, {8, 9}}) {
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
        ground(m, -37, 21, 37, 63, 2);
        ground(m, -6, 12, 6, 20, 2);
        Tower keep = tower(0, 30, 10, 29, new int[]{6, 12, 18, 24},
                new int[][]{{3, 4}, {8, 10}, {14, 16}, {20, 22}, {26, 27}}, 12, true, 2);
        Tower gate = tower(0, 17, 4, 37, new int[]{6, 12, 18, 24, 30},
                new int[][]{{8, 10}, {14, 16}, {20, 22}, {26, 28}}, 6, false, 3);
        int[][] rows = {{3, 4}, {8, 10}, {14, 16}};
        // Twelve windows round a wing: none falls where the wall between the class and the master's room meets the
        // ring, nor on the axis toward the keep and the turret.
        Tower west = tower(-18, 30, 9, WING_TOP, new int[]{6, 12}, rows, 12, true, 2);
        Tower east = tower(18, 30, 9, WING_TOP, new int[]{6, 12}, rows, 12, true, 2);
        Tower south = tower(0, 48, 9, WING_TOP, new int[]{6, 12}, rows, 12, true, 2);
        int[][] turretRows = {{8, 9}, {14, 15}, {20, 22}};
        Tower westTurret = tower(-28, 30, 3, 26, new int[]{6, 12, 18}, turretRows, 4, false, 3);
        Tower eastTurret = tower(28, 30, 3, 26, new int[]{6, 12, 18}, turretRows, 4, false, 3);
        Tower southTurret = tower(0, 58, 3, 26, new int[]{6, 12, 18}, turretRows, 4, false, 3);
        // From the lowest to the tallest: a taller tower's inside is cleared last, so no roof or cone of a lower one
        // runs through it.
        draw(m, west, keep, westTurret);
        draw(m, east, keep, eastTurret);
        draw(m, south, keep, southTurret);
        draw(m, westTurret, west);
        draw(m, eastTurret, east);
        draw(m, southTurret, south);
        draw(m, keep, gate, west, east, south);
        draw(m, gate, keep);
        belfry(m);
        if (!m.touches(-37, 0, 12, 37, WING_TOP + 12, 63)) {
            return;
        }
        towerEntrance(m, 0, 13, Direction.NORTH, Blocks.DARK_OAK_DOOR);
        // The keep: the great spiral round its post; the hall below, rooms of study, the house's library at the top.
        slabStair(m, 0, 30, 24, new int[]{6, 12, 18, 24});
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
        // Up to the belfry by the west wall, where no window is.
        m.fill(-3, 25, 17, -3, 30, 17, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        for (int floor : new int[]{0, 6, 12, 18, 24}) {
            m.chandelier(0, floor + 6, 18, 2);
        }
        // Last, nothing stands behind a window: what the rooms put against the wall there is taken away.
        unblock(m, west, keep, westTurret);
        unblock(m, east, keep, eastTurret);
        unblock(m, south, keep, southTurret);
        unblock(m, westTurret, west);
        unblock(m, eastTurret, east);
        unblock(m, southTurret, south);
        unblock(m, keep, gate, west, east, south);
        unblock(m, gate, keep);
    }

    /**
     * Takes away what stands just inside a window of {@code t} (the windows {@link #draw} makes, with the same
     * {@code open}): a shelf against the wall there is gone, and the window looks into the room.
     */
    private static void unblock(Masonry m, Tower t, Tower... open) {
        for (int[] row : t.rows()) {
            for (int k = 0; k < t.n(); k++) {
                double angle = (k + 0.5D) * 2.0D * Math.PI / t.n();
                int dx = (int) Math.round(t.r() * Math.cos(angle));
                int dz = (int) Math.round(t.r() * Math.sin(angle));
                if (within(dx, dz, t.r() - 0.5D) || !within(dx, dz, t.r() + 0.5D)
                        || openedTo(t, t.cx() + dx, t.cz() + dz, open) >= row[0] || blinded(t, dx, dz, row, open)) {
                    continue;
                }
                int ix = t.cx() + (int) Math.round((t.r() - 1) * Math.cos(angle));
                int iz = t.cz() + (int) Math.round((t.r() - 1) * Math.sin(angle));
                for (int y = row[0] - 1; y <= row[1] + 1; y++) {
                    if (m.get(ix, y, iz).is(Blocks.BOOKSHELF)) {
                        m.set(ix, y, iz, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /** The gate tower's belfry: pointed arches three wide on its four faces on a sill of stone, the bell on its floor. */
    private static void belfry(Masonry m) {
        if (!m.touches(-5, 30, 12, 5, 36, 22)) {
            return;
        }
        for (Direction face : Direction.Plane.HORIZONTAL) {
            for (int s = -1; s <= 1; s++) {
                int x = face.getStepX() * 4 + (face.getStepZ() != 0 ? s : 0);
                int z = 17 + face.getStepZ() * 4 + (face.getStepX() != 0 ? s : 0);
                m.fill(x, 32, z, x, s == 0 ? 35 : 34, z, Blocks.AIR.defaultBlockState());
                m.set(x, 31, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        m.set(0, 31, 17, Blocks.BELL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BellBlock.FACING, Direction.NORTH)
                .setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT,
                        net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR));
        m.chandelier(0, 37, 17, 2);
    }

    /** Which cell round the post (of the ring, from the north, clockwise) the stair of slabs comes up on {@code floor}. */
    private static int slabStairCell(int floor) {
        return (2 * floor - 1) % 8;
    }

    /**
     * The first stair round a post, of slabs: one wide, round a post of wood, each step half a block above the last
     * (a slab low in its block, then one high in the next), a step to each cell round the post, four up to a turn, so
     * the climb never asks for a jump. From the ground up to {@code to}; a floor opens only over the last of the climb
     * up to it, where a head would meet it, and the stair comes out on it at its height.
     */
    private static void slabStair(Masonry m, int cx, int cz, int to, int[] floors) {
        int[][] ring = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        m.fill(cx, 0, cz, cx, to + 4, cz, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
        BlockState low = Blocks.DARK_OAK_SLAB.defaultBlockState();
        BlockState high = low.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP);
        for (int n = 0; ; n++) {
            double surface = 1.5D + n * 0.5D;
            if (surface > to + 1) {
                break;
            }
            int[] cell = ring[n % ring.length];
            for (int floor : floors) {
                if (surface > floor - 2.5D && surface <= floor + 0.5D) {
                    m.set(cx + cell[0], floor, cz + cell[1], Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int n = 0; ; n++) {
            double surface = 1.5D + n * 0.5D;
            if (surface > to + 1) {
                break;
            }
            int[] cell = ring[n % ring.length];
            int y = (int) Math.floor(surface);
            if (surface == y) {
                m.set(cx + cell[0], y - 1, cz + cell[1], high);
            } else {
                m.set(cx + cell[0], y, cz + cell[1], low);
            }
        }
    }

    /**
     * The broad stair round a pillar, the endless tower's (nahva/TowerRealm): a pillar of stone {@code 2 * pillar + 1}
     * wide (one block, or three as the tower's), the steps two wide round it, sixteen to a turn, each half a block above the last (a block, then a slab), so the climb never asks
     * for a jump; eight up to a turn. From the ground up to {@code to}; a floor opens only over the last of the climb up
     * to it, where a head would meet it, and the stair comes out on it at its height.
     */
    private static void broadStair(Masonry m, int cx, int cz, int to, int[] floors, int pillar) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = 0; y <= to + 4; y++) {
            for (int dx = -pillar; dx <= pillar; dx++) {
                for (int dz = -pillar; dz <= pillar; dz++) {
                    boolean band = y % 6 == 0;
                    m.set(cx + dx, y, cz + dz, (band ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS)
                            .defaultBlockState());
                }
            }
        }
        int inner = pillar + 1;
        int outer = pillar + 2;
        BlockState step = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState half = Blocks.DARK_OAK_SLAB.defaultBlockState();
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) < inner) {
                    continue;
                }
                double angle = Math.atan2(dz, dx);
                angle = ((angle % (2.0D * Math.PI)) + 2.0D * Math.PI) % (2.0D * Math.PI);
                int sector = Mth.clamp((int) (angle / (2.0D * Math.PI) * 16.0D), 0, 15);
                // A floor opens over a step only where the climber's head would meet it (the endless tower's: the
                // floor above opens over the last of the climb up to it); elsewhere it stays whole.
                for (int turn = 0; ; turn++) {
                    double surface = 1 + turn * 8 + sector * 0.5D;
                    if (surface > to + 1) {
                        break;
                    }
                    for (int floor : floors) {
                        if (surface > floor - 2.5D && surface <= floor + 0.5D) {
                            m.set(cx + dx, floor, cz + dz, air);
                        }
                    }
                }
                for (int turn = 0; ; turn++) {
                    double surface = 1 + turn * 8 + sector * 0.5D;
                    if (surface > to + 1) {
                        break;
                    }
                    int y = (int) Math.floor(surface);
                    if (surface == y) {
                        m.set(cx + dx, y - 1, cz + dz, step);
                    } else {
                        m.set(cx + dx, y, cz + dz, half);
                    }
                }
            }
        }
        // Light set in the pillar, a little over every floor and halfway between (its faces, if it is broad).
        for (int y = 3; y <= to + 3; y += 3) {
            if (pillar == 0) {
                m.set(cx, y, cz, Blocks.SHROOMLIGHT.defaultBlockState());
                continue;
            }
            for (Direction face : Direction.Plane.HORIZONTAL) {
                m.set(cx + face.getStepX() * pillar, y, cz + face.getStepZ() * pillar,
                        Blocks.SHROOMLIGHT.defaultBlockState());
            }
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
                    for (int y = 1; y <= 2; y++) { // below the windows
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
                if (openedTo(t, t.cx() + dx, t.cz() + dz, open) >= row[0] || blinded(t, dx, dz, row, open)) {
                    continue;
                }
                // Where the round wall is two thick (on a slant), the window goes through both: the cells of the
                // wall between it and the room are glazed too, or the window would show stone from inside.
                List<int[]> panes = new ArrayList<>();
                panes.add(new int[]{dx, dz});
                for (int[] in : new int[][]{{dx - Integer.signum(dx), dz}, {dx, dz - Integer.signum(dz)}}) {
                    if ((in[0] != dx || in[1] != dz) && within(in[0], in[1], t.r() + 0.5D)
                            && !within(in[0], in[1], t.r() - 0.5D)
                            && openedTo(t, t.cx() + in[0], t.cz() + in[1], open) < row[0]) {
                        panes.add(in);
                    }
                }
                for (int[] pane : panes) {
                    for (int y = row[0]; y <= row[1]; y++) {
                        m.set(t.cx() + pane[0], y, t.cz() + pane[1], Blocks.GLASS.defaultBlockState());
                    }
                    m.set(t.cx() + pane[0], row[0] - 1, t.cz() + pane[1],
                            Blocks.POLISHED_ANDESITE.defaultBlockState());
                }
            }
        }
        m.cone(t.cx(), t.top() + 1, t.cz(), t.coneRadius(), t.step(), t.cone(), t.edge());
        m.set(t.cx(), t.apex(), t.cz(), Blocks.LIGHTNING_ROD.defaultBlockState()); // in place of the top slab
    }

    /**
     * Whether a window of {@code t} at {@code (dx, dz)} from its middle would look straight onto one of {@code open}:
     * its wall or its cone just outside, at the window's height. A window that shows only stone is left out.
     */
    private static boolean blinded(Tower t, int dx, int dz, int[] row, Tower... open) {
        double d = Math.sqrt(dx * dx + dz * dz);
        for (int s = 1; s <= 2; s++) {
            int px = t.cx() + (int) Math.round(dx * (t.r() + s) / d);
            int pz = t.cz() + (int) Math.round(dz * (t.r() + s) / d);
            for (Tower o : open) {
                double reach = Math.max(o.r() + 1.5D, o.coneRadius() + 0.5D);
                if (within(px - o.cx(), pz - o.cz(), reach) && row[0] <= o.apex()) {
                    return true;
                }
            }
        }
        return false;
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

    /** The step out of a tower's door at {@code (x, z)}, where its battered foot would stand in the way. */
    private static void footway(Masonry m, int x, int z) {
        m.fill(x, 1, z, x, 2, z, Blocks.AIR.defaultBlockState());
        m.set(x, 0, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
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
        ground(m, -65, -9, -43, 17, 2);
        Tower kitchen = tower(-54, 11, 4, 24, new int[0], new int[][]{{3, 4}, {9, 11}, {16, 18}}, 6, false, 3);
        Tower hall = tower(-54, 0, 8, 14, new int[]{8}, new int[][]{{3, 6}, {10, 12}}, 10, true, 2);
        Tower stair = tower(-48, -6, 2, 20, new int[]{8, 14}, new int[][]{{11, 12}, {16, 18}}, 4, false, 3);
        // Lowest first: a taller tower drawn after keeps its inside clear of the lower one's roof.
        draw(m, hall, kitchen, stair);
        draw(m, stair, hall);
        draw(m, kitchen, hall);
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
        m.fill(-48, 1, -7, -48, 14, -7, ladder);
        for (int[] at : new int[][]{{-58, -5}, {-50, -5}, {-58, 5}, {-50, 5}, {-60, -2}, {-60, 2}, {-48, 2}}) {
            m.set(at[0], 9, at[1], Blocks.BARREL.defaultBlockState());
        }
        m.set(-56, 9, 6, Blocks.HAY_BLOCK.defaultBlockState());
        m.set(-52, 9, 6, Blocks.HAY_BLOCK.defaultBlockState());
        m.chandelier(-54, 14, 0, 2);
        m.chandelier(-48, 20, -6, 3);
    }

    /**
     * The workshop, three towers (the plans, sheet 05, made of towers): the workshop, low and broad under its
     * battlements, the tables of galdraria, the bench of invocation, the Artificer's counter, a loft above; the forge,
     * the tallest, its cone of copper that greens with the years, the hearth of glowing stone under its chimney, the
     * anvil, the blast furnace; and the store, low, its chests round the wall.
     */
    static void oficina(Masonry m) {
        ground(m, 41, 15, 63, 39, 2);
        Tower forge = new Tower(57, 22, 4, 21, new int[0], new int[][]{{3, 4}, {10, 12}, {16, 18}}, 6, false, 3,
                Blocks.EXPOSED_CUT_COPPER.defaultBlockState(), Blocks.EXPOSED_CUT_COPPER_SLAB.defaultBlockState());
        Tower shop = tower(50, 28, 7, 13, new int[]{8}, new int[][]{{3, 6}, {10, 11}}, 10, true, 2);
        Tower store = tower(56, 34, 3, 9, new int[0], new int[][]{{3, 4}}, 4, false, 3);
        draw(m, store, shop);
        draw(m, shop, forge, store);
        draw(m, forge, shop);
        if (!m.touches(41, 0, 15, 64, 42, 39)) {
            return;
        }
        towerEntrance(m, 43, 28, Direction.WEST, Blocks.OAK_DOOR);
        pave(m, 40, 27, 41, 29);
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
        m.fill(48, 1, 22, 48, 8, 22, ladder); // between two windows
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
        draw(m, ward, herbs, watch);
        draw(m, watch, ward);
        draw(m, herbs, ward);
        if (!m.touches(41, 0, 40, 62, 36, 59)) {
            return;
        }
        towerEntrance(m, 43, 49, Direction.WEST, Blocks.BIRCH_DOOR);
        pave(m, 40, 48, 41, 50);
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
        ground(m, 59, -9, 69, 10, 14);
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
            m.set(60, 0, cz, Blocks.POLISHED_ANDESITE.defaultBlockState());
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
            case REITORIA -> UniversityPiece.wizard(m, 0, 2, -59, Rank.RECTOR, Discipline.REITOR, 14);
            case BIBLIOTECA -> {
                UniversityPiece.wizard(m, -44, 1, -30, Rank.MAGISTER, Discipline.ARQUIVO, 12);
                UniversityPiece.wizard(m, -46, 1, -33, Rank.LECTOR, null, 30);
                UniversityPiece.villager(m, -40, -40, VillagerProfession.LIBRARIAN);
                UniversityPiece.villager(m, -48, -40, VillagerProfession.LIBRARIAN);
            }
            case PESQUISA -> {
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
                for (int[] at : STALL_HORSES) {
                    animal(m, net.minecraft.world.entity.EntityType.HORSE, 54 + at[0], 106 + at[1]);
                }
                animal(m, net.minecraft.world.entity.EntityType.COW, 66, 100);
                animal(m, net.minecraft.world.entity.EntityType.COW, 68, 112);
                animal(m, net.minecraft.world.entity.EntityType.SHEEP, 66, 108);
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
