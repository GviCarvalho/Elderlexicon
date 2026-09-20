package com.elderlexicon.mod.spelling.config;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spelling.client.ClientSpellingController;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Houses all client-side tuning options for the Spelling overlay.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpellingClientConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.DoubleValue INPUT_WINDOW_SECONDS = BUILDER
            .comment("Duration (in seconds) the Spelling overlay stays open by default.")
            .defineInRange("spelling.inputWindowSeconds", 2.0D, 1.0D, 4.0D);

    private static final ForgeConfigSpec.BooleanValue SHOW_COUNTDOWN = BUILDER
            .comment("If true, render the countdown timer text while recording a spell.")
            .define("spelling.showCountdown", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_SLOW_MODE = BUILDER
            .comment("If true, holding Shift extends the capture window to the slow mode duration.")
            .define("spelling.enableSlowMode", true);

    private static final ForgeConfigSpec.DoubleValue SLOW_MODE_SECONDS = BUILDER
            .comment("Duration (in seconds) when slow mode is active. Only used if enableSlowMode is true.")
            .defineInRange("spelling.slowModeSeconds", 3.5D, 2.0D, 6.0D);

    private static final ForgeConfigSpec.DoubleValue HOLD_DURATION_SECONDS = BUILDER
            .comment("How long (in seconds) the Spelling key must be held to open the repertoire editor.")
            .defineInRange("spelling.repertoireHoldSeconds", 1.5D, 0.5D, 4.0D);

    private static final ForgeConfigSpec.BooleanValue SHOW_HINTS = BUILDER
            .comment("If true, display the hint banner with recommended rune sequences.")
            .define("spelling.showHints", true);

    private static final ForgeConfigSpec.IntValue HINT_HISTORY_SIZE = BUILDER
            .comment("How many unique successful sequences to keep for hint suggestions.")
            .defineInRange("spelling.hintHistorySize", 6, 1, 20);

    private static final ForgeConfigSpec.BooleanValue ENABLE_SOUND_CUES = BUILDER
            .comment("If true, play sound cues for rune taps and spell results.")
            .define("spelling.enableSoundCues", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_PARTICLE_CUES = BUILDER
            .comment("If true, spawn subtle particle bursts when spells resolve.")
            .define("spelling.enableParticleCues", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double inputWindowSeconds = 2.0D;
    public static boolean showCountdown = true;
    public static boolean enableSlowMode = true;
    public static double slowModeSeconds = 3.5D;
    public static double holdDurationSeconds = 1.5D;
        public static boolean showHints = true;
        public static int hintHistorySize = 6;
        public static boolean enableSoundCues = true;
        public static boolean enableParticleCues = true;

    private SpellingClientConfig() {
    }

    @SubscribeEvent
    public static void handleModConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            inputWindowSeconds = INPUT_WINDOW_SECONDS.get();
            showCountdown = SHOW_COUNTDOWN.get();
            enableSlowMode = ENABLE_SLOW_MODE.get();
            slowModeSeconds = SLOW_MODE_SECONDS.get();
            holdDurationSeconds = HOLD_DURATION_SECONDS.get();
                        showHints = SHOW_HINTS.get();
                        hintHistorySize = HINT_HISTORY_SIZE.get();
                        enableSoundCues = ENABLE_SOUND_CUES.get();
                        enableParticleCues = ENABLE_PARTICLE_CUES.get();
                        ClientSpellingController.getInstance().handleClientConfigReload();
        }
    }
}
