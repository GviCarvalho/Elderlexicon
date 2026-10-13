package com.elderlexicon.mod.university;

import com.elderlexicon.mod.tower.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The road out of a university's gate (docs/mago-design.md, 5g): its own piece, so the terrain is shaped to it too, a
 * cut through a hill or a causeway over a dip, and the way in is never shut by the land. Paved, with lamp posts.
 */
public class UniversityRoadPiece extends StructurePiece {

    /** How far the road runs from the gate, and how wide the ground shaped to it is. */
    static final int LENGTH = 40;
    private static final int HALF_WIDTH = 4;
    private static final int CLEAR = 6;

    private final int cx;
    private final int y0;
    private final int cz;

    public UniversityRoadPiece(BlockPos center) {
        super(Towers.UNIVERSITY_ROAD_PIECE.get(), 0, new BoundingBox(center.getX() - HALF_WIDTH, center.getY(),
                center.getZ() + UniversityPiece.REACH + 1, center.getX() + HALF_WIDTH, center.getY() + CLEAR,
                center.getZ() + UniversityPiece.WALL + LENGTH));
        this.cx = center.getX();
        this.y0 = center.getY();
        this.cz = center.getZ();
    }

    public UniversityRoadPiece(CompoundTag tag) {
        super(Towers.UNIVERSITY_ROAD_PIECE.get(), tag);
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
        int from = UniversityPiece.REACH + 1;
        int to = UniversityPiece.WALL + LENGTH;
        for (int z = from; z <= to; z++) {
            for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
                if (!m.chunk.isInside(m.at(x, 0, z))) {
                    continue;
                }
                for (int y = -1; y >= -6 && !m.solid(x, y, z); y--) {
                    m.set(x, y, z, Blocks.DIRT.defaultBlockState());
                }
                m.set(x, 0, z, Math.abs(x) <= 2 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        m.fill(-HALF_WIDTH, 1, from, HALF_WIDTH, CLEAR, to, Blocks.AIR.defaultBlockState());
        for (int z = from + 4; z <= to; z += 10) {
            for (int x : new int[]{-3, 3}) {
                m.set(x, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                m.set(x, 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                m.set(x, 3, z, Blocks.LANTERN.defaultBlockState());
            }
        }
        m.join();
    }
}
