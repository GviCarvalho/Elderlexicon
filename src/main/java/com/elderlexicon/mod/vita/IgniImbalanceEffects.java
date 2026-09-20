package com.elderlexicon.mod.vita;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies thermal imbalance penalties/bonuses for Igni tiers.
 */
final class IgniImbalanceEffects {

    private static final int BASE_DURATION = 80;
    private static final Map<UUID, Integer> heatPulse = new HashMap<>();

    private IgniImbalanceEffects() {
    }

    static void apply(ServerPlayer player, VitaData data) {
        VitaImbalanceTier tier = data.igniTier();
        switch (tier) {
            case SEVERELY_LOW -> applySeverelyLow(player);
            case SLIGHTLY_LOW -> applySlightlyLow(player);
            case SLIGHTLY_HIGH -> applySlightlyHigh(player);
            case SEVERELY_HIGH -> applySeverelyHigh(player);
            default -> heatPulse.remove(player.getUUID());
        }
        if (tier != VitaImbalanceTier.SEVERELY_HIGH) {
            heatPulse.remove(player.getUUID());
        }
    }

    private static void applySeverelyLow(ServerPlayer player) {
        applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 1, BASE_DURATION);
        applyEffect(player, MobEffects.DIG_SLOWDOWN, 1, BASE_DURATION);
        applyEffect(player, MobEffects.WEAKNESS, 0, BASE_DURATION);
        player.getFoodData().addExhaustion(0.04F);
        player.clearFire();
    }

    private static void applySlightlyLow(ServerPlayer player) {
        applyEffect(player, MobEffects.DIG_SLOWDOWN, 0, BASE_DURATION);
        player.getFoodData().addExhaustion(0.01F);
    }

    private static void applySlightlyHigh(ServerPlayer player) {
        applyEffect(player, MobEffects.CONFUSION, 0, 40);
        player.getFoodData().addExhaustion(0.02F);
    }

    private static void applySeverelyHigh(ServerPlayer player) {
        applyEffect(player, MobEffects.WEAKNESS, 1, BASE_DURATION);
        applyEffect(player, MobEffects.HUNGER, 1, BASE_DURATION);
        int counter = heatPulse.merge(player.getUUID(), 1, Integer::sum);
        if (counter >= 40) {
            heatPulse.put(player.getUUID(), 0);
            player.setSecondsOnFire(2);
        }
    }

    private static void applyEffect(ServerPlayer player, MobEffect effect, int amplifier, int duration) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() >= amplifier) {
            player.addEffect(new MobEffectInstance(effect, duration, current.getAmplifier(), true, false, true));
            return;
        }
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
    }
}
