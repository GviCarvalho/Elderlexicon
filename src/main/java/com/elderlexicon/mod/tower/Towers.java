package com.elderlexicon.mod.tower;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The towers of the casters (docs/mago-design.md, 3 and 5e): one structure type for both, drawn by code, and its piece.
 * Where they stand (biomes, spacing) is data: {@code worldgen/structure} and {@code worldgen/structure_set}.
 */
public final class Towers {

    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE,
            ElderLexicon.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(
            Registries.STRUCTURE_PIECE, ElderLexicon.MODID);

    public static final RegistryObject<StructureType<TowerStructure>> TOWER = TYPES.register("caster_tower",
            () -> () -> TowerStructure.CODEC);
    public static final RegistryObject<StructurePieceType> TOWER_PIECE = PIECES.register("caster_tower",
            () -> (context, tag) -> new TowerPiece(tag));
    /** A university (docs/mago-design.md, 4 and 5g): registered here with the towers, as the casters' other home. */
    public static final RegistryObject<StructureType<com.elderlexicon.mod.university.UniversityStructure>> UNIVERSITY =
            TYPES.register("university", () -> () -> com.elderlexicon.mod.university.UniversityStructure.CODEC);
    public static final RegistryObject<StructurePieceType> UNIVERSITY_PIECE = PIECES.register("university",
            () -> (context, tag) -> new com.elderlexicon.mod.university.UniversityPiece(tag));
    public static final RegistryObject<StructurePieceType> UNIVERSITY_ROAD_PIECE = PIECES.register("university_road",
            () -> (context, tag) -> new com.elderlexicon.mod.university.UniversityRoadPiece(tag));

    private Towers() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
    }
}
