package com.elderlexicon.mod;

import com.elderlexicon.mod.command.SpellCommand;
import com.elderlexicon.mod.command.VitaCommand;
import com.elderlexicon.mod.item.ElderBrushItem;
import com.elderlexicon.mod.item.LifeCompassItem;
import com.elderlexicon.mod.mark.network.MarkNetwork;
import com.elderlexicon.mod.spelling.config.SpellingClientConfig;
import com.elderlexicon.mod.spell.scene.ArcBoltEntity;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.item.ImprovisedWandItem;
import com.elderlexicon.mod.spelling.item.ModularWandItem;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.elderlexicon.mod.spelling.recipe.WandUpgradeRecipe;
import com.elderlexicon.mod.vita.damage.DamageMappingConfig;
import com.elderlexicon.mod.vita.recovery.VitaRecoveryConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.Map;

@SuppressWarnings("null")
@Mod(ExampleMod.MODID)
public class ExampleMod {

    public static final String MODID = "elderlexicon";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);

    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block",
            () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register("example_item",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .alwaysEat().nutrition(1).saturationMod(2f).build())));

    public static final RegistryObject<Item> LIFE_COMPASS = ITEMS.register("life_compass",
            () -> new LifeCompassItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ELDER_BRUSH = ITEMS.register("elder_brush",
            () -> new ElderBrushItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> IMPROVISED_WAND = ITEMS.register("improvised_wand",
            () -> new ImprovisedWandItem(new Item.Properties()));
    public static final RegistryObject<Item> IMPROVISED_WAND_BONE = ITEMS.register("improvised_wand_bone",
            () -> new ImprovisedWandItem(
                    new Item.Properties(),
                    8D,
                    "tooltip.elderlexicon.improvised_wand_bone",
                    Map.of("firmo", 0.20D)));
    public static final RegistryObject<Item> IMPROVISED_WAND_BAMBOO = ITEMS.register("improvised_wand_bamboo",
            () -> new ImprovisedWandItem(
                    new Item.Properties(),
                    8D,
                    "tooltip.elderlexicon.improvised_wand_bamboo",
                    Map.of("aura", 0.10D, "aqua", 0.10D)));
    public static final RegistryObject<Item> IMPROVISED_WAND_BLAZE = ITEMS.register("improvised_wand_blaze",
            () -> new ImprovisedWandItem(
                    new Item.Properties(),
                    12D,
                    "tooltip.elderlexicon.improvised_wand_blaze",
                    Map.of("igni", 0.20D)));

    public static final RegistryObject<Item> WAND = ITEMS.register("wand",
            () -> new ModularWandItem(new Item.Properties(), 6D, "tooltip.elderlexicon.wand", Map.of()));
    public static final RegistryObject<Item> WAND_BONE = ITEMS.register("wand_bone",
            () -> new ModularWandItem(new Item.Properties(), 8D, "tooltip.elderlexicon.wand_bone",
                    Map.of("firmo", 0.20D)));
    public static final RegistryObject<Item> WAND_BAMBOO = ITEMS.register("wand_bamboo",
            () -> new ModularWandItem(new Item.Properties(), 8D, "tooltip.elderlexicon.wand_bamboo",
                    Map.of("aura", 0.10D, "aqua", 0.10D)));
    public static final RegistryObject<Item> WAND_BLAZE = ITEMS.register("wand_blaze",
            () -> new ModularWandItem(new Item.Properties(), 12D, "tooltip.elderlexicon.wand_blaze",
                    Map.of("igni", 0.20D)));

    public static final RegistryObject<Item> GRIMOIRE = ITEMS.register("grimoire",
            () -> new GrimoireItem(new Item.Properties()));
    public static final RegistryObject<Item> DETACHED_PAGE = ITEMS.register("detached_page",
            () -> new SpellScrollItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<EntityType<PlacedScrollEntity>> PLACED_SCROLL = ENTITY_TYPES.register("placed_scroll",
            () -> EntityType.Builder.<PlacedScrollEntity>of(PlacedScrollEntity::new, MobCategory.MISC)
                    .sized(0.75F, 0.75F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("placed_scroll"));

    public static final RegistryObject<EntityType<ArcBoltEntity>> ARC_BOLT = ENTITY_TYPES.register("arc_bolt",
            () -> EntityType.Builder.<ArcBoltEntity>of(ArcBoltEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("arc_bolt"));

    public static final RegistryObject<RecipeSerializer<WandUpgradeRecipe>> WAND_UPGRADE_SERIALIZER =
            RECIPE_SERIALIZERS.register("wand_upgrade", () -> new SimpleCraftingRecipeSerializer<>(WandUpgradeRecipe::new));

    public static final RegistryObject<CreativeModeTab> ELDER_LEXICON_TAB = CREATIVE_MODE_TABS.register("elder_lexicon",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.elderlexicon.elder_lexicon"))
                    .icon(() -> GRIMOIRE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(LIFE_COMPASS.get());
                        output.accept(ELDER_BRUSH.get());
                        output.accept(GRIMOIRE.get());
                        output.accept(DETACHED_PAGE.get());

                        output.accept(IMPROVISED_WAND.get());
                        output.accept(IMPROVISED_WAND_BONE.get());
                        output.accept(IMPROVISED_WAND_BAMBOO.get());
                        output.accept(IMPROVISED_WAND_BLAZE.get());

                        output.accept(WAND.get());
                        output.accept(WAND_BONE.get());
                        output.accept(WAND_BAMBOO.get());
                        output.accept(WAND_BLAZE.get());
                    })
                    .build());

    public ExampleMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, SpellingClientConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        if (Config.logDirtBlock) {
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
        }
        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);
        Config.items.forEach(item -> LOGGER.info("ITEM >> {}", item));

        event.enqueueWork(() -> {
            SpellingNetwork.register();
            MarkNetwork.register();
            DamageMappingConfig.initialize();
            VitaRecoveryConfig.initialize();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        SpellCommand.register(event.getDispatcher());
        VitaCommand.register(event.getDispatcher());
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
