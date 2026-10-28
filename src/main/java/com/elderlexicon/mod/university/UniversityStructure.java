package com.elderlexicon.mod.university;

import com.elderlexicon.mod.tower.Towers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A university (docs/mago-design.md, 4 and 5g; docs/universidade-arquitetura-design.md): a campus and the village at its
 * gate. The campus is planned when the structure begins: its walled core on the highest of four ways it could lie,
 * every building a piece of its own at the height of the ground under it ({@link CampusPiece}), the avenues as
 * stretches that follow the land ({@link CampusRoadPiece}); the village is the game's own, of the biome, built out of
 * the arch. The terrain is shaped to each piece ({@code "terrain_adaptation": "beard_thin"}), as for villages.
 * <p>
 * The game builds a structure's pieces only within 8 chunks of where it begins: the structure begins between the campus
 * and the village, the campus on one side and the village on the other.
 */
public class UniversityStructure extends Structure {

    /** The university, as the world's structures name it. */
    public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation(com.elderlexicon.mod.ElderLexicon.MODID, "university"));

    public static final Codec<UniversityStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance)).apply(instance, UniversityStructure::new));

    /** Where the campus's middle is from where the structure begins, toward the core's side; where the village is. */
    private static final int CAMPUS_BACK = 56;
    private static final int VILLAGE_AHEAD = 72;
    /** How far the village's houses may go from its well, and how many joints deep it grows. */
    private static final int VILLAGE_REACH = 56;
    private static final int VILLAGE_DEPTH = 6;
    /** The longest a stretch of avenue runs before the land is sampled again. */
    private static final int STRETCH = 12;

    public UniversityStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int sx = context.chunkPos().getMiddleBlockX();
        int sz = context.chunkPos().getMiddleBlockZ();
        // The way it lies: the one of four that puts the walled core highest.
        Rotation best = Rotation.NONE;
        int highest = Integer.MIN_VALUE;
        for (Rotation rotation : Rotation.values()) {
            int[] core = world(sx, sz, rotation, 0, -40);
            int height = height(context, core[0], core[1]);
            if (height > highest) {
                highest = height;
                best = rotation;
            }
        }
        Rotation rotation = best;
        int[] middle = Masonry.turn(rotation, -CAMPUS_BACK, 0);
        int cx = sx + middle[0];
        int cz = sz + middle[1];
        int sea = context.chunkGenerator().getSeaLevel() + 1;
        List<StructurePiece> pieces = new ArrayList<>();
        java.util.Map<CampusPiece.Kind, Integer> grounds = new java.util.EnumMap<>(CampusPiece.Kind.class);
        for (CampusPiece.Kind kind : CampusPiece.Kind.values()) {
            grounds.put(kind, ground(context, cx, cz, rotation, kind, sea));
        }
        // The teaching building stands two blocks from the courtyard, its door on it: at the courtyard's height, or the
        // short way between them would be a wall.
        grounds.put(CampusPiece.Kind.AULAS, grounds.get(CampusPiece.Kind.PATIO));
        for (CampusPiece.Kind kind : CampusPiece.Kind.values()) {
            pieces.add(new CampusPiece(kind, cx, grounds.get(kind), cz, rotation));
        }
        // The avenues: to the gate of the core, to the teaching building, east to the arch and on toward the
        // village, west to the refectory, the ring round the teaching building, and a way to every door on it.
        int[][][] avenues = {
                {{0, -12}, {0, -11}}, {{0, 11}, {0, 12}},
                {{17, 0}, {59, 0}}, {{69, 0}, {76, 0}},
                {{-17, 0}, {-44, 0}},
                {{-38, 0}, {-38, 64}}, {{-38, 64}, {38, 64}}, {{38, 64}, {38, 0}},
                {{-38, 38}, {-43, 38}}, {{38, 28}, {41, 28}}, {{38, 49}, {41, 49}},
                {{0, 64}, {0, 66}}, {{-38, 64}, {-38, 88}}, {{-38, 88}, {-40, 88}},
                {{38, 64}, {38, 106}}, {{38, 76}, {40, 76}}, {{38, 106}, {44, 106}}};
        for (int[][] avenue : avenues) {
            int half = avenue[0][1] == 0 && avenue[1][1] == 0 && Math.abs(avenue[0][0]) >= 17 ? 2 : 1;
            stretches(context, pieces, grounds, cx, cz, rotation, avenue[0], avenue[1], half, sea);
        }
        int[] well = world(sx, sz, rotation, VILLAGE_AHEAD, 0);
        Optional<GenerationStub> village = village(context, new BlockPos(well[0], 0, well[1]));
        return Optional.of(new GenerationStub(new BlockPos(sx, highest, sz), builder -> {
            pieces.forEach(builder::addPiece);
            village.ifPresent(stub -> stub.getPiecesBuilder().build().pieces().forEach(builder::addPiece));
        }));
    }

    /** The world's {@code (x, z)} of the offset {@code (dx, dz)} from where the structure begins, as it lies. */
    private static int[] world(int sx, int sz, Rotation rotation, int dx, int dz) {
        int[] turned = Masonry.turn(rotation, dx, dz);
        return new int[]{sx + turned[0], sz + turned[1]};
    }

    private static int height(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
    }

    /**
     * The height a building is laid at: the land under it, sampled on a grid, a little below the middle of it (so the
     * land is more often raised to it than cut), never below the sea.
     */
    private static int ground(GenerationContext context, int cx, int cz, Rotation rotation, CampusPiece.Kind kind,
                              int sea) {
        List<Integer> heights = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 4; j++) {
                int x = kind.x0 + (kind.x1 - kind.x0) * i / 4;
                int z = kind.z0 + (kind.z1 - kind.z0) * j / 4;
                int[] turned = Masonry.turn(rotation, x, z);
                heights.add(height(context, cx + turned[0], cz + turned[1]));
            }
        }
        Collections.sort(heights);
        return Math.max(sea, heights.get(heights.size() * 2 / 5));
    }

    /**
     * An avenue from {@code a} to {@code b} (straight, along x or z), as stretches that follow the land. An end that
     * reaches a building is laid at that building's ground, so the avenue meets its door level; between the ends no
     * stretch rises more than one block in two, the land cut or raised to it, worked from both ends.
     */
    private static void stretches(GenerationContext context, List<StructurePiece> pieces,
                                  java.util.Map<CampusPiece.Kind, Integer> grounds, int cx, int cz, Rotation rotation,
                                  int[] a, int[] b, int half, int sea) {
        boolean alongX = a[1] == b[1];
        int from = alongX ? a[0] : a[1];
        int to = alongX ? b[0] : b[1];
        int step = to >= from ? STRETCH : -STRETCH;
        List<Integer> at = new ArrayList<>();
        for (int p = from; ; p += step) {
            if (Math.abs(to - p) <= STRETCH) {
                at.add(p);
                if (p != to) {
                    at.add(to);
                }
                break;
            }
            at.add(p);
        }
        int n = at.size();
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            y[i] = landAt(context, cx, cz, rotation, alongX ? at.get(i) : a[0], alongX ? a[1] : at.get(i), sea);
        }
        Integer first = groundOf(grounds, a[0], a[1]);
        Integer last = groundOf(grounds, b[0], b[1]);
        if (first != null) {
            y[0] = first;
        }
        if (last != null) {
            y[n - 1] = last;
        }
        for (int i = 1; i < n; i++) {
            if (i < n - 1 || last == null) {
                int run = Math.abs(at.get(i) - at.get(i - 1));
                y[i] = Math.max(y[i - 1] - run / 2, Math.min(y[i - 1] + run / 2, y[i]));
            }
        }
        for (int i = n - 2; i >= 0; i--) {
            if (i > 0 || first == null) {
                int run = Math.abs(at.get(i + 1) - at.get(i));
                y[i] = Math.max(y[i + 1] - run / 2, Math.min(y[i + 1] + run / 2, y[i]));
            }
        }
        for (int i = 0; i + 1 < n; i++) {
            int p = at.get(i);
            int q = at.get(i + 1);
            pieces.add(alongX ? new CampusRoadPiece(cx, cz, rotation, p, a[1], y[i], q, a[1], y[i + 1], half)
                    : new CampusRoadPiece(cx, cz, rotation, a[0], p, y[i], a[0], q, y[i + 1], half));
        }
    }

    /**
     * The ground of the building whose walls {@code (x, z)} of the campus is within a block of, the smallest if more
     * than one; null if none.
     */
    private static Integer groundOf(java.util.Map<CampusPiece.Kind, Integer> grounds, int x, int z) {
        CampusPiece.Kind best = null;
        for (CampusPiece.Kind kind : CampusPiece.Kind.values()) {
            boolean near = x >= kind.x0 - 1 && x <= kind.x1 + 1 && z >= kind.z0 - 1 && z <= kind.z1 + 1;
            if (near && (best == null
                    || (kind.x1 - kind.x0) * (kind.z1 - kind.z0) < (best.x1 - best.x0) * (best.z1 - best.z0))) {
                best = kind;
            }
        }
        return best == null ? null : grounds.get(best);
    }

    private static int landAt(GenerationContext context, int cx, int cz, Rotation rotation, int x, int z, int sea) {
        int[] turned = Masonry.turn(rotation, x, z);
        return Math.max(sea, height(context, cx + turned[0], cz + turned[1]));
    }

    /** The village at the gate: the game's own, of the biome where its well falls. */
    private static Optional<GenerationStub> village(GenerationContext context, BlockPos well) {
        Holder<Biome> biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(well.getX()),
                QuartPos.fromBlock(height(context, well.getX(), well.getZ())), QuartPos.fromBlock(well.getZ()),
                context.randomState().sampler());
        String kind = biome.is(BiomeTags.HAS_VILLAGE_DESERT) ? "desert"
                : biome.is(BiomeTags.HAS_VILLAGE_SAVANNA) ? "savanna"
                : biome.is(BiomeTags.HAS_VILLAGE_SNOWY) ? "snowy"
                : biome.is(BiomeTags.HAS_VILLAGE_TAIGA) ? "taiga" : "plains";
        Optional<Holder.Reference<StructureTemplatePool>> pool = context.registryAccess()
                .registryOrThrow(Registries.TEMPLATE_POOL).getHolder(ResourceKey.create(Registries.TEMPLATE_POOL,
                        new ResourceLocation("minecraft", "village/" + kind + "/town_centers")));
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return JigsawPlacement.addPieces(context, pool.get(), Optional.empty(), VILLAGE_DEPTH, well, false,
                Optional.of(Heightmap.Types.WORLD_SURFACE_WG), VILLAGE_REACH);
    }

    /**
     * Where the courtyard of the university that began at {@code start} is (the Emissary brings the player there):
     * before the core's gate, at the fountain. Empty for a university of the old kind, one piece.
     */
    public static Optional<BlockPos> courtyard(ServerLevel level, BlockPos start) {
        Structure university = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(KEY);
        if (university == null) {
            return Optional.empty();
        }
        ChunkAccess chunk = level.getChunk(start.getX() >> 4, start.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
        StructureStart found = chunk.getStartForStructure(university);
        if (found == null || !found.isValid()) {
            return Optional.empty();
        }
        for (StructurePiece piece : found.getPieces()) {
            if (piece instanceof CampusPiece campus && campus.kind() == CampusPiece.Kind.PATIO) {
                return Optional.of(campus.at(0, 0, 6));
            }
        }
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return Towers.UNIVERSITY.get();
    }
}
