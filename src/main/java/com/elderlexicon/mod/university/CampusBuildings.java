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

    // ------------------------------------------------------------------ the core

    /** The walled core: the Great Hall, the Library and the Observatory as they were, the short wall and its gate. */
    static void nucleo(Masonry m) {
        ground(m, -60, -70, 54, -12, 14);
        // The ways inside: from the Great Hall's portal to the gate; from the Library's door and the Observatory's.
        pave(m, -2, -27, 2, -12);
        pave(m, -31, -35, -28, -33);
        pave(m, -30, -35, -28, -14);
        pave(m, -30, -16, 0, -14);
        pave(m, 30, -44, 33, -42);
        pave(m, 30, -44, 32, -14);
        pave(m, 0, -16, 32, -14);
        UniversityPiece.greatHall(m.shifted(0, -8));
        UniversityPiece.library(m.shifted(0, -20));
        UniversityPiece.observatory(m.shifted(-6, -96));
        UniversityPiece.garden(m, 36, 50, -32, -20);
        UniversityPiece.tree(m, -45, -16);
        UniversityPiece.tree(m, 14, -20);
        wall(m, -60, -70, 54, -12);
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
            UniversityPiece.roundTower(m, at[0], at[1], 5, 17);
        }
        UniversityPiece.roundTower(m, (x0 + x1) / 2, z0, 4, 14);
        UniversityPiece.roundTower(m, x0, (z0 + z1) / 2, 4, 14);
        UniversityPiece.roundTower(m, x1, (z0 + z1) / 2, 4, 14);
        UniversityPiece.roundTower(m, gateX - 7, z1, 4, 18);
        UniversityPiece.roundTower(m, gateX + 7, z1, 4, 18);
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

    // ------------------------------------------------------------------ the courtyard

    /** The courtyard before the core's gate: paved, the fountain in the middle, lamp posts round it. */
    static void patio(Masonry m) {
        ground(m, -17, -11, 17, 11, 8);
        pave(m, -16, -10, 16, 10);
        m.ring(0, 0, 0, 5, Blocks.STONE_BRICKS.defaultBlockState(), true);
        m.ring(0, 1, 0, 5, Blocks.STONE_BRICKS.defaultBlockState(), true);
        m.ring(0, 1, 0, 4, Blocks.WATER.defaultBlockState(), true);
        m.ring(0, 2, 0, 5, Blocks.STONE_BRICK_SLAB.defaultBlockState(), false);
        m.fill(0, 1, 0, 0, 4, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.set(0, 5, 0, Blocks.LANTERN.defaultBlockState());
        for (int[] at : new int[][]{{-15, -9}, {15, -9}, {-15, 9}, {15, 9}, {-8, -9}, {8, -9}, {-8, 9}, {8, 9},
                {-15, 0}, {15, 0}}) {
            lampPost(m, at[0], at[1]);
        }
    }

    // ------------------------------------------------------------------ the teaching building

    /** A room of the teaching building: its corners, the floor it is on, what it is for, its master if it has one. */
    private record Room(int x0, int z0, int x1, int z1, int floor, boolean master, Discipline chair) {
    }

    private static final int UPPER = 5;

    /**
     * The rooms of the teaching building, ground floor and upper: the first year's in the central block, a wing for each
     * group of courses (docs/universidade-arquitetura-design.md, 7). Until each course has its Magister (the courses'
     * plan, step 5), the masters of the old disciplines sit in the rooms whose chairs they give.
     */
    private static final List<Room> ROOMS = List.of(
            // The central block: the first year.
            new Room(-10, 15, -5, 21, 0, false, Discipline.FONTES),
            new Room(5, 15, 10, 21, 0, false, Discipline.FUNCOES),
            new Room(-10, 27, -5, 35, 0, false, Discipline.VITA),
            new Room(5, 27, 10, 35, 0, false, null),
            new Room(-10, 15, -5, 21, UPPER, false, null),
            new Room(5, 15, 10, 21, UPPER, false, Discipline.NAHVA),
            new Room(-10, 27, -5, 35, UPPER, false, null),
            new Room(5, 27, 10, 35, UPPER, false, Discipline.FATOS),
            // The Wing of Force (west): Evocação, Guarda, Conversão.
            new Room(-34, 17, -24, 21, 0, false, null),
            new Room(-22, 17, -12, 21, 0, false, null),
            new Room(-34, 27, -24, 31, 0, false, null),
            new Room(-34, 17, -24, 21, UPPER, true, Discipline.FILTROS),
            new Room(-22, 17, -12, 21, UPPER, true, null),
            new Room(-34, 27, -24, 31, UPPER, true, null),
            // The Wing of the Senses (east): Visão, Vinculação, Contramagia.
            new Room(12, 17, 22, 21, 0, false, null),
            new Room(24, 17, 34, 21, 0, false, null),
            new Room(24, 27, 34, 31, 0, false, null),
            new Room(12, 17, 22, 21, UPPER, true, null),
            new Room(24, 17, 34, 21, UPPER, true, null),
            new Room(24, 27, 34, 31, UPPER, true, null),
            // The Wing of Craft (south): Ritual, Oneirologia, Artificiaria.
            new Room(-6, 37, -3, 42, 0, false, null),
            new Room(-6, 44, -3, 49, 0, false, null),
            new Room(3, 37, 6, 46, 0, false, Discipline.GALDRARIA),
            new Room(-6, 37, -3, 42, UPPER, true, null),
            new Room(-6, 44, -3, 49, UPPER, true, null),
            new Room(3, 37, 6, 46, UPPER, true, null));

    static void aulas(Masonry m) {
        ground(m, -36, 13, 36, 38, 2);
        ground(m, -8, 35, 8, 59, 2);
        CampusHouse west = new CampusHouse(-35, 16, -11, 32, 10, false, Blocks.SPRUCE_PLANKS, null, UPPER);
        CampusHouse east = new CampusHouse(11, 16, 35, 32, 10, false, Blocks.SPRUCE_PLANKS, null, UPPER);
        CampusHouse south = new CampusHouse(-7, 36, 7, 58, 10, true, Blocks.SPRUCE_PLANKS, null, UPPER);
        CampusHouse centre = new CampusHouse(-11, 14, 11, 36, 11, true, Blocks.DARK_OAK_PLANKS, null, UPPER);
        west.draw(m, centre);
        east.draw(m, centre);
        south.draw(m, centre);
        centre.draw(m, west, east, south);
        west.dormers(m, Blocks.STONE_BRICKS, -31, -23, -15);
        east.dormers(m, Blocks.STONE_BRICKS, 15, 23, 31);
        south.dormers(m, Blocks.STONE_BRICKS, 43, 51);
        tower(m);
        if (!m.touches(-36, 0, 13, 36, 12, 59)) {
            return;
        }
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int f : new int[]{0, UPPER}) {
            int top = f + UPPER - 1;
            // The central block is a block higher than the wings: its walls within reach its own ceiling.
            int middle = f == 0 ? top : centre.height() - 1;
            // Into the wings, from the central block's corridor.
            m.fill(-11, f + 1, 23, -11, f + 3, 25, air);
            m.fill(11, f + 1, 23, 11, f + 3, 25, air);
            m.fill(-1, f + 1, 36, 1, f + 3, 36, air);
            // The central block: the corridor across it, the rooms at its corners, the hall of entrance.
            m.fill(-10, f + 1, 22, 10, middle, 22, wall);
            m.fill(-10, f + 1, 26, 10, middle, 26, wall);
            m.fill(-4, f + 1, 15, -4, middle, 21, wall);
            m.fill(4, f + 1, 15, 4, middle, 21, wall);
            m.fill(-4, f + 1, 27, -4, middle, 35, wall);
            m.fill(4, f + 1, 27, 4, middle, 35, wall);
            for (int x : new int[]{-7, 7, -1, 0, 1}) {
                m.fill(x, f + 1, 22, x, f + 2, 22, air);
                m.fill(x, f + 1, 26, x, f + 2, 26, air);
            }
            // The wings of Force and of the Senses: a corridor down each, rooms both sides.
            for (int sign : new int[]{-1, 1}) {
                m.fill(sign * 34, f + 1, 22, sign * 12, top, 22, wall);
                m.fill(sign * 34, f + 1, 26, sign * 12, top, 26, wall);
                m.fill(sign * 23, f + 1, 17, sign * 23, top, 21, wall);
                m.fill(sign * 23, f + 1, 27, sign * 23, top, 31, wall);
                // The stair's room is five wide (the stair two wide, room to pass beside it), the study's five.
                m.fill(sign * 17, f + 1, 27, sign * 17, top, 31, wall);
                for (int x : new int[]{29, 15, 21}) {
                    m.fill(sign * x, f + 1, 22, sign * x, f + 2, 22, air);
                    m.fill(sign * x, f + 1, 26, sign * x, f + 2, 26, air);
                }
                m.set(sign * 28, top, 24, hanging());
                m.set(sign * 16, top, 24, hanging());
            }
            // The wing of Craft: the corridor down the middle, rooms both sides.
            m.fill(-2, f + 1, 37, -2, top, 57, wall);
            m.fill(2, f + 1, 37, 2, top, 57, wall);
            m.fill(-6, f + 1, 43, -3, top, 43, wall);
            m.fill(-6, f + 1, 50, -3, top, 50, wall);
            m.fill(3, f + 1, 47, 6, top, 47, wall);
            for (int[] door : new int[][]{{-2, 40}, {-2, 47}, {-2, 54}, {2, 41}, {2, 52}}) {
                m.fill(door[0], f + 1, door[1], door[0], f + 2, door[1], air);
            }
            m.set(0, top, 42, hanging());
            m.set(0, top, 52, hanging());
            m.chandelier(0, middle + 1, 24, middle - top + 1);
        }
        // In each wing, beside its stair: the room of study below, the wing's library above (the plans, sheet 02).
        for (int sign : new int[]{-1, 1}) {
            study(m, sign * -16, sign * -12, 27, 31, 0);
            library(m, sign * -16, sign * -12, 27, 31, UPPER);
        }
        study(m, 3, 6, 48, 57, 0);
        library(m, 3, 6, 48, 57, UPPER);
        // The stairs: the great one in the central block; one in each wing, at the end of its corridor.
        // Every stair two wide, a lantern over the way beside it on each floor.
        for (int x : new int[]{-3, -2}) {
            Hall.stair(m, x, 28, Direction.SOUTH, 0, UPPER, Blocks.DARK_OAK_STAIRS);
        }
        for (int x : new int[]{-21, -20, 20, 21}) {
            Hall.stair(m, x, 31, Direction.NORTH, 0, UPPER, Blocks.SPRUCE_STAIRS);
        }
        for (int x : new int[]{-6, -5}) {
            Hall.stair(m, x, 57, Direction.NORTH, 0, UPPER, Blocks.SPRUCE_STAIRS);
        }
        for (int[] at : new int[][]{{-18, 29}, {18, 29}, {-3, 52}}) {
            m.chandelier(at[0], UPPER, at[1], 1);
            m.chandelier(at[0], 10, at[1], 1);
        }
        for (Room room : ROOMS) {
            furnish(m, room);
        }
    }

    /** A classroom: the master's lectern at the front, benches in rows, shelves at the back; a master's room: a desk. */
    private static void furnish(Masonry m, Room room) {
        int y = room.floor() + 1;
        int mx = (room.x0() + room.x1()) / 2;
        boolean deep = room.z1() - room.z0() >= room.x1() - room.x0();
        RuneGuides.Family family = room.chair() == null ? null : room.chair().family().orElse(null);
        int index = Math.floorMod(room.x0() * 31 + room.z0() * 17 + room.floor(), 40);
        java.util.function.Supplier<net.minecraft.world.item.ItemStack> book = family != null
                ? () -> RuneGuides.familyGuide(family) : () -> UniversityPiece.runeGuide(index);
        if (room.master()) {
            shelves(m, room, room.x0(), y, room.z0(), room.x1(), y + 2, room.z0());
            m.set(mx, y, room.z0() + 2, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            m.set(mx - 1, y, room.z0() + 2, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST));
            m.lectern(room.x1(), y, room.z1(), Direction.WEST, book);
            return;
        }
        if (deep) {
            // An aisle down the room from its door (the door's column left free of benches), the lectern beside it.
            int aisle = Integer.MIN_VALUE;
            for (int x = room.x0(); x <= room.x1(); x++) {
                if (door(x, room.z0() - 1) || door(x, room.z1() + 1)) {
                    aisle = x;
                }
            }
            int lectern = aisle == mx ? mx + (mx < 0 ? -1 : 1) : mx;
            m.lectern(lectern, y, room.z0() + 1, Direction.SOUTH, book);
            for (int z = room.z0() + 3; z <= room.z1() - 1; z += 2) {
                for (int x = room.x0() + 1; x <= room.x1() - 1; x++) {
                    if (x != aisle) {
                        m.set(x, y, z, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
                    }
                }
            }
            shelves(m, room, room.x0(), y, room.z1(), room.x1(), y + 2, room.z1());
        } else {
            m.lectern(room.x0() + 1, y, (room.z0() + room.z1()) / 2, Direction.EAST, book);
            // Benches in rows across the room, an aisle along the door's side and another along the shelves.
            for (int x = room.x0() + 3; x <= room.x1() - 2; x += 2) {
                for (int z = room.z0(); z <= room.z1(); z++) {
                    if (z != (room.z0() + room.z1()) / 2 + (room.z1() > 25 ? -2 : 2)) {
                        m.set(x, y, z, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
                    }
                }
            }
            shelves(m, room, room.x1(), y, room.z0(), room.x1(), y + 2, room.z1());
        }
    }

    /**
     * Whether the teaching building has a door in its inner walls at {@code (x, z)}, on both floors: the openings cut
     * in {@link #aulas}. Asked by what furnishes the rooms, so nothing is put before a door; not read from the world,
     * which a piece sees only one chunk of at a time.
     */
    private static boolean door(int x, int z) {
        int ax = Math.abs(x);
        if (z == 22 || z == 26) {
            return ax <= 1 || ax == 7 || ax == 15 || ax == 21 || ax == 29;
        }
        if (x == -2) {
            return z == 40 || z == 47 || z == 54;
        }
        return x == 2 && (z == 41 || z == 52);
    }

    /**
     * Shelves over a box at a room's wall, leaving free the doorway and a column either side of it, so the door opens
     * into the room and not into a slot between shelves.
     */
    private static void shelves(Masonry m, Room room, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean doorway = false;
                for (int d = -1; d <= 1; d++) {
                    doorway |= (z == room.z0() && door(x + d, z - 1)) || (z == room.z1() && door(x + d, z + 1))
                            || (x == room.x0() && door(x - 1, z + d)) || (x == room.x1() && door(x + 1, z + d));
                }
                if (!doorway) {
                    m.fill(x, y0, z, x, y1, z, Blocks.BOOKSHELF.defaultBlockState());
                }
            }
        }
    }

    /** A room of study: tables with their chairs, the shelves along the back wall. */
    private static void study(Masonry m, int xa, int xb, int z0, int z1, int floor) {
        int x0 = Math.min(xa, xb);
        int x1 = Math.max(xa, xb);
        int y = floor + 1;
        m.fill(x0, y, z1, x1, y + 1, z1, Blocks.BOOKSHELF.defaultBlockState());
        for (int z = z0 + 1; z <= z1 - 2; z += 3) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                m.set(x, y, z + 1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                m.set(x, y, z, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            }
            m.set(x0 + 1, y + 1, z + 1, Blocks.CANDLE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        }
    }

    /** A wing's library: shelves round its walls to the ceiling, a lectern with a guide in the middle. */
    private static void library(Masonry m, int xa, int xb, int z0, int z1, int floor) {
        int x0 = Math.min(xa, xb);
        int x1 = Math.max(xa, xb);
        int y = floor + 1;
        m.fill(x0, y, z1, x1, y + 2, z1, Blocks.BOOKSHELF.defaultBlockState());
        for (int z = z0 + 1; z < z1; z++) {
            for (int x : new int[]{x0, x1}) {
                boolean doorway = door(x == x0 ? x - 1 : x + 1, z);
                if (!doorway && m.get(x, y, z).isAir() && m.get(x, y + 1, z).isAir()) {
                    m.fill(x, y, z, x, y + 2, z, Blocks.BOOKSHELF.defaultBlockState());
                }
            }
        }
        int index = Math.floorMod(x0 * 13 + z0 * 7, 40);
        m.lectern((x0 + x1) / 2, y, (z0 + z1) / 2 + 1, Direction.NORTH, () -> UniversityPiece.runeGuide(index));
    }

    /**
     * The tower over the hall of entrance (the plans, sheet 02: "vazio sobre o hall"): the hall open to the ceiling
     * with a gallery behind it, the pointed portal with the door set back in it, a tall window over it, a round window
     * on each free face, the belfry with its bell, the cornice with pinnacles, and the spire.
     */
    private static void tower(Masonry m) {
        int x0 = -4;
        int x1 = 4;
        int z0 = 14;
        int z1 = 22;
        int top = 26;
        if (!m.touches(x0 - 1, 0, z0 - 1, x1 + 1, top + 17, z1 + 1)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        // Nothing of the roof inside the tower or before its face.
        m.fill(x0 + 1, 12, z0 + 1, x1 - 1, top, z1 - 1, air);
        m.fill(x0, 11, z0 - 1, x1, 24, z0 - 1, air);
        // The hall two storeys high, the gallery over its back.
        m.fill(x0 + 1, UPPER, z0 + 1, x1 - 1, UPPER, z1 - 3, air);
        m.fill(x0 + 1, UPPER - 1, z0 + 1, x1 - 1, UPPER - 1, z1 - 3, air);
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            m.set(x, UPPER + 1, z1 - 2, Blocks.DARK_OAK_FENCE.defaultBlockState());
        }
        m.chandelier(0, 11, 18, 4);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) {
                    continue;
                }
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                for (int y = 12; y <= top; y++) {
                    m.set(x, y, z, corner ? Blocks.POLISHED_ANDESITE.defaultBlockState() : m.stone(x, y, z));
                }
            }
        }
        // Its corners down to the ground, and buttresses before them.
        for (int x : new int[]{x0, x1}) {
            m.fill(x, 1, z0, x, 11, z0, Blocks.POLISHED_ANDESITE.defaultBlockState());
            m.fill(x, 0, z0 - 1, x, 12, z0 - 1, Blocks.STONE_BRICKS.defaultBlockState());
            m.set(x, 13, z0 - 1, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
        }
        // The tall window over the portal, lighting the hall.
        for (int y = 6; y <= 9; y++) {
            m.set(0, y, z0, Blocks.GLASS_PANE.defaultBlockState());
        }
        m.set(0, 5, z0 - 1, CampusHouse.slab(Blocks.STONE_BRICK_SLAB, SlabType.TOP));
        m.set(0, 10, z0 - 1, CampusHouse.slab(Blocks.STONE_BRICK_SLAB, SlabType.BOTTOM));
        // A round window on each face the roof leaves free, the house's banners under the front one.
        for (int s = -1; s <= 1; s++) {
            for (int y = 14; y <= 16; y++) {
                BlockState state = s == 0 || y == 15 ? Blocks.GLASS_PANE.defaultBlockState()
                        : Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                m.set(s, y, z0, state);
                m.set(x0, y, 18 + s, state);
                m.set(x1, y, 18 + s, state);
            }
        }
        for (int x : new int[]{-2, 2}) {
            m.set(x, 17, z0 - 1, Blocks.PURPLE_WALL_BANNER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.NORTH));
        }
        // The belfry: a floor of whole planks for the bell to stand on, a lantern in each corner (it is open to the
        // night), an arched opening on each face over a little balustrade.
        m.fill(x0 + 1, 20, z0 + 1, x1 - 1, 20, z1 - 1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        for (int x : new int[]{x0 + 1, x1 - 1}) {
            for (int z : new int[]{z0 + 1, z1 - 1}) {
                m.set(x, 21, z, Blocks.LANTERN.defaultBlockState());
            }
        }
        m.set(0, 21, 18, Blocks.BELL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BellBlock.FACING, Direction.NORTH)
                .setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT,
                        net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR));
        for (int s = -1; s <= 1; s++) {
            for (int y = 21; y <= 23; y++) {
                m.set(s, y, z0, air);
                m.set(s, y, z1, air);
                m.set(x0, y, 18 + s, air);
                m.set(x1, y, 18 + s, air);
            }
            m.set(s, 20, z0, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(s, 20, z1, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(x0, 20, 18 + s, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(x1, 20, 18 + s, Blocks.STONE_BRICK_WALL.defaultBlockState());
        }
        for (int z : new int[]{z0, z1}) {
            m.set(-1, 23, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
            m.set(1, 23, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
        }
        for (int x : new int[]{x0, x1}) {
            m.set(x, 23, 17, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
            m.set(x, 23, 19, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
        }
        m.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, Blocks.DARK_OAK_SLAB.defaultBlockState());
        // The cornice round the top, pinnacles at its corners.
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean westEast = x == x0 - 1 || x == x1 + 1;
                boolean northSouth = z == z0 - 1 || z == z1 + 1;
                if (westEast && northSouth) {
                    m.set(x, top, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                    m.set(x, top + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    m.set(x, top + 2, z, Blocks.LANTERN.defaultBlockState());
                } else if (westEast || northSouth) {
                    Direction toward = x == x0 - 1 ? Direction.EAST : x == x1 + 1 ? Direction.WEST
                            : z == z0 - 1 ? Direction.SOUTH : Direction.NORTH;
                    m.set(x, top, z, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, toward));
                }
            }
        }
        // The spire, steep: three rows to each step in, the last of them stairs.
        int cx = 0;
        int cz = 18;
        for (int i = 0; i <= 4; i++) {
            int r = 4 - i;
            for (int s = 0; s <= 2; s++) {
                int y = top + 1 + i * 3 + s;
                for (int x = cx - r; x <= cx + r; x++) {
                    for (int z = cz - r; z <= cz + r; z++) {
                        boolean edge = Math.abs(x - cx) == r || Math.abs(z - cz) == r;
                        if (!edge) {
                            continue;
                        }
                        boolean corner = Math.abs(x - cx) == r && Math.abs(z - cz) == r;
                        Direction toward = x == cx - r ? Direction.EAST : x == cx + r ? Direction.WEST
                                : z == cz - r ? Direction.SOUTH : Direction.NORTH;
                        m.set(x, y, z, s < 2 || corner || r == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                                : Masonry.stairs(CampusHouse.ROOF, toward));
                    }
                }
            }
        }
        m.set(cx, top + 16, cz, Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
        m.set(cx, top + 17, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
        // The portal: a pointed arch, the door set back in it, lanterns on posts either side.
        m.fill(-1, 1, z0, 1, 3, z0, air);
        m.fill(-1, 1, z0 - 1, 1, 3, z0 - 1, air);
        m.set(0, 6, z0 - 1, air);
        m.set(-1, 3, z0,Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        m.set(1, 3, z0, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
        m.set(0, 4, z0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.fill(-1, 0, z0, 1, 0, z0, Blocks.POLISHED_ANDESITE.defaultBlockState());
        m.fill(-1, 1, z0 + 1, 1, 3, z0 + 1, Blocks.STONE_BRICKS.defaultBlockState());
        m.door(0, z0 + 1, Direction.NORTH, Blocks.DARK_OAK_DOOR, false);
        for (int x : new int[]{-2, 2}) {
            m.set(x, 1, z0 - 1, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(x, 2, z0 - 1, Blocks.STONE_BRICK_WALL.defaultBlockState());
            m.set(x, 3, z0 - 1, Blocks.LANTERN.defaultBlockState());
        }
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
        ground(m, -63, 28, -43, 48, 2);
        if (!m.touches(-64, 0, 27, -42, h + 3 * r + 3, 49)) {
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
                    m.set(x, 2, z, Blocks.COBBLESTONE.defaultBlockState());
                    m.set(x, 3, z, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, out.getOpposite()));
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
        // The windows all round, framed in polished stone (a sill would stick out askew from a round wall).
        int[][] windows = {{-7, -4}, {-7, 4}, {-4, -7}, {-4, 7}, {4, -7}, {4, 7}, {7, -4}, {7, 4}};
        for (int i = 0; i < DORM_LEVELS; i++) {
            int f = i * DORM_STOREY;
            BlockState glass = (i == DORM_LEVELS - 1 ? Blocks.PURPLE_STAINED_GLASS_PANE : Blocks.GLASS_PANE)
                    .defaultBlockState();
            for (int[] w : windows) {
                dormWindow(m, w[0], w[1], f, glass);
            }
            if (i > 0) {
                dormWindow(m, r, 0, f, glass);
            }
        }
        // The cone, steep, four lit windows in it.
        m.cone(DORM_X, h + 1, DORM_Z, r - 1, 3, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
        m.set(DORM_X, h + 1 + 3 * r, DORM_Z, Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
        m.set(DORM_X, h + 2 + 3 * r, DORM_Z, Blocks.LIGHTNING_ROD.defaultBlockState());
        for (int[] c : new int[][]{{r - 1, 0}, {-(r - 1), 0}, {0, r - 1}, {0, -(r - 1)}}) {
            m.set(DORM_X + c[0], h + 2, DORM_Z + c[1], Blocks.PURPLE_STAINED_GLASS_PANE.defaultBlockState());
            m.set(DORM_X + c[0], h + 3, DORM_Z + c[1], Blocks.PURPLE_STAINED_GLASS_PANE.defaultBlockState());
            m.set(DORM_X + c[0] - Integer.signum(c[0]), h + 2, DORM_Z + c[1] - Integer.signum(c[1]),
                    Blocks.SHROOMLIGHT.defaultBlockState());
        }
        for (int i = 0; i < DORM_LEVELS; i++) {
            dormLevel(m, i);
        }
        // The common room's hearth, its hood, the flue through the wall into the chimney up the west face.
        int hx = DORM_X - 7;
        m.set(hx, 1, DORM_Z, Blocks.CAMPFIRE.defaultBlockState());
        for (int dz : new int[]{-1, 1}) {
            m.set(hx, 1, DORM_Z + dz, bricks);
            m.set(hx, 2, DORM_Z + dz, bricks);
        }
        m.fill(hx, 3, DORM_Z - 1, hx, 4, DORM_Z + 1, bricks);
        m.set(DORM_X - r, 3, DORM_Z, bricks);
        m.fill(DORM_X - r - 1, 1, DORM_Z - 1, DORM_X - r - 1, h + 9, DORM_Z + 1, bricks);
        for (int dz = -1; dz <= 1; dz++) {
            m.set(DORM_X - r - 2, 1, DORM_Z + dz, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
            m.set(DORM_X - r - 1, h + 10, DORM_Z + dz, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        }
        m.set(DORM_X - r - 1, h + 10, DORM_Z, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, false));
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
     * A level of the dormitory tower. The ground is the common room (west) and the hall (east): on the dividing wall a
     * row of scrolls, one to each floor ({@code pN_code}, whose text {@code ego aN_code ubis vocant} sends the one who
     * reads it up), a sign over each saying what to read, and on the floor by the door the scroll one arrives on when
     * coming down ({@code a0_code}). Above the ground each level is a flat, for there is no stair to climb: a mage reads
     * their way up. On its floor lies the scroll one arrives on ({@code aN_code}), whose text sends them back down;
     * to each student a bed by the wall, a chest, a desk with its chair and candle, a barrel, a shelf; a table, shelves
     * by the east window, a carpet, lanterns. Up the south wall, through a hatch in every floor, the service ladder.
     */
    private static void dormLevel(Masonry m, int i) {
        int f = i * DORM_STOREY;
        int x = DORM_X;
        int z = DORM_Z;
        String code = m.houseCode();
        // The service ladder, up the south wall; a hatch where it goes through a floor.
        BlockState ladder = Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH);
        for (int y = f + 1; y < f + DORM_STOREY; y++) {
            m.set(x + 1, y, z + 7, ladder);
        }
        if (i > 0) {
            m.set(x + 1, f, z + 7, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF,
                            net.minecraft.world.level.block.state.properties.Half.TOP));
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
            // The scrolls of the floors on the dividing wall, a sign over each; the scroll of arrival by the door.
            for (int n = 1; n <= Porter.FLOORS; n++) {
                int dz = n - 4;
                placedScroll(m, x, 3, z + dz, Direction.EAST, "ego " + Porter.arrival(code, n) + " ubis vocant",
                        Porter.scroll(code, n));
                sign(m, x + 1, 4, z + dz, Direction.EAST, n + "º andar" + (n == Porter.FLOORS ? ": sonho" : ""),
                        Porter.scroll(code, n), "surgit");
            }
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

    /** A window of the dormitory tower at {@code (dx, dz)} of its ring, on the level at {@code f}. */
    private static void dormWindow(Masonry m, int dx, int dz, int f, BlockState glass) {
        int x = DORM_X + dx;
        int z = DORM_Z + dz;
        for (int y = f + 2; y <= f + 4; y++) {
            m.set(x, y, z, glass);
        }
        m.set(x, f + 1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
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

    // ------------------------------------------------------------------ the houses of use

    /**
     * The refectory (the plans, sheet 05): the hall of the long tables to the north, the kitchen along the south wall
     * with its two hearths under their chimneys (what the lesson of vertere uses: to cook, to eat), the pantry beside
     * it. Stone below, timbered above.
     */
    static void refeitorio(Masonry m) {
        ground(m, -65, -5, -43, 13, 2);
        CampusHouse house = new CampusHouse(-64, -4, -44, 12, 9, true, Blocks.OAK_PLANKS,
                new CampusHouse.Timber(5, Blocks.DARK_OAK_LOG, Blocks.CALCITE));
        house.draw(m);
        house.dormers(m, Blocks.OAK_PLANKS, 0, 8);
        if (!m.touches(-65, 0, -5, -43, 23, 13)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        CampusHouse.entrance(m, -44, 0, Direction.EAST, Blocks.OAK_DOOR, false);
        for (int z : new int[]{-1, 3}) {
            m.fill(-60, 1, z, -48, 1, z, Blocks.OAK_PLANKS.defaultBlockState());
            for (int x = -60; x <= -48; x += 2) {
                m.set(x, 1, z - 1, Masonry.stairs(Blocks.OAK_STAIRS, Direction.NORTH));
                m.set(x, 1, z + 1, Masonry.stairs(Blocks.OAK_STAIRS, Direction.SOUTH));
            }
            m.set(-54, 2, z, Blocks.CANDLE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        }
        // The wall between the hall and the kitchen, a way through to each; the pantry walled off the kitchen.
        m.fill(-63, 1, 6, -45, 8, 6, Blocks.STONE_BRICKS.defaultBlockState());
        m.fill(-58, 1, 6, -58, 2, 6, air);
        m.fill(-48, 1, 6, -48, 2, 6, air);
        m.fill(-52, 1, 7, -52, 8, 11, Blocks.STONE_BRICKS.defaultBlockState());
        m.fill(-52, 1, 9, -52, 2, 9, air);
        m.chandelier(-55, 9, 9, 5);
        m.chandelier(-48, 9, 9, 5);
        // The kitchen: two hearths in breasts of brick that rise into the chimneys, the stoves between them.
        for (int x : new int[]{-61, -57}) {
            m.set(x, 1, 11, Blocks.CAMPFIRE.defaultBlockState());
            m.fill(x, 3, 11, x, 8, 11, Blocks.BRICKS.defaultBlockState());
            m.set(x - 1, 2, 11, Blocks.BRICKS.defaultBlockState());
            m.set(x + 1, 2, 11, Blocks.BRICKS.defaultBlockState());
            CampusHouse.chimney(m, x, 11, 9, 21);
        }
        m.set(-63, 1, 11, Blocks.SMOKER.defaultBlockState().setValue(
                net.minecraft.world.level.block.SmokerBlock.FACING, Direction.NORTH));
        m.set(-59, 1, 11, Blocks.FURNACE.defaultBlockState().setValue(
                net.minecraft.world.level.block.FurnaceBlock.FACING, Direction.NORTH));
        m.set(-55, 1, 11, Blocks.SMOKER.defaultBlockState().setValue(
                net.minecraft.world.level.block.SmokerBlock.FACING, Direction.NORTH));
        m.set(-54, 1, 11, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        m.set(-63, 1, 8, Blocks.CRAFTING_TABLE.defaultBlockState());
        m.set(-53, 1, 7, Blocks.BARREL.defaultBlockState());
        // The pantry: barrels, hay, a composter.
        for (int x = -51; x <= -46; x++) {
            m.set(x, 1, 11, Blocks.BARREL.defaultBlockState());
        }
        m.set(-46, 1, 7, Blocks.HAY_BLOCK.defaultBlockState());
        m.set(-46, 1, 8, Blocks.HAY_BLOCK.defaultBlockState());
        m.set(-46, 2, 7, Blocks.HAY_BLOCK.defaultBlockState());
        m.set(-51, 1, 7, Blocks.COMPOSTER.defaultBlockState());
    }

    /**
     * The workshop (the plans, sheet 05): along the north the tables of galdraria and the forge under its great
     * chimney; along the south the bench of invocation, the Artificer's counter and the store; a work yard under a
     * lean-to outside the south wall. Stone below, timbered in spruce and mud above.
     */
    static void oficina(Masonry m) {
        ground(m, 41, 19, 63, 39, 2);
        CampusHouse house = new CampusHouse(42, 20, 62, 36, 8, false, Blocks.STONE_BRICKS,
                new CampusHouse.Timber(5, Blocks.SPRUCE_LOG, Blocks.PACKED_MUD));
        house.draw(m);
        if (!m.touches(41, 0, 19, 63, 21, 39)) {
            return;
        }
        CampusHouse.entrance(m, 42, 28, Direction.WEST, Blocks.OAK_DOOR, true);
        // The tables of galdraria.
        Block table = Galdraria.TABLE.get();
        for (int x : new int[]{45, 49}) {
            for (int z : new int[]{22, 25}) {
                m.set(x, 1, z, table.defaultBlockState());
            }
        }
        // The forge: a hearth of glowing stone in a breast of brick that rises into the chimney; anvils before it.
        BlockState brick = Blocks.BRICKS.defaultBlockState();
        m.fill(56, 1, 21, 59, 7, 21, brick);
        m.fill(56, 1, 22, 59, 1, 22, brick);
        m.set(57, 1, 22, Blocks.MAGMA_BLOCK.defaultBlockState());
        m.set(58, 1, 22, Blocks.MAGMA_BLOCK.defaultBlockState());
        m.fill(56, 3, 22, 59, 7, 22, brick);
        m.fill(57, 8, 21, 58, 19, 22, brick);
        m.set(57, 20, 21, Blocks.CAMPFIRE.defaultBlockState());
        m.set(58, 20, 22, Blocks.CAMPFIRE.defaultBlockState());
        m.set(55, 1, 24, Blocks.ANVIL.defaultBlockState());
        m.set(58, 1, 25, Blocks.ANVIL.defaultBlockState());
        m.set(61, 1, 22, Blocks.BLAST_FURNACE.defaultBlockState().setValue(
                net.minecraft.world.level.block.BlastFurnaceBlock.FACING, Direction.SOUTH));
        m.set(61, 1, 24, Blocks.SMITHING_TABLE.defaultBlockState());
        m.set(53, 1, 22, Blocks.GRINDSTONE.defaultBlockState());
        // The bench of invocation, the Artificer's counter, the store behind its wall.
        m.set(46, 1, 33, com.elderlexicon.mod.invocation.Invocations.BENCH.get().defaultBlockState());
        m.set(44, 1, 34, Blocks.BOOKSHELF.defaultBlockState());
        m.set(48, 1, 34, Blocks.BOOKSHELF.defaultBlockState());
        m.fill(50, 1, 31, 54, 1, 31, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(
                net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
        m.set(52, 2, 31, Blocks.LANTERN.defaultBlockState());
        m.set(50, 1, 34, Blocks.CRAFTING_TABLE.defaultBlockState());
        m.set(54, 1, 34, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        m.fill(56, 1, 29, 56, 4, 35, Blocks.SPRUCE_PLANKS.defaultBlockState());
        m.fill(56, 1, 30, 56, 2, 30, Blocks.AIR.defaultBlockState());
        for (int z = 31; z <= 35; z++) {
            m.chest(61, 1, z, Direction.WEST, "chests/wizard_house");
        }
        m.set(58, 1, 35, Blocks.BARREL.defaultBlockState());
        m.set(59, 1, 35, Blocks.BARREL.defaultBlockState());
        // The work yard: a lean-to on posts, the grindstone, the stonecutter, the wood.
        for (int x = 46; x <= 58; x++) {
            m.set(x, 4, 37, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            m.set(x, 3, 38, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        }
        for (int x : new int[]{46, 52, 58}) {
            m.fill(x, 1, 38, x, 2, 38, Blocks.SPRUCE_LOG.defaultBlockState());
        }
        m.set(48, 1, 37, Blocks.GRINDSTONE.defaultBlockState());
        m.set(50, 1, 37, Blocks.BARREL.defaultBlockState());
        m.set(54, 1, 37, Blocks.STONECUTTER.defaultBlockState());
        m.fill(55, 1, 37, 57, 1, 37, Blocks.OAK_LOG.defaultBlockState().setValue(
                net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
    }

    /** The infirmary: beds along the east wall, the curers' stands; timbered in birch, its herb bed by the door. */
    static void enfermaria(Masonry m) {
        ground(m, 41, 41, 59, 57, 2);
        CampusHouse house = new CampusHouse(42, 42, 58, 56, 8, false, Blocks.BIRCH_PLANKS,
                new CampusHouse.Timber(5, Blocks.STRIPPED_BIRCH_LOG, Blocks.CALCITE));
        house.draw(m);
        house.dormers(m, Blocks.BIRCH_PLANKS, 50);
        if (!m.touches(41, 0, 41, 59, 20, 57)) {
            return;
        }
        CampusHouse.entrance(m, 42, 49, Direction.WEST, Blocks.BIRCH_DOOR, false);
        Block[] herbs = {Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER};
        for (int z = 44; z <= 47; z++) {
            m.set(41, 1, z, herbs[z - 44].defaultBlockState());
        }
        // A bed faces from its foot to its head: the heads here are to the east, by the wall.
        BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        for (int z = 44; z <= 54; z += 2) {
            if (Math.abs(z - 49) <= 1) {
                continue;
            }
            m.set(56, 1, z, bed.setValue(BedBlock.PART, BedPart.HEAD));
            m.set(55, 1, z, bed.setValue(BedBlock.PART, BedPart.FOOT));
        }
        m.set(45, 1, 44, Blocks.BREWING_STAND.defaultBlockState());
        m.set(46, 1, 44, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        m.set(45, 1, 54, Blocks.BREWING_STAND.defaultBlockState());
        m.set(46, 1, 54, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
    }

    /**
     * The gatehouse where the avenue leaves the campus for the village: two square towers with slits and battlements,
     * the pointed arch over the road between them, the walk across its top, the house's banners on its outer face.
     */
    static void arco(Masonry m) {
        ground(m, 59, -9, 69, 9, 14);
        if (!m.touches(59, 0, -9, 69, 13, 9)) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        m.fill(60, 0, -3, 68, 0, 3, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int[] side : new int[][]{{-7, -4}, {4, 7}}) {
            int za = side[0];
            int zb = side[1];
            int mid = (za + zb) / 2;
            for (int x = 61; x <= 67; x++) {
                for (int z = za; z <= zb; z++) {
                    boolean edge = x == 61 || x == 67 || z == za || z == zb;
                    boolean corner = (x == 61 || x == 67) && (z == za || z == zb);
                    for (int y = 0; y <= 11; y++) {
                        if (!edge) {
                            m.set(x, y, z, y == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : air);
                        } else {
                            m.set(x, y, z, y <= 1 ? CampusHouse.plinth(m, x, y, z)
                                    : corner ? Blocks.POLISHED_ANDESITE.defaultBlockState() : m.stone(x, y, z));
                        }
                    }
                }
            }
            for (int x : new int[]{61, 67}) {
                m.set(x, 4, mid, Blocks.IRON_BARS.defaultBlockState());
                m.set(x, 8, mid, Blocks.IRON_BARS.defaultBlockState());
            }
            m.fill(62, 6, za + 1, 66, 6, zb - 1, Blocks.SPRUCE_SLAB.defaultBlockState());
            m.set(64, 5, mid, hanging());
            // The cornice and the battlements.
            for (int x = 60; x <= 68; x++) {
                for (int z = za - 1; z <= zb + 1; z++) {
                    boolean westEast = x == 60 || x == 68;
                    boolean northSouth = z == za - 1 || z == zb + 1;
                    if (!westEast && !northSouth) {
                        continue;
                    }
                    Direction toward = x == 60 ? Direction.EAST : x == 68 ? Direction.WEST
                            : z == za - 1 ? Direction.SOUTH : Direction.NORTH;
                    m.set(x, 11, z, westEast && northSouth ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                            : Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, toward));
                    if (Math.floorMod(x + z, 2) == 0) {
                        m.set(x, 12, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    }
                }
            }
            m.fill(61, 11, za, 67, 11, zb, Blocks.STONE_BRICK_SLAB.defaultBlockState());
            // A door on the campus's side, a ladder up the far wall through the floor to a hatch onto the top.
            m.door(61, mid, Direction.WEST, Blocks.SPRUCE_DOOR, false);
            BlockState ladder = Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST);
            for (int y = 1; y <= 10; y++) {
                m.set(66, y, mid, ladder);
            }
            m.set(66, 11, mid, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF,
                            net.minecraft.world.level.block.state.properties.Half.TOP));
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
        m.fill(63, 9, -3, 65, 9, 3, Blocks.STONE_BRICKS.defaultBlockState());
        m.fill(63, 10, -3, 65, 10, 3, Blocks.SPRUCE_SLAB.defaultBlockState());
        for (int z = -3; z <= 3; z++) {
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

    // ------------------------------------------------------------------ who lives there

    /** The Auditor who keeps the dormitory tower, in its hall: it sends up whoever cannot read their way. */
    private static void porter(Masonry m) {
        net.minecraft.core.BlockPos at = m.at(DORM_X + 4, 1, DORM_Z - 3);
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

    static void people(Masonry m, CampusPiece.Kind kind) {
        switch (kind) {
            case NUCLEO -> {
                UniversityPiece.wizard(m.shifted(0, -8), 0, 2, -51, Rank.RECTOR, Discipline.REITOR, 18);
                Masonry library = m.shifted(0, -20);
                UniversityPiece.wizard(library, -44, 1, -10, Rank.MAGISTER, Discipline.ARQUIVO, 14);
                UniversityPiece.wizard(library, -46, 1, -12, Rank.LECTOR, null, 40);
                UniversityPiece.villager(library, -39, -6, VillagerProfession.LIBRARIAN);
                UniversityPiece.villager(library, -39, -14, VillagerProfession.LIBRARIAN);
            }
            case PATIO -> {
                UniversityPiece.wizard(m, 9, 1, 6, Rank.AUDITOR, null, 60);
                UniversityPiece.wizard(m, -9, 1, 6, Rank.AUDITOR, null, 60);
                UniversityPiece.wizard(m, 8, 1, -6, Rank.SCRIBA, null, 60);
                UniversityPiece.wizard(m, -8, 1, -6, Rank.SCRIBA, null, 60);
            }
            case AULAS -> {
                for (Room room : ROOMS) {
                    if (room.chair() != null) {
                        UniversityPiece.wizard(m, (room.x0() + room.x1()) / 2, room.floor() + 1,
                                (room.z0() + room.z1()) / 2 + 1, Rank.MAGISTER, room.chair(), 5);
                    }
                }
            }
            case DORMITORIOS -> porter(m);
            case ENFERMARIA -> {
                UniversityPiece.villager(m, 48, 47, VillagerProfession.CLERIC);
                UniversityPiece.villager(m, 48, 51, VillagerProfession.CLERIC);
            }
            default -> {
            }
        }
    }
}
