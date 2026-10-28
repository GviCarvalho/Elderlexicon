package com.elderlexicon.mod.university;

import com.elderlexicon.mod.tower.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A terrace of the campus (docs/universidade-arquitetura-design.md, 5.3): the campus does not lie on the land building
 * by building, each at its own height, but on a few level terraces, every building of one at the same ground.
 * <ul>
 *   <li><b>A platform</b> is the terrace's ground: the land under it mended with earth, over it cleared, grass on top.
 *       Its box is what the game shapes the land to round it ({@code beard_thin}), so it falls away in a slope and not
 *       a cliff. Where the terrace next to it is lower, its edge is a retaining wall of stone, a band on top and a
 *       parapet, with gaps where a stair or an avenue goes down.</li>
 *   <li><b>A stair</b> goes from a terrace down to the one below it, from its retaining wall: drawn after the buildings,
 *       so nothing of theirs covers it.</li>
 * </ul>
 * Everything is in the campus's offsets ({@code x} along, {@code z} across), turned as the campus is; a wall and a stair
 * are always on the {@code z1} side of the higher terrace, the side toward the south of the campus.
 */
public class TerracePiece extends StructurePiece {

    /** How deep the land under a terrace is mended (down to firm ground), and how high above it it is cleared. */
    private static final int MEND = 64;
    private static final int CLEAR = 40;
    /**
     * The escarp: how high the plateau's sloped edge is, from its foot out on the land to the terrace's ground, and how
     * many blocks deep it is from the edge in (a step of four a block, as steep as a bastion's).
     */
    static final int ESCARP = 12;
    private static final int ESCARP_DEPTH = 3;

    private final int cx;
    private final int cz;
    private final Rotation rotation;
    private final int y;
    private final int x0;
    private final int z0;
    private final int x1;
    private final int z1;
    /** A platform: how far its z1 edge falls to the terrace below (0: no wall). A stair: how many steps. */
    private final int drop;
    private final boolean stair;
    /** A platform's gaps in the parapet, as pairs of x (from, to). */
    private final int[] gaps;
    /** A platform: the plateau's rectangles, four numbers each, and where its rampart opens (a rectangle). */
    private final int[] plateau;
    private final int[] gate;
    /**
     * The height of the plateau's ring, the middle terrace's: the escarp is laid from it all round, so it is one line;
     * a terrace higher than the ring rises over its last step, and only its parapet follows it.
     */
    private final int ring;
    /** A tower of the rampart (then {@code x0, z0} is its middle, {@code x1} its radius, {@code z1} its height). */
    private final boolean tower;

    private TerracePiece(int cx, int cz, Rotation rotation, int y, int x0, int z0, int x1, int z1, int drop,
                         boolean stair, int[] gaps, int[] plateau, int[] gate, boolean tower, int ring) {
        super(Towers.CAMPUS_TERRACE_PIECE.get(), 0, tower
                ? box(cx, cz, rotation, y, x0 - x1 - 2, z0 - x1 - 2, x0 + x1 + 2, z0 + x1 + 2, z1, true,
                ESCARP + Math.max(0, y - ring))
                : box(cx, cz, rotation, y, x0, z0, x1, z1, drop, stair, stair ? 0 : ESCARP + Math.max(0, y - ring)));
        this.cx = cx;
        this.cz = cz;
        this.rotation = rotation;
        this.y = y;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.drop = drop;
        this.stair = stair;
        this.gaps = gaps;
        this.plateau = plateau;
        this.gate = gate;
        this.tower = tower;
        this.ring = ring;
    }

    /** The ground of a terrace from {@code (x0, z0)} to {@code (x1, z1)}, at the world's height {@code y}. */
    static TerracePiece platform(int cx, int cz, Rotation rotation, int y, int x0, int z0, int x1, int z1, int drop,
                                 int[] plateau, int[] gate, int ring, int... gaps) {
        return new TerracePiece(cx, cz, rotation, y, x0, z0, x1, z1, drop, false, gaps, plateau, gate, false, ring);
    }

    /** A tower of the rampart at {@code (x, z)} of its line, {@code r} in radius, {@code top} high over the ground. */
    static TerracePiece tower(int cx, int cz, Rotation rotation, int y, int x, int z, int r, int top, int ring) {
        return new TerracePiece(cx, cz, rotation, y, x, z, r, top, 0, false, new int[0], new int[0], new int[0], true,
                ring);
    }

    /**
     * A stair {@code x0} to {@code x1} wide, from the wall of the terrace above down to the ground {@code y} of the one
     * below, its {@code steps} running from {@code z0} toward the south.
     */
    static TerracePiece stair(int cx, int cz, Rotation rotation, int y, int x0, int x1, int z0, int steps) {
        return new TerracePiece(cx, cz, rotation, y, x0, z0, x1, z0 + steps - 1, steps, true, new int[0], new int[0],
                new int[0], false, y);
    }

    public TerracePiece(CompoundTag tag) {
        super(Towers.CAMPUS_TERRACE_PIECE.get(), tag);
        this.cx = tag.getInt("X");
        this.cz = tag.getInt("Z");
        this.rotation = Rotation.values()[tag.getInt("Rot")];
        this.y = tag.getInt("Y");
        this.x0 = tag.getInt("X0");
        this.z0 = tag.getInt("Z0");
        this.x1 = tag.getInt("X1");
        this.z1 = tag.getInt("Z1");
        this.drop = tag.getInt("Drop");
        this.stair = tag.getBoolean("Stair");
        this.gaps = tag.getIntArray("Gaps");
        this.plateau = tag.getIntArray("Plateau");
        this.gate = tag.getIntArray("Gate");
        this.tower = tag.getBoolean("Tower");
        this.ring = tag.contains("Ring") ? tag.getInt("Ring") : tag.getInt("Y");
    }

    private static BoundingBox box(int cx, int cz, Rotation rotation, int y, int x0, int z0, int x1, int z1, int drop,
                                   boolean stair, int foot) {
        int[] a = Masonry.turn(rotation, x0 - (stair ? 1 : 0), z0);
        int[] b = Masonry.turn(rotation, x1 + (stair ? 1 : 0), z1);
        return new BoundingBox(cx + Math.min(a[0], b[0]), y - foot, cz + Math.min(a[1], b[1]), cx + Math.max(a[0], b[0]),
                y + (stair ? drop + 3 : 2), cz + Math.max(a[1], b[1]));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("X", cx);
        tag.putInt("Z", cz);
        tag.putInt("Rot", rotation.ordinal());
        tag.putInt("Y", y);
        tag.putInt("X0", x0);
        tag.putInt("Z0", z0);
        tag.putInt("X1", x1);
        tag.putInt("Z1", z1);
        tag.putInt("Drop", drop);
        tag.putBoolean("Stair", stair);
        tag.putIntArray("Gaps", gaps);
        tag.putIntArray("Plateau", plateau);
        tag.putIntArray("Gate", gate);
        tag.putBoolean("Tower", tower);
        tag.putInt("Ring", ring);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        Masonry m = new Masonry(level, chunk, cx, y, cz, rotation);
        if (tower) {
            rampartTower(m, chunk);
        } else if (stair) {
            stairs(m);
        } else {
            platform(level, m, chunk);
        }
    }

    /** The ground: mended under, cleared over, grass on top; the retaining wall on the z1 edge if it falls. */
    private void platform(WorldGenLevel level, Masonry m, BoundingBox chunk) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                BlockPos at = m.at(x, 0, z);
                if (!chunk.isInside(at)) {
                    continue;
                }
                // Solid all through: earth, then stone, down to the escarp's foot whatever the land left there
                // (a crust over a hollow is no ground), and on down to firm ground.
                for (int below = -1; below >= -MEND && (below >= -ESCARP || !m.solid(x, below, z)); below--) {
                    m.set(x, below, z, below >= -3 ? Blocks.DIRT.defaultBlockState() : m.stone(x, below, z));
                }
                m.set(x, 0, z, Blocks.GRASS_BLOCK.defaultBlockState());
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, at.getX(), at.getZ()) - y;
                for (int above = 1; above <= Math.min(CLEAR, top); above++) {
                    m.set(x, above, z, air);
                }
            }
        }
        rampart(m, chunk);
        gateArch(m);
        if (drop <= 0) {
            return;
        }
        // The retaining wall: stone from the foot of the terrace below up to this one's ground, a band on top, a
        // parapet over it but where a stair or an avenue goes down.
        for (int x = x0; x <= x1; x++) {
            if (band(x, z1) < ESCARP_DEPTH) {
                continue;
            }
            m.set(x, -drop - 1, z1, Blocks.COBBLESTONE.defaultBlockState());
            for (int h = -drop; h < 0; h++) {
                m.set(x, h, z1, h == -drop ? CampusHouse.plinth(m, x, h, z1) : m.stone(x, h, z1));
            }
            m.set(x, 0, z1, Blocks.POLISHED_ANDESITE.defaultBlockState());
            if (!gap(x)) {
                m.set(x, 1, z1, Blocks.STONE_BRICK_WALL.defaultBlockState());
                if (Math.floorMod(x, 12) == 0) {
                    m.set(x, 2, z1, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }

    /** Whether {@code (x, z)} of the campus is on the plateau. */
    private boolean onPlateau(int x, int z) {
        for (int i = 0; i + 3 < plateau.length; i += 4) {
            if (x >= plateau[i] && x <= plateau[i + 2] && z >= plateau[i + 1] && z <= plateau[i + 3]) {
                return true;
            }
        }
        return false;
    }

    /**
     * How far {@code (x, z)} is in from the plateau's edge: 0 on it, up to {@link #ESCARP_DEPTH} (and no more) inside.
     */
    private int band(int x, int z) {
        for (int k = 0; k < ESCARP_DEPTH; k++) {
            if (!onPlateau(x - k - 1, z) || !onPlateau(x + k + 1, z) || !onPlateau(x, z - k - 1)
                    || !onPlateau(x, z + k + 1)) {
                return k;
            }
        }
        return ESCARP_DEPTH;
    }

    /**
     * The fortified edge of the plateau, a bastion's: an escarp of stone sloping from its foot on the land up to the
     * terrace's ground, three blocks a step, from firm ground; on its top, the rampart: a parapet with battlements and
     * a lantern now and then. The land round it is shaped to the foot (the box reaches down to it). Open at the gate.
     */
    private void rampart(Masonry m, BoundingBox chunk) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int k = band(x, z);
                if (k >= ESCARP_DEPTH || !chunk.isInside(m.at(x, 0, z))
                        || x >= gate[0] && x <= gate[2] && z >= gate[1] && z <= gate[3]) {
                    continue;
                }
                int base = Math.min(0, ring - y);
                if (x >= gate[0] && x <= gate[2] && (z == gate[1] - 1 || z == gate[3] + 1)) {
                    // The gate's jambs: stone from the foot up past the parapet.
                    for (int h = 3; h >= -MEND && (h >= base - ESCARP || !m.solid(x, h, z)); h--) {
                        m.set(x, h, z, m.stone(x, h, z));
                    }
                    m.set(x, 4, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    continue;
                }
                int top = k == ESCARP_DEPTH - 1 ? 0 : base - ESCARP + (ESCARP / ESCARP_DEPTH) * (k + 1);
                for (int h = top; h >= -MEND && (h >= top - 1 || !m.solid(x, h, z) || m.get(x, h, z).is(Blocks.DIRT)
                        || m.get(x, h, z).is(Blocks.GRASS_BLOCK)); h--) {
                    m.set(x, h, z, m.stone(x, h, z));
                }
                m.set(x, base - ESCARP, z, CampusHouse.plinth(m, x, base - ESCARP, z));
                for (int h = top + 1; h <= CLEAR; h++) {
                    m.set(x, h, z, air);
                }
                if (k < ESCARP_DEPTH - 1) {
                    // The slope: a step faced in toward the plateau.
                    m.set(x, top + 1, z, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, inward(x, z, k)));
                    continue;
                }
                // The rampart on the escarp's top.
                m.set(x, 0, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                m.set(x, 1, z, Blocks.STONE_BRICKS.defaultBlockState());
                m.set(x, 2, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                if (Math.floorMod(x + z, 16) == 0) {
                    m.set(x, 3, z, Blocks.LANTERN.defaultBlockState());
                } else if (Math.floorMod(x + z, 2) == 0) {
                    m.set(x, 3, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
            }
        }
    }

    /** The arch over the gate, on the escarp's outer face: two courses of stone, a keystone, battlements. */
    private void gateArch(Masonry m) {
        int x = gate[2];
        if (x < x0 || x > x1 || gate[1] < z0 || gate[3] > z1) {
            return;
        }
        for (int z = gate[1]; z <= gate[3]; z++) {
            m.set(x, 4, z, Blocks.STONE_BRICKS.defaultBlockState());
            m.set(x, 5, z, z == (gate[1] + gate[3]) / 2 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                    : Blocks.POLISHED_ANDESITE.defaultBlockState());
            if (Math.floorMod(z, 2) == 0) {
                m.set(x, 6, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            }
        }
        m.set(x - 1, 4, gate[1], Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
        m.set(x - 1, 4, gate[3], Masonry.stairsUpsideDown(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
        m.set(x - 1, 3, (gate[1] + gate[3]) / 2, Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        m.set(x - 1, 4, (gate[1] + gate[3]) / 2, Blocks.CHAIN.defaultBlockState());
    }

    /** The way in toward the plateau from {@code (x, z)}, {@code k} in from its edge. */
    private net.minecraft.core.Direction inward(int x, int z, int k) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (!onPlateau(x - d.getStepX() * (k + 1), z - d.getStepZ() * (k + 1))) {
                return d;
            }
        }
        return net.minecraft.core.Direction.NORTH;
    }

    /** A tower of the rampart: its foundation of stone down to firm ground, then the house's tower over it. */
    private void rampartTower(Masonry m, BoundingBox chunk) {
        int r = x1;
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                if (dx * dx + dz * dz > (r + 1.5D) * (r + 1.5D) || !chunk.isInside(m.at(x0 + dx, 0, z0 + dz))) {
                    continue;
                }
                int foot = Math.min(0, ring - y) - ESCARP;
                for (int below = -1; below >= -MEND && (below >= foot || !m.solid(x0 + dx, below, z0 + dz));
                     below--) {
                    m.set(x0 + dx, below, z0 + dz, below == foot ? CampusHouse.plinth(m, x0 + dx, below, z0 + dz)
                            : m.stone(x0 + dx, below, z0 + dz));
                }
            }
        }
        CampusBuildings.wallTower(m, x0, z0, r, z1, null);
    }

    private boolean gap(int x) {
        for (int i = 0; i + 1 < gaps.length; i += 2) {
            if (x >= gaps[i] && x <= gaps[i + 1]) {
                return true;
            }
        }
        return false;
    }

    /** The stair down from the wall: a step a block, on stone, walled at its sides, lit at its foot. */
    private void stairs(Masonry m) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int k = 0; k < drop; k++) {
            int z = z0 + k;
            int top = drop - 1 - k;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                boolean side = x < x0 || x > x1;
                if (top > 0) {
                    m.fill(x, 0, z, x, top - 1, z, Blocks.STONE_BRICKS.defaultBlockState());
                }
                if (side) {
                    m.set(x, top, z, Blocks.STONE_BRICKS.defaultBlockState());
                    m.set(x, top + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    m.fill(x, top + 2, z, x, top + 4, z, air);
                } else {
                    m.set(x, top, z, Masonry.stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
                    m.fill(x, top + 1, z, x, top + 4, z, air);
                }
            }
        }
        m.set(x0 - 1, 1, z1 + 1, Blocks.LANTERN.defaultBlockState());
        m.set(x1 + 1, 1, z1 + 1, Blocks.LANTERN.defaultBlockState());
    }
}
