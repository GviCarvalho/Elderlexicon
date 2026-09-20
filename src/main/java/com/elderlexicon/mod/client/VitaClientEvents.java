package com.elderlexicon.mod.client;

import com.elderlexicon.mod.Config;
import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaImbalanceTier;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * Client-only hooks for Vita UI feedback.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class VitaClientEvents {

    private static final ElementStatus AQUA_STATUS = new ElementStatus(
            VitaElement.AQUA,
            "Aqua",
            () -> Config.aquaVisualWarnings,
            () -> Config.aquaAudioWarnings,
            (value, previous) -> VitaSystem.evaluateAquaTier(value, previous),
            "tooltip.elderlexicon.vita.aqua"
    );

    private static final ElementStatus IGNI_STATUS = new ElementStatus(
            VitaElement.IGNI,
            "Igni",
            () -> Config.igniVisualWarnings,
            () -> Config.igniAudioWarnings,
            (value, previous) -> VitaSystem.evaluateIgniTier(value, previous),
            "tooltip.elderlexicon.vita.igni"
    );

    private static final ElementStatus AURA_STATUS = new ElementStatus(
            VitaElement.AURA,
            "Aura",
            () -> Config.auraVisualWarnings,
            () -> Config.auraAudioWarnings,
            (value, previous) -> VitaSystem.evaluateAuraTier(value, previous),
            "tooltip.elderlexicon.vita.aura"
    );

    private static final ElementStatus FIRMO_STATUS = new ElementStatus(
            VitaElement.FIRMO,
            "Firmo",
            () -> Config.firmoVisualWarnings,
            () -> Config.firmoAudioWarnings,
            (value, previous) -> VitaSystem.evaluateFirmoTier(value, previous),
            "tooltip.elderlexicon.vita.firmo"
    );

    private static final ElementStatus[] STATUSES = {
            AQUA_STATUS,
            IGNI_STATUS,
            FIRMO_STATUS,
            AURA_STATUS
    };

    private VitaClientEvents() {
    }

    @SubscribeEvent
    public static void handleClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            resetStatuses();
            return;
        }
        for (ElementStatus status : STATUSES) {
            status.update(player);
        }
    }

    private static void resetStatuses() {
        for (ElementStatus status : STATUSES) {
            status.reset();
        }
    }

    private static double readScore(LocalPlayer player, String label) {
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective("vita_status");
        if (objective == null) {
            return Double.NaN;
        }
        Score score = scoreboard.getOrCreatePlayerScore(label, objective);
        return score.getScore();
    }

    public static final class ElementStatus {

        private final VitaElement element;
        private final String scoreboardLabel;
        private final BooleanSupplier iconEnabled;
        private final BooleanSupplier audioEnabled;
        private final BiFunction<Double, VitaImbalanceTier, VitaImbalanceTier> evaluator;
        private final String tooltipKey;

        private VitaImbalanceTier tier = VitaImbalanceTier.BALANCED;
        private VitaImbalanceTier lastAudioTier = VitaImbalanceTier.BALANCED;
        private double value = Double.NaN;

        private ElementStatus(VitaElement element,
                              String scoreboardLabel,
                              BooleanSupplier iconEnabled,
                              BooleanSupplier audioEnabled,
                              BiFunction<Double, VitaImbalanceTier, VitaImbalanceTier> evaluator,
                              String tooltipKey) {
            this.element = element;
            this.scoreboardLabel = scoreboardLabel;
            this.iconEnabled = iconEnabled;
            this.audioEnabled = audioEnabled;
            this.evaluator = evaluator;
            this.tooltipKey = tooltipKey;
        }

        void update(LocalPlayer player) {
            value = readScore(player, scoreboardLabel);
            if (Double.isNaN(value)) {
                tier = VitaImbalanceTier.BALANCED;
            } else {
                tier = evaluator.apply(value, tier);
            }
            maybePlayAudio(player);
        }

        void reset() {
            tier = VitaImbalanceTier.BALANCED;
            lastAudioTier = VitaImbalanceTier.BALANCED;
            value = Double.NaN;
        }

        public VitaElement element() {
            return element;
        }

        public VitaImbalanceTier tier() {
            return tier;
        }

        public boolean iconEnabled() {
            return iconEnabled.getAsBoolean();
        }

        public Component tooltip() {
            Component tierComponent = Component.translatable(tierTranslationKey(tier));
            return Component.translatable(tooltipKey, tierComponent);
        }

        public boolean isSevere() {
            return tier == VitaImbalanceTier.SEVERELY_HIGH || tier == VitaImbalanceTier.SEVERELY_LOW;
        }

        private void maybePlayAudio(LocalPlayer player) {
            if (!audioEnabled.getAsBoolean()) {
                return;
            }
            if (tier == lastAudioTier) {
                return;
            }
            if (tier == VitaImbalanceTier.SEVERELY_LOW) {
                player.playSound(SoundEvents.PLAYER_HURT_DROWN, 0.5F, 0.8F);
            } else if (tier == VitaImbalanceTier.SEVERELY_HIGH) {
                player.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.6F, 1.3F);
            }
            lastAudioTier = tier;
        }

        private static String tierTranslationKey(VitaImbalanceTier tier) {
            return switch (tier) {
                case SEVERELY_LOW -> "hud.elderlexicon.vita.tier.severely_low";
                case SLIGHTLY_LOW -> "hud.elderlexicon.vita.tier.slightly_low";
                case SLIGHTLY_HIGH -> "hud.elderlexicon.vita.tier.slightly_high";
                case SEVERELY_HIGH -> "hud.elderlexicon.vita.tier.severely_high";
                default -> "hud.elderlexicon.vita.tier.balanced";
            };
        }
    }
}
