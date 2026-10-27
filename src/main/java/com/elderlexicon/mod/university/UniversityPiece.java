package com.elderlexicon.mod.university;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.galdraria.Galdraria;
import com.elderlexicon.mod.library.RuneGuides;
import com.elderlexicon.mod.tower.Towers;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A university (docs/mago-design.md, 4 and 5g), one of the few of the world: a walled town of learning, drawn block by
 * block. The ground is shaped to it as the world is made (the piece's box is the ground the terrain is beared to), so
 * it sits in the land as if it had always been there; a road out of its gate ({@link UniversityRoadPiece}) keeps the
 * way in clear of hills.
 * <ul>
 *   <li><b>The wall</b>, with walkways, battlements and lights, round towers at the corners and halfway, and the
 *       gatehouse to the south.</li>
 *   <li><b>The Great Hall</b> (north): a nave of columns under a steep roof, two towers with spires before its front,
 *       a rose window, a spire over the middle; the Rector's seat on a dais and the council's table.</li>
 *   <li><b>The Library</b> (west), the Archive: tall shelves, a gallery round the walls, the reading room, and the
 *       closed room behind an iron door.</li>
 *   <li><b>The Research Wing</b> (east): two storeys of rooms off a corridor, the seven chairs' on the ground floor.</li>
 *   <li><b>The Observatory</b> (south-east): a round tower with a glass dome.</li>
 *   <li><b>The dormitory</b>, <b>the refectory</b>, <b>the workshop</b> and <b>the infirmary</b> (south), the main
 *       courtyard with its fountain, gardens, lawns with trees and lamp posts.</li>
 * </ul>
 * Who lives there is born in it: the Rector, the eight masters, students, the Archive's librarians, the infirmary's
 * clerics. Every place where something could be born of the dark is lit, or of slabs.
 */
public class UniversityPiece extends StructurePiece {

    /** Where the wall stands from the middle, and how far the piece reaches (the corner towers). */
    static final int WALL = 62;
    static final int REACH = 68;
    /** How high the piece reaches: the Great Hall's spire. */
    static final int TOP = 72;
    /** How high a test looks for dark places to be born in: the highest floor (the observatory's top room). */
    public static final int TOP_FOR_TESTS = TOP;
    /** How deep the ground is mended under the grounds, where the terrain left a gap. */
    private static final int MEND = 4;
    /** The edge of a slate cone, where its layer is left open to the sky. */
    private static final BlockState SLATE_EDGE = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
    /** How far apart the lamp posts stand on the lawns. */
    private static final int LAMPS = 7;

    private final int cx;
    private final int y0;
    private final int cz;

    public UniversityPiece(BlockPos center) {
        super(Towers.UNIVERSITY_PIECE.get(), 0, new BoundingBox(center.getX() - REACH, center.getY(),
                center.getZ() - REACH, center.getX() + REACH, center.getY() + TOP, center.getZ() + REACH));
        this.cx = center.getX();
        this.y0 = center.getY();
        this.cz = center.getZ();
    }

    public UniversityPiece(CompoundTag tag) {
        super(Towers.UNIVERSITY_PIECE.get(), tag);
        this.cx = tag.getInt("X");
        this.y0 = tag.getInt("Y");
        this.cz = tag.getInt("Z");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("X", cx);
        tag.putInt("Y", y0);
        tag.putInt("Z", cz);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        Masonry m = new Masonry(level, chunk, cx, y0, cz);
        grounds(m);
        walls(m);
        greatHall(m);
        library(m);
        researchWing(m);
        observatory(m);
        dormitory(m);
        refectory(m);
        workshop(m);
        infirmary(m);
        lampPosts(m);
        m.join();
        people(m);
    }

    // ------------------------------------------------------------------ the grounds

    /** The ground mended and cleared, lawns, the paved courtyard and the avenues, the fountain, the gardens. */
    private void grounds(Masonry m) {
        if (m.touches(-REACH, -MEND, -REACH, REACH, 0, REACH)) {
            for (int x = -REACH; x <= REACH; x++) {
                for (int z = -REACH; z <= REACH; z++) {
                    if (!m.chunk.isInside(m.at(x, 0, z))) {
                        continue;
                    }
                    for (int y = -1; y >= -MEND && !m.solid(x, y, z); y--) {
                        m.set(x, y, z, Blocks.DIRT.defaultBlockState());
                    }
                }
            }
        }
        m.fill(-WALL, 1, -WALL, WALL, 14, WALL, Blocks.AIR.defaultBlockState());
        m.fill(-WALL, 0, -WALL, WALL, 0, WALL, Blocks.GRASS_BLOCK.defaultBlockState());
        // The courtyard, paved in two stones, and the avenues to every door and out of the gate.
        for (int x = -24; x <= 24; x++) {
            for (int z = -16; z <= 26; z++) {
                pave(m, x, z);
            }
        }
        avenue(m, -2, 2, 26, REACH);
        avenue(m, -31, -25, -15, -13);
        avenue(m, 25, 31, -15, -13);
        avenue(m, -2, 2, -19, -17);
        avenue(m, -52, 52, 30, 32);
        avenue(m, -2, 2, 27, 29);
        // The fountain: a round basin of solid stone, the water in it, a column in the middle with a light on it.
        m.ring(0, 0, 5, 6, Blocks.STONE_BRICKS.defaultBlockState(), true);
        m.ring(0, 1, 5, 6, Blocks.STONE_BRICKS.defaultBlockState(), true);
        m.ring(0, 1, 5, 5, Blocks.WATER.defaultBlockState(), true);
        m.ring(0, 2, 5, 6, Blocks.STONE_BRICK_SLAB.defaultBlockState(), false);
        m.fill(0, 1, 5, 0, 4, 5, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        m.set(0, 5, 5, Blocks.LANTERN.defaultBlockState());
        // Gardens at the corners of the courtyard, hedged, with a tree each; trees on the lawns.
        garden(m, -22, -10, -13, -5);
        garden(m, 10, 22, -13, -5);
        garden(m, -22, -10, 15, 24);
        garden(m, 10, 22, 15, 24);
        for (int[] at : new int[][]{{-46, 12}, {-36, 18}, {46, 12}, {36, 18}, {-46, -56}, {46, -56}, {-10, 58},
                {12, 58}, {-28, -30}, {28, -30}}) {
            tree(m, at[0], at[1]);
        }
    }

    private static void pave(Masonry m, int x, int z) {
        m.set(x, 0, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                : Blocks.STONE_BRICKS.defaultBlockState());
    }

    private static void avenue(Masonry m, int x0, int x1, int z0, int z1) {
        if (!m.touches(x0, 0, z0, x1, 0, z1)) {
            return;
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                m.set(x, 0, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
    }

    /** A garden bed: grass hedged by low leaves, flowers, a tree in the middle. */
    static void garden(Masonry m, int x0, int x1, int z0, int z1) {
        if (!m.touches(x0, 0, z0, x1, 8, z1)) {
            return;
        }
        BlockState hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                m.set(x, 0, z, Blocks.GRASS_BLOCK.defaultBlockState());
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                boolean gap = edge && (x == (x0 + x1) / 2 || z == (z0 + z1) / 2);
                if (edge && !gap) {
                    m.set(x, 1, z, hedge);
                } else if (!edge) {
                    int roll = m.chance(x, 1, z);
                    if (roll < 25) {
                        m.set(x, 1, z, (roll < 8 ? Blocks.POPPY : roll < 16 ? Blocks.CORNFLOWER : Blocks.ALLIUM)
                                .defaultBlockState());
                    }
                }
            }
        }
        tree(m, (x0 + x1) / 2, (z0 + z1) / 2);
        m.set((x0 + x1) / 2 + 2, 1, (z0 + z1) / 2, Blocks.LANTERN.defaultBlockState());
    }

    /** A small tree, as a gardener keeps one: a trunk and a round crown of leaves that never fall. */
    static void tree(Masonry m, int x, int z) {
        if (!m.touches(x - 3, 0, z - 3, x + 3, 8, z + 3)) {
            return;
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 4; dy <= 7; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double d = Math.sqrt(dx * dx + (dy - 5.5D) * (dy - 5.5D) * 1.3D + dz * dz);
                    if (d <= 2.6D) {
                        m.set(x + dx, dy, z + dz, leaves);
                    }
                }
            }
        }
        m.set(x, 0, z, Blocks.GRASS_BLOCK.defaultBlockState());
        m.fill(x, 1, z, x, 5, z, Blocks.OAK_LOG.defaultBlockState());
    }

    /**
     * Lamp posts on the lawns and the courtyard, wherever there is room for one, so nothing is born of the dark in the
     * grounds; lanterns along the wall's walk.
     */
    private void lampPosts(Masonry m) {
        for (int x = -WALL + 4; x <= WALL - 4; x += LAMPS) {
            for (int z = -WALL + 4; z <= WALL - 4; z += LAMPS) {
                if (!m.chunk.isInside(m.at(x, 0, z))) {
                    continue;
                }
                BlockState ground = m.get(x, 0, z);
                boolean open = ground.is(Blocks.GRASS_BLOCK) && m.get(x, 1, z).isAir() && m.get(x, 2, z).isAir()
                        && m.get(x, 3, z).isAir();
                if (open) {
                    m.set(x, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    m.set(x, 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    m.set(x, 3, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
        // The courtyard: posts round its edge and among its paving, clear of the avenues and the fountain.
        for (int x = -24; x <= 24; x += 8) {
            for (int z = -16; z <= 26; z += 7) {
                boolean edge = Math.abs(x) == 24 || z == -16 || z == 26;
                boolean inner = (Math.abs(x) == 8 || Math.abs(x) == 16) && (z == -9 || z == 12 || z == 19);
                boolean clear = Math.abs(x) > 3 && (Math.abs(x) > 8 || Math.abs(z - 5) > 8);
                if ((edge || inner) && clear && m.get(x, 1, z).isAir() && m.get(x, 2, z).isAir()
                        && m.get(x, 3, z).isAir()) {
                    m.set(x, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    m.set(x, 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    m.set(x, 3, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
        // Beside the fountain, along the gate's avenue, and along the inside of the wall.
        List<int[]> more = new ArrayList<>(List.of(new int[]{-11, 5}, new int[]{11, 5}));
        for (int z = 30; z <= WALL - 3; z += 7) {
            more.add(new int[]{-4, z});
            more.add(new int[]{4, z});
        }
        for (int i = -WALL + 6; i <= WALL - 6; i += 7) {
            more.add(new int[]{i, -WALL + 3});
            more.add(new int[]{i, WALL - 3});
            more.add(new int[]{-WALL + 3, i});
            more.add(new int[]{WALL - 3, i});
        }
        for (int[] at : more) {
            if (m.chunk.isInside(m.at(at[0], 0, at[1])) && m.get(at[0], 1, at[1]).isAir()
                    && m.get(at[0], 2, at[1]).isAir() && m.get(at[0], 3, at[1]).isAir()
                    && !m.get(at[0], 0, at[1]).isAir()) {
                m.set(at[0], 1, at[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
                m.set(at[0], 2, at[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
                m.set(at[0], 3, at[1], Blocks.LANTERN.defaultBlockState());
            }
        }
        for (int i = -WALL + 4; i <= WALL - 4; i += 8) {
            for (int[] at : new int[][]{{i, -WALL + 1}, {i, WALL - 1}, {-WALL + 1, i}, {WALL - 1, i}}) {
                if (m.get(at[0], 10, at[1]).isAir()) {
                    m.set(at[0], 10, at[1], Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }

    // ------------------------------------------------------------------ the wall

    /** The curtain wall with its walk and battlements, its towers, and the gatehouse. */
    private void walls(Masonry m) {
        int height = 9;
        for (int i = -WALL; i <= WALL; i++) {
            for (int[] at : new int[][]{{i, -WALL}, {i, WALL}, {-WALL, i}, {WALL, i}}) {
                boolean gate = at[1] == WALL && Math.abs(at[0]) <= 3;
                int inX = at[0] == WALL ? -1 : at[0] == -WALL ? 1 : 0;
                int inZ = at[1] == WALL ? -1 : at[1] == -WALL ? 1 : 0;
                if (gate) {
                    m.fill(at[0], 1, at[1], at[0] + inX, 6, at[1] + inZ, Blocks.AIR.defaultBlockState());
                    m.fill(at[0], 7, at[1], at[0] + inX, height, at[1] + inZ, Blocks.STONE_BRICKS.defaultBlockState());
                } else {
                    for (int y = 0; y <= height; y++) {
                        m.set(at[0], y, at[1], m.stone(at[0], y, at[1]));
                        m.set(at[0] + inX, y, at[1] + inZ, m.stone(at[0] + inX, y, at[1] + inZ));
                    }
                }
                if ((i & 1) == 0) {
                    m.set(at[0], height + 1, at[1], m.stone(at[0], height + 1, at[1]));
                }
            }
        }
        for (int[] at : new int[][]{{-WALL, -WALL}, {WALL, -WALL}, {-WALL, WALL}, {WALL, WALL}}) {
            roundTower(m, at[0], at[1], 5, 17);
        }
        for (int[] at : new int[][]{{-WALL, 0}, {WALL, 0}, {0, -WALL}}) {
            roundTower(m, at[0], at[1], 4, 14);
        }
        // The gatehouse: two towers flanking the gate, a raised portcullis, lights under the lintel.
        roundTower(m, -7, WALL, 4, 18);
        roundTower(m, 7, WALL, 4, 18);
        m.fill(-3, 6, WALL, 3, 6, WALL, Blocks.IRON_BARS.defaultBlockState());
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        m.set(-2, 6, WALL - 1, hanging);
        m.set(2, 6, WALL - 1, hanging);
    }

    /** A round tower of the wall: solid stone to its walk, a ring of battlements, a slate cone. */
    static void roundTower(Masonry m, int x, int z, int radius, int height) {
        if (!m.touches(x - radius - 1, 0, z - radius - 1, x + radius + 1, height + radius * 2 + 4, z + radius + 1)) {
            return;
        }
        for (int y = 0; y <= height; y++) {
            m.ring(x, y, z, radius, Blocks.STONE_BRICKS.defaultBlockState(), true);
        }
        for (int y = 3; y < height - 1; y += 4) {
            for (int[] slit : new int[][]{{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}}) {
                m.set(x + slit[0], y, z + slit[1], Blocks.IRON_BARS.defaultBlockState());
            }
        }
        m.ring(x, height + 1, z, radius, Blocks.STONE_BRICK_WALL.defaultBlockState(), false);
        m.cone(x, height + 2, z, radius, 2, Blocks.DEEPSLATE_TILES.defaultBlockState(), SLATE_EDGE);
        m.set(x, height + 2 + (radius + 1) * 2, z, Blocks.LIGHTNING_ROD.defaultBlockState());
        m.set(x + radius - 1, height + 1, z, Blocks.LANTERN.defaultBlockState());
    }

    // ------------------------------------------------------------------ the Great Hall

    static void greatHall(Masonry m) {
        Hall hall = new Hall(-20, -56, 20, -20, 20, true, Blocks.POLISHED_ANDESITE);
        hall.draw(m);
        if (!m.touches(-27, 0, -57, 27, TOP, -15)) {
            return;
        }
        // Its front: a tall portal and the rose window above it, between two towers with spires.
        m.fill(-2, 1, -20, 2, 6, -20, Blocks.AIR.defaultBlockState());
        m.door(-1, -20, Direction.SOUTH, Blocks.DARK_OAK_DOOR, false);
        m.door(1, -20, Direction.SOUTH, Blocks.DARK_OAK_DOOR, true);
        m.fill(-1, 3, -20, 1, 6, -20, Blocks.AIR.defaultBlockState());
        m.set(-2, 6, -20, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
        m.set(2, 6, -20, Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        m.set(-3, 4, -19, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        m.set(3, 4, -19, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 4.5D) {
                    m.set(dx, 13 + dy, -20, d > 3.5D ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                            : (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy))
                            ? Blocks.STONE_BRICKS.defaultBlockState()
                            : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                }
            }
        }
        frontTower(m, -26, -24);
        frontTower(m, 20, -24);
        // The spire over the middle of the nave, on the ridge.
        int ridge = 20 + hall.roofRise() - 1;
        m.fill(-2, ridge - 3, -40, 2, ridge + 4, -36, Blocks.STONE_BRICKS.defaultBlockState());
        m.fill(-1, ridge + 1, -40, 1, ridge + 3, -36, Blocks.AIR.defaultBlockState());
        m.fill(-2, ridge + 2, -38, 2, ridge + 2, -38, Blocks.AIR.defaultBlockState());
        m.set(0, ridge + 1, -38, Blocks.LANTERN.defaultBlockState());
        m.cone(0, ridge + 5, -38, 3, 3, Blocks.DEEPSLATE_TILES.defaultBlockState(), SLATE_EDGE);
        m.set(0, Math.min(TOP, ridge + 5 + 12), -38, Blocks.LIGHTNING_ROD.defaultBlockState());
        // The nave's columns, the dais and the Rector's seat, the council's table, a carpet up the middle.
        for (int z = -52; z <= -24; z += 6) {
            for (int x : new int[]{-9, 9}) {
                m.fill(x, 1, z, x, 19, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                m.set(x, 19, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                m.set(x, 8, z + 1, Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, Direction.SOUTH));
            }
        }
        m.fill(-19, 1, -55, 19, 1, -51, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int x = -19; x <= 19; x++) {
            m.set(x, 1, -50, Masonry.stairs(Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH));
        }
        m.fill(-8, 2, -55, 8, 4, -55, Blocks.BOOKSHELF.defaultBlockState());
        m.set(0, 2, -53, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
        m.lectern(0, 2, -52, Direction.NORTH, () -> RuneGuides.familyGuide(RuneGuides.Family.FONTS));
        m.fill(-3, 1, -46, 3, 1, -32, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        for (int z = -46; z <= -32; z += 2) {
            m.set(-4, 1, z, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST));
            m.set(4, 1, z, Masonry.stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST));
        }
        for (int z = -31; z <= -21; z++) {
            m.set(0, 1, z, Blocks.RED_CARPET.defaultBlockState());
        }
        for (int z = -50; z <= -26; z += 8) {
            m.chandelier(0, 20, z, 4);
            m.chandelier(-14, 20, z, 3);
            m.chandelier(14, 20, z, 3);
        }
    }

    /** A square tower before the Great Hall's front, with a slate spire, lit on every floor. */
    static void frontTower(Masonry m, int x0, int z0) {
        int height = 30;
        for (int x = x0; x <= x0 + 6; x++) {
            for (int z = z0; z <= z0 + 6; z++) {
                boolean edge = x == x0 || x == x0 + 6 || z == z0 || z == z0 + 6;
                boolean corner = (x == x0 || x == x0 + 6) && (z == z0 || z == z0 + 6);
                for (int y = 0; y <= height; y++) {
                    BlockState state = !edge ? (y == 0 || y % 8 == 0 ? Blocks.DARK_OAK_SLAB.defaultBlockState()
                            : Blocks.AIR.defaultBlockState())
                            : corner ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                            : (x == x0 + 3 || z == z0 + 3) && y % 8 >= 3 && y % 8 <= 5
                            ? Blocks.GLASS.defaultBlockState() : m.stone(x, y, z);
                    m.set(x, y, z, state);
                }
            }
        }
        for (int y = 1; y < height; y += 8) {
            m.set(x0 + 3, y, z0 + 3, Blocks.LANTERN.defaultBlockState());
        }
        m.cone(x0 + 3, height + 1, z0 + 3, 4, 3, Blocks.DEEPSLATE_TILES.defaultBlockState(), SLATE_EDGE);
        m.set(x0 + 3, height + 1 + 15, z0 + 3, Blocks.LIGHTNING_ROD.defaultBlockState());
    }

    // ------------------------------------------------------------------ the Library

    static void library(Masonry m) {
        Hall library = new Hall(-56, -48, -32, 0, 16, true, Blocks.DARK_OAK_PLANKS);
        library.draw(m);
        if (!m.touches(-57, 0, -49, -31, 17, 1)) {
            return;
        }
        m.fill(-32, 1, -15, -32, 3, -13, Blocks.AIR.defaultBlockState());
        m.door(-32, -14, Direction.EAST, Blocks.DARK_OAK_DOOR, false);
        // The gallery round the walls, its railing with a gap where the stair comes up, and the stair.
        int gallery = 8;
        m.fill(-55, gallery, -47, -53, gallery, -1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.fill(-35, gallery, -47, -33, gallery, -1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.fill(-52, gallery, -47, -36, gallery, -45, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.fill(-52, gallery, -3, -36, gallery, -1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.fill(-52, gallery + 1, -44, -52, gallery + 1, -4, Blocks.DARK_OAK_FENCE.defaultBlockState());
        m.fill(-36, gallery + 1, -44, -36, gallery + 1, -4, Blocks.DARK_OAK_FENCE.defaultBlockState());
        m.fill(-51, gallery + 1, -44, -37, gallery + 1, -44, Blocks.DARK_OAK_FENCE.defaultBlockState());
        m.fill(-51, gallery + 1, -4, -37, gallery + 1, -4, Blocks.DARK_OAK_FENCE.defaultBlockState());
        // The stair runs up along the inside of the west gallery, from the south, and comes out on it.
        Hall.stair(m, -51, -6, Direction.NORTH, 0, gallery, Blocks.DARK_OAK_STAIRS);
        m.fill(-52, gallery + 1, -15, -52, gallery + 1, -11, Blocks.AIR.defaultBlockState());
        m.fill(-52, gallery, -15, -52, gallery, -11, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        m.set(-51, gallery, -14, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        // Light under the gallery and on it.
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int z = -45; z <= -3; z += 5) {
            m.set(-54, gallery - 1, z, hanging);
            m.set(-34, gallery - 1, z, hanging);
        }
        // Shelves on the gallery against the walls, between the windows.
        for (int z = -47; z <= -1; z++) {
            if (Math.floorMod(z + 48, 4) != 2) {
                m.fill(-55, gallery + 1, z, -55, gallery + 4, z, Blocks.BOOKSHELF.defaultBlockState());
                m.fill(-33, gallery + 1, z, -33, gallery + 4, z, Blocks.BOOKSHELF.defaultBlockState());
            }
        }
        // The stacks: rows of tall shelves across the floor, with room between them.
        for (int z = -36; z <= -16; z += 5) {
            m.fill(-49, 1, z, -39, 4, z, Blocks.BOOKSHELF.defaultBlockState());
        }
        // The closed room at the north end, behind an iron door.
        m.fill(-55, 1, -39, -33, gallery - 1, -39, Blocks.STONE_BRICKS.defaultBlockState());
        BlockState iron = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        m.set(-44, 1, -39, iron.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        m.set(-44, 2, -39, iron.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        m.fill(-52, 1, -47, -36, 3, -47, Blocks.BOOKSHELF.defaultBlockState());
        m.set(-44, 1, -43, Blocks.ENCHANTING_TABLE.defaultBlockState());
        m.set(-40, 1, -43, Blocks.LANTERN.defaultBlockState());
        m.set(-48, 1, -43, Blocks.LANTERN.defaultBlockState());
        m.chest(-51, 1, -42, Direction.EAST, "chests/university_archive");
        m.chest(-37, 1, -42, Direction.WEST, "chests/university_archive");
        // The reading room by the door: tables and lecterns with guides.
        for (int z = -8; z <= -2; z += 3) {
            m.fill(-48, 1, z, -42, 1, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        }
        int guides = 0;
        for (int[] at : new int[][]{{-38, -8}, {-38, -4}, {-38, 0 - 2}, {-38, -16}}) {
            int index = guides++;
            m.lectern(at[0], 1, at[1], Direction.WEST, () -> runeGuide(index * 5 + 1));
        }
    }

    // ------------------------------------------------------------------ the Research Wing

    /** The seven chairs whose rooms are in the Research Wing, from north to south. */
    private static final Discipline[] CHAIRS = {Discipline.FONTES, Discipline.FUNCOES, Discipline.FILTROS,
            Discipline.FATOS, Discipline.GALDRARIA, Discipline.VITA, Discipline.NAHVA};
    /** The rooms of the ground floor's west side, from north to south (the one at -17 is the way in). */
    private static final int[] ROOMS = {-47, -41, -35, -29, -23, -11, -5};

    private void researchWing(Masonry m) {
        int upper = 7;
        Hall wing = new Hall(32, -48, 56, 4, 13, true, Blocks.SPRUCE_PLANKS, upper);
        wing.draw(m);
        if (!m.touches(31, 0, -49, 57, 14, 5)) {
            return;
        }
        m.fill(32, 1, -15, 32, 2, -13, Blocks.AIR.defaultBlockState());
        m.door(32, -14, Direction.WEST, Blocks.DARK_OAK_DOOR, false);
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        // The corridor's walls on both storeys, and the walls between the rooms, each room lit.
        for (int storey : new int[]{0, upper}) {
            int ceiling = storey == 0 ? upper - 1 : 12;
            m.fill(42, storey + 1, -47, 42, ceiling, 3, Blocks.STONE_BRICKS.defaultBlockState());
            m.fill(46, storey + 1, -47, 46, ceiling, 3, Blocks.STONE_BRICKS.defaultBlockState());
            for (int z = -42; z <= 0; z += 6) {
                m.fill(33, storey + 1, z, 41, ceiling, z, Blocks.STONE_BRICKS.defaultBlockState());
                m.fill(47, storey + 1, z, 55, ceiling, z, Blocks.STONE_BRICKS.defaultBlockState());
            }
            m.fill(42, storey + 1, 2, 42, storey + 2, 2, Blocks.AIR.defaultBlockState());
            m.fill(46, storey + 1, 2, 46, storey + 2, 2, Blocks.AIR.defaultBlockState());
            for (int z = -47; z <= 3; z += 6) {
                m.fill(42, storey + 1, z + 1, 42, storey + 2, z + 1, Blocks.AIR.defaultBlockState());
                m.fill(46, storey + 1, z + 1, 46, storey + 2, z + 1, Blocks.AIR.defaultBlockState());
                m.set(37, ceiling, z + 2, hanging);
                m.set(51, ceiling, z + 2, hanging);
                m.set(44, ceiling, z + 2, hanging);
            }
        }
        // The way in: the room by the door is open to the corridor; the stair up at the south end of the corridor.
        m.fill(42, 1, -17, 42, 3, -13, Blocks.AIR.defaultBlockState());
        Hall.stair(m, 44, 3, Direction.NORTH, 0, upper, Blocks.SPRUCE_STAIRS);
        for (int i = 0; i < CHAIRS.length; i++) {
            chairRoom(m, CHAIRS[i], ROOMS[i]);
        }
        // East of the corridor and upstairs: rooms of study, with shelves, a table and its benches.
        for (int z0 = -47; z0 <= -1; z0 += 6) {
            for (int storey : new int[]{0, upper}) {
                m.fill(55, storey + 1, z0, 55, storey + 3, z0 + 4, Blocks.BOOKSHELF.defaultBlockState());
                m.fill(50, storey + 1, z0 + 2, 52, storey + 1, z0 + 2, Blocks.SPRUCE_PLANKS.defaultBlockState());
                m.set(51, storey + 1, z0 + 1, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
                m.set(51, storey + 1, z0 + 3, Masonry.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            }
            if (z0 != -17) {
                m.fill(33, upper + 1, z0, 33, upper + 3, z0 + 4, Blocks.BOOKSHELF.defaultBlockState());
            }
        }
    }

    /** A master's room: shelves, a lectern with its family's guide, the tools of the chair. */
    private static void chairRoom(Masonry m, Discipline chair, int z0) {
        m.fill(33, 1, z0, 33, 3, z0 + 4, Blocks.BOOKSHELF.defaultBlockState());
        m.lectern(35, 1, z0 + 2, Direction.EAST, () -> chair.family().map(RuneGuides::familyGuide)
                .orElseGet(() -> runeGuide(chair.ordinal() * 3)));
        int x = 37;
        switch (chair) {
            case FONTES -> {
                m.set(x, 1, z0, Blocks.CAMPFIRE.defaultBlockState());
                m.set(x + 1, 1, z0, Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(LayeredCauldronBlock.LEVEL, 3));
                m.set(x + 2, 1, z0, Blocks.AMETHYST_BLOCK.defaultBlockState());
                m.set(x + 2, 2, z0, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            }
            case FUNCOES -> {
                m.set(x, 1, z0, Blocks.TARGET.defaultBlockState());
                m.set(x + 1, 1, z0, Blocks.DISPENSER.defaultBlockState());
                m.set(x + 2, 1, z0, Blocks.LODESTONE.defaultBlockState());
            }
            case FILTROS -> {
                m.set(x, 1, z0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
                m.set(x + 1, 1, z0, Blocks.COMPOSTER.defaultBlockState());
            }
            case FATOS -> {
                m.set(x, 1, z0, Blocks.NOTE_BLOCK.defaultBlockState());
                m.set(x + 1, 1, z0, Blocks.BELL.defaultBlockState());
            }
            case GALDRARIA -> {
                m.set(x, 1, z0, Galdraria.TABLE.get().defaultBlockState());
                m.set(x + 1, 1, z0, Blocks.GRINDSTONE.defaultBlockState());
                m.set(x + 2, 1, z0, Blocks.SMITHING_TABLE.defaultBlockState());
            }
            case VITA -> {
                m.set(x, 1, z0, Blocks.BREWING_STAND.defaultBlockState());
                m.set(x + 1, 1, z0, Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(LayeredCauldronBlock.LEVEL, 3));
            }
            case NAHVA -> {
                BlockState bed = Blocks.PURPLE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
                m.set(x, 1, z0, bed.setValue(BedBlock.PART, BedPart.FOOT));
                m.set(x + 1, 1, z0, bed.setValue(BedBlock.PART, BedPart.HEAD));
                m.set(x + 2, 1, z0, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ the Observatory

    static void observatory(Masonry m) {
        int x = 46;
        int z = 52;
        int radius = 6;
        int height = 28;
        if (!m.touches(x - radius - 1, 0, z - radius - 1, x + radius + 1, height + radius + 3, z + radius + 1)) {
            return;
        }
        for (int y = 0; y <= height; y++) {
            m.ring(x, y, z, radius, y == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : m.stone(x, y, z), y == 0);
            if (y > 0) {
                m.ring(x, y, z, radius - 1, Blocks.AIR.defaultBlockState(), true);
            }
        }
        for (int y = 4; y < height; y += 6) {
            for (int[] window : new int[][]{{radius, 0}, {0, -radius}, {0, radius}}) {
                m.set(x + window[0], y, z + window[1], Blocks.GLASS.defaultBlockState());
                m.set(x + window[0], y + 1, z + window[1], Blocks.GLASS.defaultBlockState());
            }
        }
        m.fill(x - radius, 1, z, x - radius, 2, z, Blocks.AIR.defaultBlockState());
        m.door(x - radius, z, Direction.WEST, Blocks.DARK_OAK_DOOR, false);
        // The stair winding round the column up to the top room; torches on the wall all the way up.
        int[][] spiral = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        m.fill(x, 0, z, x, height, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int k = 1; k <= height; k++) {
            int[] cell = spiral[(k - 1) % spiral.length];
            m.set(x + cell[0], k, z + cell[1], Blocks.POLISHED_ANDESITE.defaultBlockState());
            for (int above = 1; above <= 3; above++) {
                if (k + above <= height) {
                    m.set(x + cell[0], k + above, z + cell[1], Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int y = 3; y < height; y += 4) {
            m.set(x + radius - 1, y, z, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,
                    Direction.WEST));
            m.set(x, y, z - radius + 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,
                    Direction.SOUTH));
            m.set(x, y, z + radius - 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,
                    Direction.NORTH));
        }
        // The top room under the glass dome: the table of the stars, a telescope, a light.
        m.ring(x, height, z, radius - 1, Blocks.SPRUCE_SLAB.defaultBlockState(), true);
        int[] last = spiral[(height - 1) % spiral.length];
        m.set(x + last[0], height, z + last[1], Blocks.POLISHED_ANDESITE.defaultBlockState());
        m.set(x + 3, height + 1, z, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        m.set(x - 3, height + 1, z + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
        m.set(x - 3, height + 2, z + 1, Blocks.LIGHTNING_ROD.defaultBlockState());
        m.set(x, height + 1, z + 3, Blocks.LANTERN.defaultBlockState());
        for (int dy = 1; dy <= radius + 1; dy++) {
            int r = (int) Math.round(Math.sqrt(Math.max(0, (radius + 0.5D) * (radius + 0.5D) - dy * dy)));
            m.ring(x, height + dy, z, r, dy == 1 ? m.stone(x, height + dy, z)
                    : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), false);
        }
        m.set(x, height + radius + 2, z, Blocks.LIGHTNING_ROD.defaultBlockState());
    }

    // ------------------------------------------------------------------ the south

    private void dormitory(Masonry m) {
        int upper = 6;
        Hall dorm = new Hall(-56, 36, -34, 56, 11, true, Blocks.SPRUCE_PLANKS, upper);
        dorm.draw(m);
        if (!m.touches(-57, 0, 35, -33, 12, 57)) {
            return;
        }
        m.fill(-45, 1, 36, -45, 2, 36, Blocks.AIR.defaultBlockState());
        m.door(-45, 36, Direction.NORTH, Blocks.SPRUCE_DOOR, false);
        Hall.stair(m, -36, 38, Direction.SOUTH, 0, upper, Blocks.SPRUCE_STAIRS);
        BlockState bed = Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.WEST);
        for (int storey : new int[]{0, upper}) {
            for (int z = 40; z <= 54; z += 2) {
                m.set(-54, storey + 1, z, bed.setValue(BedBlock.PART, BedPart.FOOT));
                m.set(-55, storey + 1, z, bed.setValue(BedBlock.PART, BedPart.HEAD));
                m.set(-53, storey + 1, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,
                        Direction.EAST));
            }
        }
    }

    private void refectory(Masonry m) {
        Hall refectory = new Hall(-26, 36, -8, 56, 9, true, Blocks.OAK_PLANKS);
        refectory.draw(m);
        if (!m.touches(-27, 0, 35, -7, 10, 57)) {
            return;
        }
        m.fill(-17, 1, 36, -17, 2, 36, Blocks.AIR.defaultBlockState());
        m.door(-17, 36, Direction.NORTH, Blocks.OAK_DOOR, false);
        for (int x : new int[]{-22, -12}) {
            m.fill(x, 1, 40, x, 1, 53, Blocks.OAK_PLANKS.defaultBlockState());
            for (int z = 40; z <= 53; z += 2) {
                m.set(x - 1, 1, z, Masonry.stairs(Blocks.OAK_STAIRS, Direction.WEST));
                m.set(x + 1, 1, z, Masonry.stairs(Blocks.OAK_STAIRS, Direction.EAST));
            }
        }
        m.set(-18, 1, 55, Blocks.SMOKER.defaultBlockState());
        m.set(-17, 1, 55, Blocks.BARREL.defaultBlockState());
        m.set(-16, 1, 55, Blocks.SMOKER.defaultBlockState());
    }

    private void workshop(Masonry m) {
        Hall shop = new Hall(8, 36, 26, 48, 8, false, Blocks.OAK_PLANKS);
        shop.draw(m);
        if (!m.touches(7, 0, 35, 27, 9, 49)) {
            return;
        }
        m.fill(17, 1, 36, 17, 2, 36, Blocks.AIR.defaultBlockState());
        m.door(17, 36, Direction.NORTH, Blocks.OAK_DOOR, false);
        for (int x : new int[]{10, 13, 21, 24}) {
            m.set(x, 1, 40, Galdraria.TABLE.get().defaultBlockState());
            m.set(x, 1, 44, Blocks.GRINDSTONE.defaultBlockState());
        }
        for (int x = 10; x <= 12; x++) {
            m.chest(x, 1, 47, Direction.NORTH, "chests/wizard_house");
        }
        m.set(23, 1, 47, Blocks.BARREL.defaultBlockState());
        m.set(24, 1, 47, Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    private void infirmary(Masonry m) {
        Hall infirmary = new Hall(30, 36, 52, 44, 8, false, Blocks.BIRCH_PLANKS);
        infirmary.draw(m);
        if (!m.touches(29, 0, 35, 53, 9, 45)) {
            return;
        }
        m.fill(41, 1, 36, 41, 2, 36, Blocks.AIR.defaultBlockState());
        m.door(41, 36, Direction.NORTH, Blocks.BIRCH_DOOR, false);
        BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        for (int x = 32; x <= 50; x += 2) {
            if (Math.abs(x - 41) <= 1) {
                continue;
            }
            m.set(x, 1, 42, bed.setValue(BedBlock.PART, BedPart.FOOT));
            m.set(x, 1, 43, bed.setValue(BedBlock.PART, BedPart.HEAD));
        }
        for (int x : new int[]{33, 49}) {
            m.set(x, 1, 38, Blocks.BREWING_STAND.defaultBlockState());
            m.set(x + (x < 41 ? 1 : -1), 1, 38, Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, 3));
        }
    }

    // ------------------------------------------------------------------ who lives there

    private void people(Masonry m) {
        wizard(m, 0, 2, -51, Rank.RECTOR, Discipline.REITOR, 18);
        for (int i = 0; i < CHAIRS.length; i++) {
            wizard(m, 39, 1, ROOMS[i] + 3, Rank.MAGISTER, CHAIRS[i], 6);
        }
        wizard(m, -44, 1, -10, Rank.MAGISTER, Discipline.ARQUIVO, 14);
        wizard(m, 9, 1, 12, Rank.AUDITOR, null, WALL);
        wizard(m, -9, 1, 12, Rank.AUDITOR, null, WALL);
        wizard(m, 8, 1, -14, Rank.SCRIBA, null, WALL);
        wizard(m, -8, 1, -14, Rank.SCRIBA, null, WALL);
        wizard(m, -46, 1, -12, Rank.LECTOR, null, WALL);
        wizard(m, 46, 1, 50, Rank.AUDITOR, null, WALL);
        villager(m, -39, -6, VillagerProfession.LIBRARIAN);
        villager(m, -39, -14, VillagerProfession.LIBRARIAN);
        villager(m, 36, 40, VillagerProfession.CLERIC);
        villager(m, 46, 40, VillagerProfession.CLERIC);
    }

    static void wizard(Masonry m, int x, int y, int z, Rank rank, Discipline chair, int radius) {
        BlockPos at = m.at(x, y, z);
        if (!m.chunk.isInside(at)) {
            return;
        }
        Wizard wizard = ElderLexicon.WIZARD.get().create(m.level.getLevel());
        if (wizard == null) {
            return;
        }
        wizard.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        wizard.finalizeSpawn(m.level, m.level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
        wizard.joinUniversity(rank, chair, radius);
        m.level.addFreshEntityWithPassengers(wizard);
    }

    static void villager(Masonry m, int x, int z, VillagerProfession profession) {
        BlockPos at = m.at(x, 1, z);
        if (!m.chunk.isInside(at)) {
            return;
        }
        Villager villager = EntityType.VILLAGER.create(m.level.getLevel());
        if (villager == null) {
            return;
        }
        villager.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        villager.finalizeSpawn(m.level, m.level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
        villager.setVillagerData(villager.getVillagerData().setType(VillagerType.byBiome(m.level.getBiome(at)))
                .setProfession(profession));
        villager.setVillagerXp(1); // it keeps its trade, a job site or not
        villager.setPersistenceRequired();
        m.level.addFreshEntityWithPassengers(villager);
    }

    /** A rune's guide, the {@code index}th of all the runes guides are kept of. */
    static ItemStack runeGuide(int index) {
        List<String> runes = new ArrayList<>();
        for (RuneGuides.Family family : RuneGuides.Family.values()) {
            runes.addAll(RuneGuides.runesOf(family));
        }
        return runes.isEmpty() ? ItemStack.EMPTY
                : RuneGuides.runeGuide(runes.get(Math.floorMod(index * 7, runes.size()))).orElse(ItemStack.EMPTY);
    }
}
