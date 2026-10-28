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
     * The layout of the axis (docs/universidade-arquitetura-design.md, 5.5): the terraces the campus lies on, each a
     * rectangle of the campus {x0, z0, x1, z1, level}. Level 0 is the plateau's ground: the gate court east, the fields
     * south; level 1, six over it: the square of learning on the axis, the teaching building, the refectory, the
     * dormitories and the auditorium north of it, the walk under the acropolis's wall; level 2, twelve over: the
     * acropolis west, the Great Hall at the axis's end.
     */
    private static final int[][] TERRACES = {
            {34, -40, 72, 48, 0}, {-70, 49, 72, 127, 0}, {-11, 44, 33, 48, 0},
            {-11, -48, 33, 43, 1}, {-70, -116, 72, -49, 1}, {34, -48, 72, -41, 1}, {-70, -48, -12, -45, 1},
            {-70, -44, -12, 48, 2}};
    private static final int LEVELS = 3;
    /** How high each level stands over the one below it. */
    private static final int RISE = 6;
    /** How far the plateau stands over most of the land under it. */
    private static final int PLATEAU = 9;
    /**
     * The stairs between the levels, on the axis {x, z, level below}: their top step's middle, each running east, nine
     * wide; where the terrace above opens its parapet for it.
     */
    private static final int[][] STAIRS = {{34, 0, 0}, {-11, 0, 1}};
    private static final int[] OPENINGS = {33, -4, 33, 4, -12, -4, -12, 4};
    /**
     * The towers of the rampart, on its line {x, z, r}: at its corners and along it, one where the escarp steps from
     * a level to another. None where a tower of the campus stands close inside and would fuse with it.
     */
    private static final int[][] RAMPART_TOWERS = {
            {-70, -116, 4}, {-26, -116, 4}, {16, -116, 4}, {72, -116, 4}, {72, -90, 4}, {72, -56, 4}, {72, -41, 4},
            {72, -28, 4}, {72, 30, 4}, {72, 52, 4}, {72, 66, 4}, {72, 82, 4}, {72, 127, 4},
            {54, 127, 4}, {24, 127, 4}, {-20, 127, 4}, {-70, 127, 4}, {-70, 108, 4}, {-70, 66, 4}, {-70, 48, 4},
            {-70, 20, 4}, {-70, -10, 3}, {-70, -44, 4}, {-70, -56, 4}, {-70, -90, 4}};
    /** Where the rampart opens: the gate, east, out of the arch onto the way down to the village. */
    private static final int[] GATE = {70, -3, 72, 3};

    /** The buildings of the layout of the axis, each where it stands and how it is turned. */
    private static final java.util.Map<CampusPiece.Kind, CampusPiece.Placement> LAYOUT = layout();

    private static java.util.Map<CampusPiece.Kind, CampusPiece.Placement> layout() {
        java.util.Map<CampusPiece.Kind, CampusPiece.Placement> at = new java.util.EnumMap<>(CampusPiece.Kind.class);
        CampusPiece.Placement home = CampusPiece.Placement.HOME;
        // The gate court: the fountain turned so its way across runs along the axis; the workshop north of it and the
        // infirmary south, each turned to open onto it.
        at.put(CampusPiece.Kind.PATIO, new CampusPiece.Placement(0, 0, 50, 0, Rotation.CLOCKWISE_90));
        at.put(CampusPiece.Kind.ARCO, home);
        at.put(CampusPiece.Kind.OFICINA, new CampusPiece.Placement(50, 28, 50, -27, Rotation.COUNTERCLOCKWISE_90));
        at.put(CampusPiece.Kind.ENFERMARIA, new CampusPiece.Placement(50, 49, 50, 27, Rotation.CLOCKWISE_90));
        // The square of learning: the teaching building north of it, its bell tower toward it; the library south.
        at.put(CampusPiece.Kind.PRACA, home);
        at.put(CampusPiece.Kind.AULAS, new CampusPiece.Placement(0, 30, 11, -58, Rotation.CLOCKWISE_180));
        at.put(CampusPiece.Kind.BIBLIOTECA,
                new CampusPiece.Placement(-44, -44, 11, 31, Rotation.COUNTERCLOCKWISE_90));
        // North, at the square's level: the refectory and the dormitories west, the auditorium east.
        at.put(CampusPiece.Kind.REFEITORIO, new CampusPiece.Placement(-54, 0, -48, -68, Rotation.NONE));
        at.put(CampusPiece.Kind.DORMITORIOS, new CampusPiece.Placement(-53, 38, -52, -97, Rotation.NONE));
        at.put(CampusPiece.Kind.AUDITORIO, new CampusPiece.Placement(40, -96, 44, -94, Rotation.NONE));
        // The acropolis: the Great Hall facing the axis, the research tower north-west of it.
        at.put(CampusPiece.Kind.ACROPOLE, home);
        at.put(CampusPiece.Kind.REITORIA, new CampusPiece.Placement(0, -44, -46, 0, Rotation.COUNTERCLOCKWISE_90));
        at.put(CampusPiece.Kind.PESQUISA, new CampusPiece.Placement(42, -48, -50, -26, Rotation.COUNTERCLOCKWISE_90));
        // The fields, where they were.
        at.put(CampusPiece.Kind.CAMPO, home);
        at.put(CampusPiece.Kind.ARENA, home);
        at.put(CampusPiece.Kind.LABORATORIO, home);
        at.put(CampusPiece.Kind.ESTABULOS, home);
        return at;
    }

    /** How rough the land may be for a university to be laid on it: past this, it is not laid there. */
    private static final double ROUGHEST = 18.0D;

    /** The level a building stands on. */
    static int terraceOf(CampusPiece.Kind kind) {
        return switch (kind) {
            case ACROPOLE, REITORIA, PESQUISA -> 2;
            case PRACA, AULAS, BIBLIOTECA, REFEITORIO, DORMITORIOS, AUDITORIO, NUCLEO -> 1;
            default -> 0;
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
        // The terraces first, so every building is drawn on its ground; each with a retaining wall where it looks out
        // onto a lower one; round them all, the rampart, and its towers.
        int[] plateau = new int[TERRACES.length * 4];
        int[] levels = new int[TERRACES.length * 5];
        for (int i = 0; i < TERRACES.length; i++) {
            int[] r = TERRACES[i];
            System.arraycopy(r, 0, plateau, i * 4, 4);
            System.arraycopy(r, 0, levels, i * 5, 4);
            levels[i * 5 + 4] = terrace[r[4]];
        }
        for (int[] r : TERRACES) {
            pieces.add(TerracePiece.platform(cx, cz, rotation, terrace[r[4]], r[0], r[1], r[2], r[3], plateau, GATE,
                    terrace[0], levels, OPENINGS));
        }
        for (int[] at : RAMPART_TOWERS) {
            Integer y = terraceAt(terrace, at[0], at[1]);
            pieces.add(TerracePiece.tower(cx, cz, rotation, y == null ? terrace[0] : y, at[0], at[1], at[2], 14,
                    terrace[0]));
        }
        for (java.util.Map.Entry<CampusPiece.Kind, CampusPiece.Placement> building : LAYOUT.entrySet()) {
            pieces.add(new CampusPiece(building.getKey(), cx, terrace[terraceOf(building.getKey())], cz, rotation,
                    building.getValue()));
        }
        // The stairs up the axis, after the buildings, so nothing of theirs covers them.
        for (int[] stair : STAIRS) {
            pieces.add(TerracePiece.stair(cx, cz, rotation, terrace[stair[2]], stair[0], stair[1],
                    Rotation.COUNTERCLOCKWISE_90, 4, RISE));
        }
        // The avenues: out of the gate to the village; from the gate court south, down the side, to the ring road of
        // the fields and the ways to their doors. Each takes its ends' heights from the terraces they are on.
        int[][][] avenues = {
                {{60, 0}, {69, 0}}, {{69, 0}, {100, 0}},
                {{62, 12}, {62, 64}}, {{38, 64}, {62, 64}},
                {{-38, 64}, {38, 64}}, {{0, 64}, {0, 66}},
                {{-38, 64}, {-38, 88}}, {{-38, 88}, {-40, 88}},
                {{38, 64}, {38, 106}}, {{38, 76}, {40, 76}}, {{38, 106}, {44, 106}}};
        for (int[][] avenue : avenues) {
            int half = avenue[0][1] == 0 && avenue[1][1] == 0 ? 2 : 1;
            stretches(context, pieces, terrace, cx, cz, rotation, avenue[0], avenue[1], half, sea);
        }
        int[] well = world(sx, sz, rotation, VILLAGE_AHEAD, 0);
        Optional<GenerationStub> village = village(context, new BlockPos(well[0], 0, well[1]));
        return Optional.of(new GenerationStub(new BlockPos(sx, terrace[LEVELS - 1], sz), builder -> {
            pieces.forEach(builder::addPiece);
            village.ifPresent(stub -> stub.getPiecesBuilder().build().pieces().forEach(builder::addPiece));
        }));
    }

    /**
     * How the campus would lie turned by {@code rotation}: its ground (level 0) stands over most of the land under the
     * whole campus (its three quarters, sampled on a grid in each terrace, never below the sea), a plateau; each level
     * a stair's rise over the one below. How rough the land is for it: how far, on the average, the land is from its
     * middle under each level.
     */
    private static Lie lie(GenerationContext context, int sx, int sz, Rotation rotation, int sea) {
        int[] middle = Masonry.turn(rotation, -CAMPUS_BACK, 0);
        int cx = sx + middle[0];
        int cz = sz + middle[1];
        List<Integer> all = new ArrayList<>();
        double roughness = 0.0D;
        for (int level = 0; level < LEVELS; level++) {
            List<Integer> heights = new ArrayList<>();
            for (int[] r : TERRACES) {
                if (r[4] != level) {
                    continue;
                }
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
            double off = 0.0D;
            for (int h : heights) {
                off += Math.abs(h - median);
            }
            roughness += off / heights.size();
            all.addAll(heights);
        }
        Collections.sort(all);
        int ground = all.get(all.size() * 3 / 4) + PLATEAU;
        int[] terrace = new int[LEVELS];
        for (int level = 0; level < LEVELS; level++) {
            terrace[level] = ground + RISE * level;
        }
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
        for (int[] r : TERRACES) {
            if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) {
                return terraces[r[4]];
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
