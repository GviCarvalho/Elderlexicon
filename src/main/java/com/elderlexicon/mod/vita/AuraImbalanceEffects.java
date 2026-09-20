package com.elderlexicon.mod.vita;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies Aura imbalance penalties/bonuses each tick.
 */
final class AuraImbalanceEffects {

    private static final int BASE_DURATION = 60;
    private static final int SEVERE_LOW_PULSE = 80;
    private static final int SEVERE_HIGH_PULSE = 60;

    private static final Map<UUID, Integer> lowPulse = new HashMap<>();
    private static final Map<UUID, Integer> highPulse = new HashMap<>();

    private AuraImbalanceEffects() {
    }

    static void apply(ServerPlayer player, VitaData data) {
        VitaImbalanceTier tier = data.auraTier();
        switch (tier) {
            case SEVERELY_LOW -> applySeverelyLow(player);
            case SLIGHTLY_LOW -> applySlightlyLow(player);
            case SLIGHTLY_HIGH -> applySlightlyHigh(player);
            case SEVERELY_HIGH -> applySeverelyHigh(player);
            default -> clearTimers(player);
        }
        if (tier != VitaImbalanceTier.SEVERELY_LOW) {
            lowPulse.remove(player.getUUID());
        }
        if (tier != VitaImbalanceTier.SEVERELY_HIGH) {
            highPulse.remove(player.getUUID());
        }
    }

    private static void applySeverelyLow(ServerPlayer player) {
        applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 2, BASE_DURATION);
        applyEffect(player, MobEffects.WEAKNESS, 1, BASE_DURATION);
        player.getFoodData().addExhaustion(0.08F);
        player.setSprinting(false);
        player.setAirSupply(Math.max(0, player.getAirSupply() - 4));
        int counter = lowPulse.merge(player.getUUID(), 1, Integer::sum);
        if (counter >= SEVERE_LOW_PULSE) {
            lowPulse.put(player.getUUID(), 0);
            player.hurt(player.damageSources().drown(), 1.0F);
        }
    }

    private static void applySlightlyLow(ServerPlayer player) {
        applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0, BASE_DURATION);
        applyEffect(player, MobEffects.WEAKNESS, 0, BASE_DURATION);
        player.setAirSupply(Math.max(0, player.getAirSupply() - 2));
    }

    private static void applySlightlyHigh(ServerPlayer player) {
        applyEffect(player, MobEffects.CONFUSION, 0, 40);
        player.getFoodData().addExhaustion(0.01F);
    }

    private static void applySeverelyHigh(ServerPlayer player) {
        applyEffect(player, MobEffects.CONFUSION, 1, BASE_DURATION);
        applyEffect(player, MobEffects.BLINDNESS, 0, 40);
        applyEffect(player, MobEffects.DIG_SLOWDOWN, 0, BASE_DURATION);
        player.getFoodData().addExhaustion(0.02F);
        int counter = highPulse.merge(player.getUUID(), 1, Integer::sum);
        if (counter >= SEVERE_HIGH_PULSE) {
            highPulse.put(player.getUUID(), 0);
            forceDash(player);
        }
    }

    private static void forceDash(ServerPlayer player) {
        var look = player.getLookAngle();
        player.push(look.x * 0.4D, 0.1D, look.z * 0.4D);
        player.hurtMarked = true;
    }

    private static void applyEffect(ServerPlayer player, MobEffect effect, int amplifier, int duration) {
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && existing.getAmplifier() >= amplifier) {
            player.addEffect(new MobEffectInstance(effect, duration, existing.getAmplifier(), true, false, true));
            return;
        }
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
    }

    private static void clearTimers(ServerPlayer player) {
        lowPulse.remove(player.getUUID());
        highPulse.remove(player.getUUID());
    }
}
