package com.elderlexicon.mod.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A caster's tower (docs/mago-design.md, 3 and 5e): one piece, drawn by code, on the ground at the middle of its chunk.
 * Whose it is ({@code kind}) is data, so the Sorcerer's and the Wizard's are two structures of one type.
 */
public class TowerStructure extends Structure {

    public static final Codec<TowerStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            TowerKind.CODEC.fieldOf("kind").forGetter(tower -> tower.kind)
    ).apply(instance, TowerStructure::new));

    private final TowerKind kind;

    public TowerStructure(StructureSettings settings, TowerKind kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, pieces -> {
            BlockPos center = context.chunkPos().getMiddleBlockPosition(context.chunkGenerator().getFirstOccupiedHeight(
                    context.chunkPos().getMiddleBlockX(), context.chunkPos().getMiddleBlockZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()));
            pieces.addPiece(new TowerPiece(kind, center, context.random()));
        });
    }

    @Override
    public StructureType<?> type() {
        return Towers.TOWER.get();
    }
}
