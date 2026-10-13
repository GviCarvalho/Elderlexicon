package com.elderlexicon.mod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Common config: Life Compass, Vita imbalance warnings and how much magic may break. */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

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

    /** How much the blows and blasts of magic may break (docs/plano-rosa-dos-elementos.md). */
    public enum MagicBreaks {
        /** Blocks break by the energy of the blow, whatever the world's rules. */
        ALWAYS,
        /** No block ever breaks: blows only hurt, push and are heard. */
        NEVER,
        /** Blocks break only where the world's mobGriefing rule lets mobs break them. */
        MOB_GRIEFING
    }

    private static final ForgeConfigSpec.EnumValue<MagicBreaks> MAGIC_BREAKS_BLOCKS = BUILDER
            .comment("Whether the impacts and blasts of spells break blocks: ALWAYS, NEVER, or MOB_GRIEFING (follow the"
                    + " world's mobGriefing rule)")
            .defineEnum("magic.breaksBlocks", MagicBreaks.MOB_GRIEFING);

    /** How often the sorcerer takes a vanilla evoker's place (docs/feiticeiro-design.md, 7). */
    private static final ForgeConfigSpec.DoubleValue SORCERER_MANSION_CHANCE = BUILDER
            .comment("Chance that an evoker of a woodland mansion is a sorcerer (0 to 1)")
            .defineInRange("sorcerer.mansionChance", 0.5D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.DoubleValue SORCERER_RAID_CHANCE = BUILDER
            .comment("Chance that an evoker of a raid's first wave is a sorcerer (0 to 1)")
            .defineInRange("sorcerer.raidChance", 0.1D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.DoubleValue SORCERER_RAID_CHANCE_PER_WAVE = BUILDER
            .comment("How much the chance grows with each wave after the first")
            .defineInRange("sorcerer.raidChancePerWave", 0.1D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.DoubleValue SORCERER_OTHER_CHANCE = BUILDER
            .comment("Chance that any other evoker (spawned by a command or another mod) is a sorcerer (0 to 1)")
            .defineInRange("sorcerer.otherChance", 0.0D, 0.0D, 1.0D);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double sorcererMansionChance = 0.5D;
    public static double sorcererRaidChance = 0.1D;
    public static double sorcererRaidChancePerWave = 0.1D;
    public static double sorcererOtherChance = 0.0D;

    public static MagicBreaks magicBreaksBlocks = MagicBreaks.MOB_GRIEFING;

    public static boolean lifeCompassOverflowRing;
    public static boolean aquaVisualWarnings;
    public static boolean aquaAudioWarnings;
    public static boolean auraVisualWarnings;
    public static boolean auraAudioWarnings;
    public static boolean firmoVisualWarnings;
    public static boolean firmoAudioWarnings;
    public static boolean igniVisualWarnings;
    public static boolean igniAudioWarnings;

        @SubscribeEvent
        @SuppressWarnings("null")
        static void onLoad(final ModConfigEvent event)
        {
                if (event.getConfig().getSpec() != SPEC) {
                        return;
                }
        lifeCompassOverflowRing = LIFE_COMPASS_OVERFLOW_RING.get();
        aquaVisualWarnings = AQUA_VISUAL_WARNINGS.get();
        aquaAudioWarnings = AQUA_AUDIO_WARNINGS.get();
        auraVisualWarnings = AURA_VISUAL_WARNINGS.get();
        auraAudioWarnings = AURA_AUDIO_WARNINGS.get();
        firmoVisualWarnings = FIRMO_VISUAL_WARNINGS.get();
        firmoAudioWarnings = FIRMO_AUDIO_WARNINGS.get();
        igniVisualWarnings = IGNI_VISUAL_WARNINGS.get();
        igniAudioWarnings = IGNI_AUDIO_WARNINGS.get();
        magicBreaksBlocks = MAGIC_BREAKS_BLOCKS.get();
        sorcererMansionChance = SORCERER_MANSION_CHANCE.get();
        sorcererRaidChance = SORCERER_RAID_CHANCE.get();
        sorcererRaidChancePerWave = SORCERER_RAID_CHANCE_PER_WAVE.get();
        sorcererOtherChance = SORCERER_OTHER_CHANCE.get();
    }
}
