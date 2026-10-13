package com.elderlexicon.mod.university;

import com.elderlexicon.mod.tower.Towers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A university (docs/mago-design.md, 4 and 5g): one piece, drawn by code, laid at the height of the land under it; the
 * terrain is shaped to the piece as the world is made ({@code "terrain_adaptation": "beard_box"}).
 */
public class UniversityStructure extends Structure {

    /** The university, as the world's structures name it. */
    public static final net.minecraft.resources.ResourceKey<Structure> KEY = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.STRUCTURE,
            new net.minecraft.resources.ResourceLocation(com.elderlexicon.mod.ElderLexicon.MODID, "university"));

    public static final Codec<UniversityStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance)).apply(instance, UniversityStructure::new));

    public UniversityStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        BlockPos center = context.chunkPos().getMiddleBlockPosition(ground(context));
        return Optional.of(new GenerationStub(center, pieces -> {
            pieces.addPiece(new UniversityPiece(center));
            pieces.addPiece(new UniversityRoadPiece(center));
        }));
    }

    /**
     * The height the grounds are laid at: the middle of the land under them (sampled on a grid, the lower half of it,
     * so the terrain shaped to them is more often raised than cut), never below the sea.
     */
    private static int ground(GenerationContext context) {
        int x0 = context.chunkPos().getMiddleBlockX();
        int z0 = context.chunkPos().getMiddleBlockZ();
        List<Integer> heights = new ArrayList<>();
        int step = UniversityPiece.WALL / 3;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                heights.add(context.chunkGenerator().getFirstOccupiedHeight(x0 + dx * step, z0 + dz * step,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()));
            }
        }
        Collections.sort(heights);
        int middle = heights.get(heights.size() * 2 / 5);
        return Math.max(context.chunkGenerator().getSeaLevel() + 1, middle);
    }

    @Override
    public StructureType<?> type() {
        return Towers.UNIVERSITY.get();
    }
}
