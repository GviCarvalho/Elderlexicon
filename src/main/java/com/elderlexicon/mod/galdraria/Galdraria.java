package com.elderlexicon.mod.galdraria;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** What galdraria adds to the game (docs/galdraria-design.md): the table, the burin and the table's menu. */
public final class Galdraria {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            ElderLexicon.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            ElderLexicon.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            ElderLexicon.MODID);

    public static final RegistryObject<Block> TABLE = BLOCKS.register("galdraria_table",
            () -> new GaldrariaTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()));
    public static final RegistryObject<Item> TABLE_ITEM = ITEMS.register("galdraria_table",
            () -> new BlockItem(TABLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BURIN = ITEMS.register("burin",
            () -> new BurinItem(new Item.Properties()));

    public static final RegistryObject<MenuType<GaldrariaMenu>> MENU = MENUS.register("galdraria_table",
            () -> new MenuType<>(GaldrariaMenu::new, FeatureFlags.VANILLA_SET));

    private Galdraria() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
    }
}
