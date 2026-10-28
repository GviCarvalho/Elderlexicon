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
 * gate. The campus is planned when the structure begins: of the four ways it could lie, the one that asks least of the
 * land, and none where the land is too rough; on three level terraces ({@link TerracePiece}), retaining walls and
 * stairs between them, every building a piece of its own at its terrace's ground ({@link CampusPiece}); the avenues as
 * stretches from terrace to terrace ({@link CampusRoadPiece}); the village is the game's own, of the biome, built out of
 * the arch. The land is shaped to the terraces ({@code "terrain_adaptation": "beard_thin"}): it slopes away from them.
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

    /**
     * The terraces the campus lies on (docs/universidade-arquitetura-design.md, 5.3), each a list of rectangles of the
     * campus {x0, z0, x1, z1}: the core, north, with the auditorium behind it; the middle, from the core's wall to the
     * ring road; the fields, south. Every building is on one and at its ground.
     */
    private static final int[][][] TERRACES = {
            {{-70, -116, 72, -10}},
            {{-70, -9, 72, 66}},
            {{-70, 67, 72, 127}}};
    private static final int CORE = 0;
    private static final int MIDDLE = 1;
    private static final int FIELDS = 2;
    /** The core over the middle (at least, at most), the fields under it (at most): a stair's height. */
    private static final int CORE_LEAST = 2;
    private static final int CORE_RISE = 4;
    private static final int FIELDS_FALL = 0;
    /** How far the plateau stands over most of the land under it. */
    private static final int PLATEAU = 9;
    /**
     * The towers of the rampart, on its line {x, z}: at its corners and along it. None where a tower of the campus
     * stands close inside and would fuse with it (the core's corners, the arch, the auditorium, the arena's own).
     */
    private static final int[][] RAMPART_TOWERS = {
            {-70, -116, 4}, {-26, -116, 4}, {16, -116, 4}, {72, -116, 4}, {72, -90, 4}, {72, -56, 4}, {72, -28, 4},
            {72, -8, 3}, {72, 8, 3}, {72, 30, 4}, {72, 52, 4}, {72, 66, 4}, {72, 82, 4}, {72, 127, 4}, {54, 127, 4},
            {24, 127, 4}, {-20, 127, 4}, {-70, 127, 4}, {-70, 108, 4}, {-70, 66, 4}, {-70, 52, 4}, {-70, 20, 4},
            {-70, -10, 3}, {-70, -26, 4}, {-70, -56, 4}, {-70, -90, 4}};
    /** Where the rampart opens: the gate, east, out of the arch onto the way down to the village. */
    private static final int[] GATE = {70, -3, 72, 3};
    /** How rough the land may be for a university to be laid on it: past this, it is not laid there. */
    private static final double ROUGHEST = 18.0D;

    /** The terrace a building is on. */
    static int terraceOf(CampusPiece.Kind kind) {
        return switch (kind) {
            case NUCLEO, AUDITORIO -> CORE;
            case CAMPO, ARENA, LABORATORIO, ESTABULOS -> FIELDS;
            default -> MIDDLE;
        };
    }

    /** A way the campus could lie: its middle, its rotation, the terraces' heights, how rough the land is for it. */
    private record Lie(Rotation rotation, int cx, int cz, int[] heights, double roughness) {
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int sx = context.chunkPos().getMiddleBlockX();
        int sz = context.chunkPos().getMiddleBlockZ();
        int sea = context.chunkGenerator().getSeaLevel() + 1;
        // The way it lies: of the four, the one that asks the least of the land; none, if even that asks too much.
        Lie best = null;
        for (Rotation rotation : Rotation.values()) {
            Lie lie = lie(context, sx, sz, rotation, sea);
            if (best == null || lie.roughness() < best.roughness()) {
                best = lie;
            }
        }
        if (best.roughness() > ROUGHEST) {
            return Optional.empty();
        }
        Rotation rotation = best.rotation();
        int cx = best.cx();
        int cz = best.cz();
        int[] terrace = best.heights();
        List<StructurePiece> pieces = new ArrayList<>();
        // The terraces first, so every building is drawn on its ground; the core's wall falls to the middle, the
        // middle's to the fields, a stair down each where the ways cross; round them all, the rampart, and its towers.
        int coreDrop = terrace[CORE] - terrace[MIDDLE];
        int fieldsDrop = terrace[MIDDLE] - terrace[FIELDS];
        List<Integer> union = new ArrayList<>();
        for (int[][] rects : TERRACES) {
            for (int[] r : rects) {
                for (int v : r) {
                    union.add(v);
                }
            }
        }
        int[] plateau = union.stream().mapToInt(Integer::intValue).toArray();
        for (int t = 0; t < TERRACES.length; t++) {
            for (int[] r : TERRACES[t]) {
                int drop = r[3] == -10 ? coreDrop : r[3] == 66 ? fieldsDrop : 0;
                pieces.add(TerracePiece.platform(cx, cz, rotation, terrace[t], r[0], r[1], r[2], r[3], drop, plateau,
                        GATE, terrace[MIDDLE], -2, 2, -1, 1, -40, -36, 36, 40));
            }
        }
        for (int[] at : RAMPART_TOWERS) {
            Integer y = terraceAt(terrace, at[0], at[1]);
            pieces.add(TerracePiece.tower(cx, cz, rotation, y == null ? terrace[MIDDLE] : y, at[0], at[1], at[2], 14,
                    terrace[MIDDLE]));
        }
        java.util.Map<CampusPiece.Kind, Integer> grounds = new java.util.EnumMap<>(CampusPiece.Kind.class);
        for (CampusPiece.Kind kind : CampusPiece.Kind.values()) {
            grounds.put(kind, terrace[terraceOf(kind)]);
            pieces.add(new CampusPiece(kind, cx, terrace[terraceOf(kind)], cz, rotation));
        }
        if (coreDrop > 0) {
            pieces.add(TerracePiece.stair(cx, cz, rotation, terrace[MIDDLE], -2, 2, -9, coreDrop));
        }
        if (fieldsDrop > 0) {
            pieces.add(TerracePiece.stair(cx, cz, rotation, terrace[FIELDS], -1, 1, 67, fieldsDrop));
        }
        // The avenues: east to the arch and on toward the village, west to the refectory, the ring round the teaching
        // building and down the fields' sides, and a way to every door on it. Each takes its ends' heights from the
        // terraces they are on, so it meets the doors; where it goes from one terrace to another it is a ramp.
        int[][][] avenues = {
                {{0, 11}, {0, 12}},
                {{17, 0}, {59, 0}}, {{69, 0}, {100, 0}},
                {{-17, 0}, {-44, 0}},
                {{-38, 0}, {-38, 64}}, {{-38, 64}, {38, 64}}, {{38, 64}, {38, 0}},
                {{-38, 38}, {-43, 38}}, {{38, 28}, {41, 28}}, {{38, 49}, {41, 49}},
                {{-38, 64}, {-38, 88}}, {{-38, 88}, {-40, 88}},
                {{38, 64}, {38, 106}}, {{38, 76}, {40, 76}}, {{38, 106}, {44, 106}}};
        for (int[][] avenue : avenues) {
            int half = avenue[0][1] == 0 && avenue[1][1] == 0 && Math.abs(avenue[0][0]) >= 17 ? 2 : 1;
            stretches(context, pieces, terrace, cx, cz, rotation, avenue[0], avenue[1], half, sea);
        }
        int[] well = world(sx, sz, rotation, VILLAGE_AHEAD, 0);
        Optional<GenerationStub> village = village(context, new BlockPos(well[0], 0, well[1]));
        return Optional.of(new GenerationStub(new BlockPos(sx, terrace[CORE], sz), builder -> {
            pieces.forEach(builder::addPiece);
            village.ifPresent(stub -> stub.getPiecesBuilder().build().pieces().forEach(builder::addPiece));
        }));
    }

    /**
     * How the campus would lie turned by {@code rotation}: each terrace stands over most of the land under it (its
     * three quarters, sampled on a grid, never below the sea), a plateau; the core a step or two over the middle, the
     * fields no higher than it and at most a stair below; how rough the land is for it is how far, on the average, the
     * land is from its middle under each terrace, and what keeping the terraces a stair apart asks.
     */
    private static Lie lie(GenerationContext context, int sx, int sz, Rotation rotation, int sea) {
        int[] middle = Masonry.turn(rotation, -CAMPUS_BACK, 0);
        int cx = sx + middle[0];
        int cz = sz + middle[1];
        int[] natural = new int[TERRACES.length];
        double roughness = 0.0D;
        for (int t = 0; t < TERRACES.length; t++) {
            List<Integer> heights = new ArrayList<>();
            for (int[] r : TERRACES[t]) {
                for (int i = 0; i <= 4; i++) {
                    for (int j = 0; j <= 4; j++) {
                        int[] turned = Masonry.turn(rotation, r[0] + (r[2] - r[0]) * i / 4, r[1] + (r[3] - r[1]) * j / 4);
                        heights.add(Math.max(sea, height(context, cx + turned[0], cz + turned[1])));
                    }
                }
            }
            List<Integer> sorted = new ArrayList<>(heights);
            Collections.sort(sorted);
            int median = sorted.get(sorted.size() / 2);
            natural[t] = sorted.get(sorted.size() * 3 / 4) + PLATEAU;
            double off = 0.0D;
            for (int h : heights) {
                off += Math.abs(h - median);
            }
            roughness += off / heights.size();
        }
        int[] terrace = new int[TERRACES.length];
        terrace[MIDDLE] = natural[MIDDLE];
        terrace[CORE] = Math.max(terrace[MIDDLE] + CORE_LEAST, Math.min(terrace[MIDDLE] + CORE_RISE, natural[CORE]));
        terrace[FIELDS] = Math.min(terrace[MIDDLE], Math.max(terrace[MIDDLE] - FIELDS_FALL, natural[FIELDS]));
        roughness += 0.5D * (Math.abs(terrace[CORE] - natural[CORE]) + Math.abs(terrace[FIELDS] - natural[FIELDS]));
        return new Lie(rotation, cx, cz, terrace, roughness);
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
     * An avenue from {@code a} to {@code b} (straight, along x or z), as stretches that follow the land. An end that
     * reaches a building is laid at that building's ground, so the avenue meets its door level; between the ends no
     * stretch rises more than one block in two, the land cut or raised to it, worked from both ends.
     */
    private static void stretches(GenerationContext context, List<StructurePiece> pieces,
                                  int[] terraces, int cx, int cz, Rotation rotation,
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
            int px = alongX ? at.get(i) : a[0];
            int pz = alongX ? a[1] : at.get(i);
            Integer on = terraceAt(terraces, px, pz);
            y[i] = on != null ? on : landAt(context, cx, cz, rotation, px, pz, sea);
        }
        Integer first = terraceAt(terraces, a[0], a[1]);
        Integer last = terraceAt(terraces, b[0], b[1]);
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

    /** The height of the terrace {@code (x, z)} of the campus is on; null off them (the land's, then). */
    private static Integer terraceAt(int[] terraces, int x, int z) {
        for (int t = 0; t < TERRACES.length; t++) {
            for (int[] r : TERRACES[t]) {
                if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) {
                    return terraces[t];
                }
            }
        }
        return null;
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
