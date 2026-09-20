package com.elderlexicon.mod.vita;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Applies Firmo imbalance gameplay effects.
 */
final class FirmoImbalanceEffects {

    private static final AttributeModifier LOW_HEALTH_DEBUFF = new AttributeModifier(
            UUID.fromString("60bb2a9d-7c47-4c0d-8ad2-9c9768d16d1d"),
            "firmo_deficiency_health",
            -8.0D,
            AttributeModifier.Operation.ADDITION
    );

    private FirmoImbalanceEffects() {
    }

    static void apply(ServerPlayer player, VitaData data) {
        VitaImbalanceTier tier = data.firmoTier();
        switch (tier) {
            case SEVERELY_LOW -> applySeverelyLow(player);
            case SLIGHTLY_LOW -> applySlightlyLow(player);
            case SLIGHTLY_HIGH -> applySlightlyHigh(player);
            case SEVERELY_HIGH -> applySeverelyHigh(player);
            default -> clear(player);
        }
        if (tier != VitaImbalanceTier.SEVERELY_LOW) {
            removeHealthDebuff(player);
        }
    }

    private static void applySeverelyLow(ServerPlayer player) {
        applyEffect(player, MobEffects.DIG_SLOWDOWN, 1, 80);
        applyEffect(player, MobEffects.WEAKNESS, 1, 80);
        applyEffect(player, MobEffects.HUNGER, 0, 60);
        applyHealthDebuff(player);
    }

    private static void applySlightlyLow(ServerPlayer player) {
        applyEffect(player, MobEffects.DIG_SLOWDOWN, 0, 80);
        applyEffect(player, MobEffects.WEAKNESS, 0, 80);
    }

    private static void applySlightlyHigh(ServerPlayer player) {
        applyEffect(player, MobEffects.DAMAGE_RESISTANCE, 0, 80);
        applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0, 80);
    }

    private static void applySeverelyHigh(ServerPlayer player) {
        applyEffect(player, MobEffects.DAMAGE_RESISTANCE, 1, 80);
        applyEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 1, 80);
        applyEffect(player, MobEffects.HUNGER, 0, 80);
    }

    private static void applyEffect(ServerPlayer player, MobEffect effect, int amplifier, int duration) {
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && existing.getAmplifier() >= amplifier) {
            player.addEffect(new MobEffectInstance(effect, duration, existing.getAmplifier(), true, false, true));
            return;
        }
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
    }

    private static void applyHealthDebuff(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        if (!attribute.hasModifier(LOW_HEALTH_DEBUFF)) {
            attribute.addTransientModifier(LOW_HEALTH_DEBUFF);
            if (player.getHealth() > attribute.getValue()) {
                player.setHealth((float) attribute.getValue());
            }
        }
    }

    private static void removeHealthDebuff(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null && attribute.hasModifier(LOW_HEALTH_DEBUFF)) {
            attribute.removeModifier(LOW_HEALTH_DEBUFF);
        }
    }

    private static void clear(ServerPlayer player) {
        removeHealthDebuff(player);
    }
}
