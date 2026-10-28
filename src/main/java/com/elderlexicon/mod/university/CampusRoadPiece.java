package com.elderlexicon.mod.university;

import com.elderlexicon.mod.tower.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A stretch of an avenue of the campus (docs/universidade-arquitetura-design.md, 5.1): straight, along x or z of the
 * campus, from one height to another, so a long avenue made of many follows the land, a ramp at a time. Paved, its
 * edges grass, lamp posts along the wide ones.
 */
public class CampusRoadPiece extends StructurePiece {

    private final int cx;
    private final int cz;
    private final Rotation rotation;
    /** From and to, as offsets of the campus; the heights at each end (of the world); the half of the width. */
    private final int ax;
    private final int az;
    private final int ay;
    private final int bx;
    private final int bz;
    private final int by;
    private final int half;

    public CampusRoadPiece(int cx, int cz, Rotation rotation, int ax, int az, int ay, int bx, int bz, int by,
                           int half) {
        super(Towers.CAMPUS_ROAD_PIECE.get(), 0, box(cx, cz, rotation, ax, az, ay, bx, bz, by, half));
        this.cx = cx;
        this.cz = cz;
        this.rotation = rotation;
        this.ax = ax;
        this.az = az;
        this.ay = ay;
        this.bx = bx;
        this.bz = bz;
        this.by = by;
        this.half = half;
    }

    public CampusRoadPiece(CompoundTag tag) {
        super(Towers.CAMPUS_ROAD_PIECE.get(), tag);
        this.cx = tag.getInt("X");
        this.cz = tag.getInt("Z");
        this.rotation = Rotation.values()[tag.getInt("Rot")];
        this.ax = tag.getInt("AX");
        this.az = tag.getInt("AZ");
        this.ay = tag.getInt("AY");
        this.bx = tag.getInt("BX");
        this.bz = tag.getInt("BZ");
        this.by = tag.getInt("BY");
        this.half = tag.getInt("Half");
    }

    private static BoundingBox box(int cx, int cz, Rotation rotation, int ax, int az, int ay, int bx, int bz, int by,
                                   int half) {
        boolean alongX = az == bz;
        int x0 = Math.min(ax, bx) - (alongX ? 0 : half);
        int x1 = Math.max(ax, bx) + (alongX ? 0 : half);
        int z0 = Math.min(az, bz) - (alongX ? half : 0);
        int z1 = Math.max(az, bz) + (alongX ? half : 0);
        int[] a = Masonry.turn(rotation, x0, z0);
        int[] b = Masonry.turn(rotation, x1, z1);
        return new BoundingBox(cx + Math.min(a[0], b[0]), Math.min(ay, by) - 1, cz + Math.min(a[1], b[1]),
                cx + Math.max(a[0], b[0]), Math.max(ay, by) + 4, cz + Math.max(a[1], b[1]));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("X", cx);
        tag.putInt("Z", cz);
        tag.putInt("Rot", rotation.ordinal());
        tag.putInt("AX", ax);
        tag.putInt("AZ", az);
        tag.putInt("AY", ay);
        tag.putInt("BX", bx);
        tag.putInt("BZ", bz);
        tag.putInt("BY", by);
        tag.putInt("Half", half);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        Masonry m = new Masonry(level, chunk, cx, 0, cz, rotation);
        boolean alongX = az == bz;
        int from = alongX ? Math.min(ax, bx) : Math.min(az, bz);
        int to = alongX ? Math.max(ax, bx) : Math.max(az, bz);
        int start = alongX ? ax : az;
        int length = Math.max(1, Math.abs((alongX ? bx : bz) - start));
        Direction up = alongX ? Direction.EAST : Direction.SOUTH;
        for (int along = from; along <= to; along++) {
            int y = heightAt(along, start, length);
            // Where the way rises a block from the cell before (or after), the step is a stair, to walk up it.
            Direction step = along > from && y > heightAt(along - 1, start, length) ? up
                    : along < to && y > heightAt(along + 1, start, length) ? up.getOpposite() : null;
            for (int across = -half; across <= half; across++) {
                int x = alongX ? along : ax + across;
                int z = alongX ? az + across : along;
                if (!chunk.isInside(m.at(x, y, z))) {
                    continue;
                }
                // Under it, stone down to firm ground: off the plateau, a causeway, walled at its sides.
                int filled = 0;
                for (int below = y - 1; below >= y - 40 && !m.solid(x, below, z); below--) {
                    m.set(x, below, z, m.stone(x, below, z));
                    filled++;
                }
                boolean edge = Math.abs(across) == half && half >= 2;
                boolean causeway = filled >= 2;
                m.set(x, y, z, step != null && !(edge && causeway) ? Masonry.stairs(Blocks.STONE_BRICK_STAIRS, step)
                        : edge && !causeway ? Blocks.GRASS_BLOCK.defaultBlockState() : paving(m, x, y, z));
                for (int above = 1; above <= 4; above++) {
                    m.set(x, y + above, z, Blocks.AIR.defaultBlockState());
                }
                if (edge && causeway) {
                    m.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                    m.set(x, y + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
            }
            // Lamp posts along the wide avenues, at their edges.
            if (half >= 2 && Math.floorMod(along, 12) == 6) {
                for (int side : new int[]{-half, half}) {
                    int x = alongX ? along : ax + side;
                    int z = alongX ? az + side : along;
                    m.set(x, y + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    m.set(x, y + 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    m.set(x, y + 3, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
        m.join();
    }

    /** The height of the way at {@code along}, between its two ends. */
    private int heightAt(int along, int start, int length) {
        double t = Math.abs(along - start) / (double) length;
        return Mth.floor(Mth.lerp(t, ay, by) + 0.5D);
    }

    private static BlockState paving(Masonry m, int x, int y, int z) {
        int roll = m.chance(x, y, z);
        return (roll < 70 ? Blocks.POLISHED_ANDESITE : roll < 90 ? Blocks.STONE_BRICKS : Blocks.ANDESITE)
                .defaultBlockState();
    }
}
