package com.elderlexicon.mod.university;

import com.elderlexicon.mod.tower.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Locale;

/**
 * A building of the campus (docs/universidade-arquitetura-design.md): its own piece, at the height of the ground under
 * it, so the land is shaped to each house and not to one box. Every building is drawn in the campus's own offsets from
 * its middle, turned as the campus is turned.
 */
public class CampusPiece extends StructurePiece {

    /**
     * The buildings, each with where it stands in the campus (its least and greatest corners, as offsets from the
     * middle) and how high it reaches.
     */
    public enum Kind {
        NUCLEO(-66, -76, 60, -10, 72),
        PATIO(-17, -11, 17, 11, 8),
        AULAS(-37, 12, 37, 63, 58),
        DORMITORIOS(-64, 27, -42, 52, 68),
        REFEITORIO(-66, -13, -42, 18, 44),
        OFICINA(41, 15, 64, 39, 42),
        ENFERMARIA(41, 40, 62, 59, 36),
        ARCO(59, -10, 69, 10, 30),
        CAMPO(-38, 66, 38, 122, 40),
        ARENA(-72, 70, -38, 106, 36),
        LABORATORIO(40, 66, 66, 90, 34),
        ESTABULOS(40, 92, 74, 124, 22),
        AUDITORIO(18, -114, 58, -77, 46);

        final int x0;
        final int z0;
        final int x1;
        final int z1;
        final int top;

        Kind(int x0, int z0, int x1, int z1, int top) {
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.top = top;
        }

        /** The middle of its ground, as an offset from the campus's. */
        int middleX() {
            return (x0 + x1) / 2;
        }

        int middleZ() {
            return (z0 + z1) / 2;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Kind kind;
    private final int cx;
    private final int y0;
    private final int cz;
    private final Rotation rotation;

    public CampusPiece(Kind kind, int cx, int y0, int cz, Rotation rotation) {
        super(Towers.CAMPUS_PIECE.get(), 0, box(kind, cx, y0, cz, rotation));
        this.kind = kind;
        this.cx = cx;
        this.y0 = y0;
        this.cz = cz;
        this.rotation = rotation;
    }

    public CampusPiece(CompoundTag tag) {
        super(Towers.CAMPUS_PIECE.get(), tag);
        this.kind = Kind.valueOf(tag.getString("Kind"));
        this.cx = tag.getInt("X");
        this.y0 = tag.getInt("Y");
        this.cz = tag.getInt("Z");
        this.rotation = Rotation.values()[tag.getInt("Rot")];
    }

    private static BoundingBox box(Kind kind, int cx, int y0, int cz, Rotation rotation) {
        int[] a = Masonry.turn(rotation, kind.x0, kind.z0);
        int[] b = Masonry.turn(rotation, kind.x1, kind.z1);
        return new BoundingBox(cx + Math.min(a[0], b[0]), y0, cz + Math.min(a[1], b[1]), cx + Math.max(a[0], b[0]),
                y0 + kind.top, cz + Math.max(a[1], b[1]));
    }

    public Kind kind() {
        return kind;
    }

    /** Where the campus's middle is, at this building's ground. */
    public BlockPos campusMiddle() {
        return new BlockPos(cx, y0, cz);
    }

    /** Where {@code (x, y, z)} of the campus is, at this building's ground. */
    public BlockPos at(int x, int y, int z) {
        int[] turned = Masonry.turn(rotation, x, z);
        return new BlockPos(cx + turned[0], y0 + y, cz + turned[1]);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Kind", kind.name());
        tag.putInt("X", cx);
        tag.putInt("Y", y0);
        tag.putInt("Z", cz);
        tag.putInt("Rot", rotation.ordinal());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        Masonry m = new Masonry(level, chunk, cx, y0, cz, rotation);
        switch (kind) {
            case NUCLEO -> CampusBuildings.nucleo(m);
            case PATIO -> CampusBuildings.patio(m);
            case AULAS -> CampusBuildings.aulas(m);
            case DORMITORIOS -> CampusBuildings.dormitorios(m);
            case REFEITORIO -> CampusBuildings.refeitorio(m);
            case OFICINA -> CampusBuildings.oficina(m);
            case ENFERMARIA -> CampusBuildings.enfermaria(m);
            case ARCO -> CampusBuildings.arco(m);
            case CAMPO -> CampusBuildings.campo(m);
            case ARENA -> CampusBuildings.arena(m);
            case LABORATORIO -> CampusBuildings.laboratorio(m);
            case ESTABULOS -> CampusBuildings.estabulos(m);
            case AUDITORIO -> CampusBuildings.auditorio(m);
        }
        m.join();
        CampusBuildings.people(m, kind);
    }
}
