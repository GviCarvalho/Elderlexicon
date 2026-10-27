package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * What the invocation bench adds to the game (docs/invocacao-design.md): the bench, its menu, and the things it makes
 * of matter. The bench has no item of its own: it is a crafting table the burin marked, and breaking it gives the
 * table back.
 */
public final class Invocations {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            ElderLexicon.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            ElderLexicon.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ElderLexicon.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            ElderLexicon.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ElderLexicon.MODID);

    public static final RegistryObject<Block> BENCH = BLOCKS.register("invocation_bench",
            () -> new InvocationBenchBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)));
    public static final RegistryObject<BlockEntityType<InvocationBenchBlockEntity>> BENCH_ENTITY =
            BLOCK_ENTITY_TYPES.register("invocation_bench", () -> BlockEntityType.Builder
                    .of(InvocationBenchBlockEntity::new, BENCH.get()).build(null));
    public static final RegistryObject<MenuType<InvocationBenchMenu>> MENU = MENUS.register("invocation_bench",
            () -> IForgeMenuType.create(InvocationBenchMenu::new));

    /** A thing thrown as a blow (iactare): a sword flies point first. */
    public static final RegistryObject<EntityType<ThrownThing>> THROWN = ENTITY_TYPES.register("thrown_thing",
            () -> EntityType.Builder.<ThrownThing>of(ThrownThing::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("thrown_thing"));

    /** Matter with no name of the game, as a square of the bench keeps it. */
    public static final RegistryObject<Item> MATTER = ITEMS.register("conjured_matter",
            () -> new ConjuredItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SWORD = tool("sword");
    public static final RegistryObject<Item> PICKAXE = tool("pickaxe");
    public static final RegistryObject<Item> AXE = tool("axe");
    public static final RegistryObject<Item> SHOVEL = tool("shovel");
    public static final RegistryObject<Item> HOE = tool("hoe");
    public static final RegistryObject<Item> SPEAR = tool("spear");
    public static final RegistryObject<Item> ARROW = ITEMS.register("conjured_arrow",
            () -> new ConjuredArrowItem(new Item.Properties()));
    public static final RegistryObject<Item> SHIELD = ITEMS.register("conjured_shield",
            () -> new ConjuredShieldItem(new Item.Properties().durability(336)));
    public static final RegistryObject<Item> CUBE = ITEMS.register("conjured_cube",
            () -> new ConjuredItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DISC = ITEMS.register("conjured_disc",
            () -> new ConjuredItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SPHERE = ITEMS.register("conjured_sphere",
            () -> new ConjuredItem(new Item.Properties().stacksTo(1)));

    private Invocations() {
    }

    private static RegistryObject<Item> tool(String format) {
        return ITEMS.register("conjured_" + format,
                () -> new ConjuredToolItem(new Item.Properties().durability(250), format));
    }

    /** Every conjured thing, coloured by its matter on the client. */
    public static List<RegistryObject<Item>> conjured() {
        return List.of(MATTER, SWORD, PICKAXE, AXE, SHOVEL, HOE, SPEAR, ARROW, SHIELD, CUBE, DISC, SPHERE);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITY_TYPES.register(bus);
        MENUS.register(bus);
        ENTITY_TYPES.register(bus);
    }
}
