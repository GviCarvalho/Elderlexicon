package com.elderlexicon.mod.vita;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Centralizes Vita-related calculations and conversions between HP and UMU.
 */
public final class VitaSystem {

    public static final double UMU_PER_HP = 5.0D;
    public static final double XP_PER_UMU = 10.0D;
    public static final double AQUA_RATIO = 0.56D;
    public static final double AURA_RATIO = 0.38D;
    public static final double IGNI_RATIO = 0.02D;
    public static final double FIRMO_RATIO = 0.04D;

    private static final double HEALTH_EPSILON = 1.0E-4D;

    private VitaSystem() {
    }

    public static VitaProfile fromPlayer(ServerPlayer player) {
        if (player == null) {
            return VitaProfile.empty();
        }
        VitaData data = VitaData.get(player);
        return data.toProfile();
    }

    public static double getLifeEnergy(ServerPlayer player) {
        return getLifeEnergy(player, VitaElement.BALANCED);
    }

    public static double getLifeEnergy(ServerPlayer player, VitaElement element) {
        if (player == null) {
            return 0.0D;
        }
        VitaData data = VitaData.get(player);
        VitaElement target = element == null ? VitaElement.BALANCED : element;
        return data.get(target);
    }

    public static void tickPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }

        VitaData data = VitaData.get(player);
        float currentHealth = Math.max(0.0F, player.getHealth());
        float lastHealth = data.lastHealth();
        if (Math.abs(currentHealth - lastHealth) > HEALTH_EPSILON) {
            if (currentHealth < lastHealth) {
                double lostUmu = (lastHealth - currentHealth) * UMU_PER_HP;
                data.consume(lostUmu);
            } else if (currentHealth > lastHealth) {
                double gainedUmu = (currentHealth - lastHealth) * UMU_PER_HP;
                data.restoreAndStabilize(gainedUmu);
            }
            data.setLastHealth(currentHealth);
            data.save(player);
        }

        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void initialize(ServerPlayer player) {
        VitaData data = VitaData.get(player);
        data.setBalancedValues(Math.max(0.0F, player.getHealth()) * UMU_PER_HP);
        data.setLastHealth(player.getHealth());
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void consumeLifeEnergy(ServerPlayer player, float hpDamage) {
        consumeLifeEnergy(player, hpDamage, VitaElement.BALANCED);
    }

    public static void consumeLifeEnergy(ServerPlayer player, float hpDamage, VitaElement element) {
        if (player == null) {
            return;
        }
        float clamped = Math.max(0.0F, Math.min(hpDamage, player.getHealth()));
        if (clamped <= 1.0E-4F) {
            return;
        }

        VitaData data = VitaData.get(player);
        double drain = clamped * UMU_PER_HP;
        VitaElement target = element == null ? VitaElement.BALANCED : element;
        data.consumeElement(target, drain);
        player.hurt(Objects.requireNonNull(player.damageSources().magic()), clamped);
        data.setLastHealth(player.getHealth());
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void restoreLifeEnergy(ServerPlayer player, double umuAmount) {
        if (player == null || umuAmount <= 1.0E-4D) {
            return;
        }
        VitaData data = VitaData.get(player);
        data.restoreAndStabilize(umuAmount);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void restoreElementEnergy(ServerPlayer player, VitaElement element, double umuAmount) {
        if (player == null || umuAmount <= 1.0E-4D) {
            return;
        }
        VitaData data = VitaData.get(player);
        data.addElementEnergy(element, umuAmount);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void consumeElementReserve(ServerPlayer player, VitaElement element, double umuAmount) {
        if (player == null || umuAmount <= 1.0E-4D) {
            return;
        }
        VitaData data = VitaData.get(player);
        data.consumeElement(element, umuAmount);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

}
