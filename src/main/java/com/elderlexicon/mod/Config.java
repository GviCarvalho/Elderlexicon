package com.elderlexicon.mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    public static final ForgeConfigSpec.BooleanValue LIFE_COMPASS_OVERFLOW_RING = BUILDER
            .comment("Render the overflow UMU ring on the Life Compass item")
            .define("lifeCompassOverflowRing", true);

    private static final ForgeConfigSpec.BooleanValue AQUA_VISUAL_WARNINGS = BUILDER
            .comment("If true, render screen tint warnings when Aqua is imbalanced")
            .define("aqua.visualWarnings", true);

    private static final ForgeConfigSpec.BooleanValue AQUA_AUDIO_WARNINGS = BUILDER
            .comment("If true, play ambience cues when Aqua enters severe tiers")
            .define("aqua.audioWarnings", true);

    private static final ForgeConfigSpec.BooleanValue AURA_VISUAL_WARNINGS = BUILDER
            .comment("If true, render screen tint warnings when Aura is imbalanced")
            .define("aura.visualWarnings", true);

    private static final ForgeConfigSpec.BooleanValue AURA_AUDIO_WARNINGS = BUILDER
            .comment("If true, play ambience cues when Aura enters severe tiers")
            .define("aura.audioWarnings", true);

    private static final ForgeConfigSpec.BooleanValue FIRMO_VISUAL_WARNINGS = BUILDER
            .comment("If true, render screen tint warnings when Firmo is imbalanced")
            .define("firmo.visualWarnings", true);

    private static final ForgeConfigSpec.BooleanValue FIRMO_AUDIO_WARNINGS = BUILDER
            .comment("If true, play ambience cues when Firmo enters severe tiers")
            .define("firmo.audioWarnings", true);

    private static final ForgeConfigSpec.BooleanValue IGNI_VISUAL_WARNINGS = BUILDER
            .comment("If true, render screen tint warnings when Igni is imbalanced")
            .define("igni.visualWarnings", true);

    private static final ForgeConfigSpec.BooleanValue IGNI_AUDIO_WARNINGS = BUILDER
            .comment("If true, play ambience cues when Igni enters severe tiers")
            .define("igni.audioWarnings", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;
    public static boolean lifeCompassOverflowRing;
    public static boolean aquaVisualWarnings;
    public static boolean aquaAudioWarnings;
    public static boolean auraVisualWarnings;
    public static boolean auraAudioWarnings;
    public static boolean firmoVisualWarnings;
    public static boolean firmoAudioWarnings;
    public static boolean igniVisualWarnings;
    public static boolean igniAudioWarnings;

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(itemName));
    }

        @SubscribeEvent
        @SuppressWarnings("null")
        static void onLoad(final ModConfigEvent event)
        {
                if (event.getConfig().getSpec() != SPEC) {
                        return;
                }
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemName)))
                .collect(Collectors.toSet());

        lifeCompassOverflowRing = LIFE_COMPASS_OVERFLOW_RING.get();
        aquaVisualWarnings = AQUA_VISUAL_WARNINGS.get();
        aquaAudioWarnings = AQUA_AUDIO_WARNINGS.get();
        auraVisualWarnings = AURA_VISUAL_WARNINGS.get();
        auraAudioWarnings = AURA_AUDIO_WARNINGS.get();
        firmoVisualWarnings = FIRMO_VISUAL_WARNINGS.get();
        firmoAudioWarnings = FIRMO_AUDIO_WARNINGS.get();
        igniVisualWarnings = IGNI_VISUAL_WARNINGS.get();
        igniAudioWarnings = IGNI_AUDIO_WARNINGS.get();
    }
}
