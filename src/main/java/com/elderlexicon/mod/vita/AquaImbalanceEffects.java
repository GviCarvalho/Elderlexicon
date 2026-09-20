package com.elderlexicon.mod.vita;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies gameplay penalties/bonuses for Aqua imbalance tiers.
 */
final class AquaImbalanceEffects {

    private static final int BASE_DURATION_TICKS = 60;
    private static final int SEVERE_LOW_PULSE_INTERVAL = 80;
    private static final int SEVERE_HIGH_PULSE_INTERVAL = 100;

    private static final Map<UUID, Integer> severeLowPulse = new HashMap<>();
    private static final Map<UUID, Integer> severeHighPulse = new HashMap<>();

    private AquaImbalanceEffects() {
    }

    static void apply(ServerPlayer player, VitaData data) {
        if (player == null || data == null) {
            return;
        }
        VitaImbalanceTier tier = data.aquaTier();
        switch (tier) {
            case SEVERELY_LOW -> applySeverelyLow(player);
            case SLIGHTLY_LOW -> applySlightlyLow(player);
            case SLIGHTLY_HIGH -> applySlightlyHigh(player);
            case SEVERELY_HIGH -> applySeverelyHigh(player);
            default -> clearTimers(player);
        }
        if (tier != VitaImbalanceTier.SEVERELY_LOW) {
            severeLowPulse.remove(player.getUUID());
        }
        if (tier != VitaImbalanceTier.SEVERELY_HIGH) {
            severeHighPulse.remove(player.getUUID());
        }
    }

    private static void applySeverelyLow(ServerPlayer player) {
        applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 1, BASE_DURATION_TICKS);
        applyEffect(player, MobEffects.WEAKNESS, 0, BASE_DURATION_TICKS);
        player.causeFoodExhaustion(0.04F);
        int counter = severeLowPulse.compute(player.getUUID(), (uuid, ticks) -> ticks == null ? 1 : ticks + 1);
        if (counter >= SEVERE_LOW_PULSE_INTERVAL) {
            severeLowPulse.put(player.getUUID(), 0);
            player.causeFoodExhaustion(1.5F);
            player.hurt(player.damageSources().starve(), 1.0F);
        }
    }

    private static void applySlightlyLow(ServerPlayer player) {
        applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0, BASE_DURATION_TICKS);
        player.causeFoodExhaustion(0.01F);
    }

    private static void applySlightlyHigh(ServerPlayer player) {
        applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0, BASE_DURATION_TICKS);
        applyEffect(player, MobEffects.WATER_BREATHING, 0, BASE_DURATION_TICKS + 40);
    }

    private static void applySeverelyHigh(ServerPlayer player) {
        applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 2, BASE_DURATION_TICKS);
        applyEffect(player, MobEffects.DIG_SLOWDOWN, 1, BASE_DURATION_TICKS);
        applyEffect(player, MobEffects.HUNGER, 0, BASE_DURATION_TICKS);
        int counter = severeHighPulse.compute(player.getUUID(), (uuid, ticks) -> ticks == null ? 1 : ticks + 1);
        if (counter >= SEVERE_HIGH_PULSE_INTERVAL) {
            severeHighPulse.put(player.getUUID(), 0);
            applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 5, 20);
        }
    }

    private static void applyEffect(ServerPlayer player, MobEffect effect, int amplifier, int durationTicks) {
        if (player.hasEffect(effect) && player.getEffect(effect).getAmplifier() >= amplifier) {
            // refresh duration but keep amplifier
            player.addEffect(new MobEffectInstance(effect, durationTicks, player.getEffect(effect).getAmplifier(), true, false, true));
            return;
        }
        player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, true, false, true));
    }

    private static void clearTimers(ServerPlayer player) {
        severeLowPulse.remove(player.getUUID());
        severeHighPulse.remove(player.getUUID());
    }
}
