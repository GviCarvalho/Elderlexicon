package com.elderlexicon.mod.spell.matter;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The blocks matter needs that the game has none of: formless matter, solid and liquid (stage 5). */
public final class MatterBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            ElderLexicon.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ElderLexicon.MODID);

    public static final RegistryObject<FormlessMatterBlock> FORMLESS_SOLID = BLOCKS.register("formless_solid",
            () -> new FormlessMatterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.6F)
                    .sound(SoundType.MUD)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false), false));

    public static final RegistryObject<FormlessMatterBlock> FORMLESS_LIQUID = BLOCKS.register("formless_liquid",
            () -> new FormlessMatterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .noCollission()
                    .noOcclusion()
                    .strength(100.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    // Solid to the fluids of the game, so they do not wash it away (and what it holds with it).
                    .forceSolidOn()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false), true));

    public static final RegistryObject<BlockEntityType<FormlessMatterBlockEntity>> FORMLESS_MATTER =
            BLOCK_ENTITY_TYPES.register("formless_matter", () -> BlockEntityType.Builder
                    .of(FormlessMatterBlockEntity::new, FORMLESS_SOLID.get(), FORMLESS_LIQUID.get())
                    .build(null));

    private MatterBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
