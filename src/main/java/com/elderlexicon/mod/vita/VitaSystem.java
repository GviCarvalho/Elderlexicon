package com.elderlexicon.mod.vita;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Centralizes Vita-related calculations and conversions between HP and UMU.
 */
public final class VitaSystem {

    public static final double UMU_PER_HP = 5.0D;
    public static final double XP_PER_UMU = 10.0D;
    public static final double AQUA_RATIO = 0.55D;
    public static final double AURA_RATIO = 0.38D;
    public static final double IGNI_RATIO = 0.02D;
    public static final double FIRMO_RATIO = 0.05D;
    public static final double DEFAULT_AQUA = 55.0D;
    public static final double DEFAULT_AURA = 38.0D;
    public static final double DEFAULT_IGNI = 2.0D;
    public static final double DEFAULT_FIRMO = 5.0D;
    public static final double DEFAULT_TOTAL = DEFAULT_AQUA + DEFAULT_AURA + DEFAULT_IGNI + DEFAULT_FIRMO;
    private static final ImbalanceProfile AQUA_PROFILE = new ImbalanceProfile(
            DEFAULT_AQUA * 0.60D,
            DEFAULT_AQUA * 0.90D,
            DEFAULT_AQUA * 1.10D,
            DEFAULT_AQUA * 1.40D,
            1.0D
    );
    private static final ImbalanceProfile AURA_PROFILE = new ImbalanceProfile(
            DEFAULT_AURA * 0.60D,
            DEFAULT_AURA * 0.90D,
            DEFAULT_AURA * 1.10D,
            DEFAULT_AURA * 1.40D,
            1.0D
    );
    private static final ImbalanceProfile FIRMO_PROFILE = new ImbalanceProfile(
            DEFAULT_FIRMO * 0.60D,
            DEFAULT_FIRMO * 0.90D,
            DEFAULT_FIRMO * 1.10D,
            DEFAULT_FIRMO * 1.40D,
            0.1D
    );
    private static final ImbalanceProfile IGNI_PROFILE = new ImbalanceProfile(
            DEFAULT_IGNI * 0.60D,
            DEFAULT_IGNI * 0.90D,
            DEFAULT_IGNI * 1.10D,
            DEFAULT_IGNI * 1.40D,
            0.05D
    );

    /** Share of an imbalance the body takes back each second: about 13 s from severe to balanced. */
    public static final double RELAX_PER_SECOND = 0.10D;
    private static final int TICKS_PER_SECOND = 20;
    private static final double HEALTH_EPSILON = 1.0E-4D;
    private static final double EPSILON = 1.0E-4D;

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
        boolean dirty = false;

        if (Math.abs(currentHealth - lastHealth) > HEALTH_EPSILON) {
            if (currentHealth < lastHealth) {
                double lostUmu = (lastHealth - currentHealth) * UMU_PER_HP;
                data.consume(lostUmu);
            } else if (currentHealth > lastHealth) {
                double gainedUmu = (currentHealth - lastHealth) * UMU_PER_HP;
                data.restoreAndStabilize(gainedUmu);
            }
            data.setLastHealth(currentHealth);
            dirty = true;
        }

        if (player.tickCount % TICKS_PER_SECOND == 0 && data.relaxTowardBalance(RELAX_PER_SECOND)) {
            dirty = true;
        }

        if (refreshAquaTier(data)) {
            dirty = true;
        }
        if (refreshAuraTier(data)) {
            dirty = true;
        }
        if (refreshFirmoTier(data)) {
            dirty = true;
        }
        if (refreshIgniTier(data)) {
            dirty = true;
        }

        if (dirty) {
            data.save(player);
        }

        VitaScoreboardManager.update(player, data.toProfile());
        AquaImbalanceEffects.apply(player, data);
        AuraImbalanceEffects.apply(player, data);
        FirmoImbalanceEffects.apply(player, data);
        IgniImbalanceEffects.apply(player, data);
    }

    public static VitaImbalanceTier getAquaTier(ServerPlayer player) {
        if (player == null) {
            return VitaImbalanceTier.BALANCED;
        }
        return VitaData.get(player).aquaTier();
    }

    public static VitaImbalanceTier getAuraTier(ServerPlayer player) {
        if (player == null) {
            return VitaImbalanceTier.BALANCED;
        }
        return VitaData.get(player).auraTier();
    }

    public static VitaImbalanceTier getFirmoTier(ServerPlayer player) {
        if (player == null) {
            return VitaImbalanceTier.BALANCED;
        }
        return VitaData.get(player).firmoTier();
    }

    public static VitaImbalanceTier getIgniTier(ServerPlayer player) {
        if (player == null) {
            return VitaImbalanceTier.BALANCED;
        }
        return VitaData.get(player).igniTier();
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

    public static VitaImbalanceTier evaluateAquaTier(double current, VitaImbalanceTier previous) {
        return evaluateTier(current, previous, AQUA_PROFILE);
    }

    private static VitaImbalanceTier stepToward(VitaImbalanceTier from, VitaImbalanceTier to) {
        if (from == to) {
            return from;
        }
        int step = from.severityIndex() < to.severityIndex() ? 1 : -1;
        int nextIndex = from.severityIndex() + step;
        VitaImbalanceTier[] values = VitaImbalanceTier.values();
        nextIndex = Math.max(0, Math.min(values.length - 1, nextIndex));
        return values[nextIndex];
    }

    public static VitaImbalanceTier evaluateAuraTier(double current, VitaImbalanceTier previous) {
        return evaluateTier(current, previous, AURA_PROFILE);
    }

    private static boolean refreshAquaTier(VitaData data) {
        VitaImbalanceTier previous = data.aquaTier();
        VitaImbalanceTier next = evaluateAquaTier(data.get(VitaElement.AQUA), previous);
        return data.setAquaTier(next);
    }

    private static boolean refreshAuraTier(VitaData data) {
        VitaImbalanceTier previous = data.auraTier();
        VitaImbalanceTier next = evaluateAuraTier(data.get(VitaElement.AURA), previous);
        return data.setAuraTier(next);
    }

    public static VitaImbalanceTier evaluateFirmoTier(double current, VitaImbalanceTier previous) {
        return evaluateTier(current, previous, FIRMO_PROFILE);
    }

    private static boolean refreshFirmoTier(VitaData data) {
        VitaImbalanceTier previous = data.firmoTier();
        VitaImbalanceTier next = evaluateFirmoTier(data.get(VitaElement.FIRMO), previous);
        return data.setFirmoTier(next);
    }

    public static VitaImbalanceTier evaluateIgniTier(double current, VitaImbalanceTier previous) {
        return evaluateTier(current, previous, IGNI_PROFILE);
    }

    private static VitaImbalanceTier evaluateTier(double current,
                                                  VitaImbalanceTier previous,
                                                  ImbalanceProfile profile) {
        VitaImbalanceTier fallback = previous == null ? VitaImbalanceTier.BALANCED : previous;
        VitaImbalanceTier candidate = profile.classify(current);
        if (candidate == fallback) {
            return candidate;
        }
        VitaImbalanceTier cursor = fallback;
        while (cursor != candidate) {
            VitaImbalanceTier next = stepToward(cursor, candidate);
            double boundary = profile.boundary(cursor, next);
            if (next.severityIndex() > cursor.severityIndex()) {
                if (current >= boundary + profile.hysteresis()) {
                    cursor = next;
                    continue;
                }
                return cursor;
            }
            if (current <= boundary - profile.hysteresis()) {
                cursor = next;
                continue;
            }
            return cursor;
        }
        return cursor;
    }

    private static boolean refreshIgniTier(VitaData data) {
        VitaImbalanceTier previous = data.igniTier();
        VitaImbalanceTier next = evaluateIgniTier(data.get(VitaElement.IGNI), previous);
        return data.setIgniTier(next);
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

    public static void setElementEnergy(ServerPlayer player, VitaElement element, double umuAmount) {
        if (player == null || element == null || umuAmount < 0.0D) {
            return;
        }
        VitaData data = VitaData.get(player);
        double current = data.get(element);
        data.adjustElement(element, umuAmount - current);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

public static void forceSetElement(ServerPlayer player, VitaElement element, double umuAmount) {
        if (player == null || element == null) {
            return;
        }
        VitaData data = VitaData.get(player);
        data.setElementDirect(element, umuAmount);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void resetStatus(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // Balanced for the health the player has now: resetting to a full 100 UMU while hurt made the next tick read
        // the missing health as fresh damage and unbalance everything again.
        VitaData data = VitaData.get(player);
        data.reset(player.getHealth());
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static ElementTransferResult transferElement(ServerPlayer player,
                                                         VitaElement source,
                                                         VitaElement target,
                                                         double umuAmount,
                                                         boolean clampToAvailable) {
        double requested = Math.max(0.0D, umuAmount);
        if (player == null || requested <= EPSILON) {
            return new ElementTransferResult(requested, 0.0D, false);
        }

        VitaElement sanitizedSource = source == null ? VitaElement.BALANCED : source;
        VitaElement sanitizedTarget = target == null ? VitaElement.BALANCED : target;
        if (sanitizedSource == sanitizedTarget) {
            return new ElementTransferResult(requested, requested, false);
        }

        VitaData data = VitaData.get(player);
        double available = Math.max(0.0D, data.get(sanitizedSource));
        double transferable = Math.min(requested, available);
        if (transferable <= EPSILON) {
            return new ElementTransferResult(requested, 0.0D, false);
        }
        if (!clampToAvailable && transferable + EPSILON < requested) {
            return new ElementTransferResult(requested, 0.0D, false);
        }

        double applied = clampToAvailable ? transferable : requested;
        if (!clampToAvailable && applied > transferable) {
            applied = transferable;
        }

        data.consumeElement(sanitizedSource, applied);
        data.addElementEnergy(sanitizedTarget, applied);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());

        boolean clamped = applied + EPSILON < requested;
        return new ElementTransferResult(requested, applied, clamped);
    }

    public static double getElementExcess(ServerPlayer player, VitaElement element) {
        if (player == null || element == null || element.isBalanced()) {
            return 0.0D;
        }
        VitaData data = VitaData.get(player);
        return data.elementExcess(element);
    }

    public static double consumeElementExcess(ServerPlayer player, VitaElement element, double umuAmount) {
        if (player == null || element == null || element.isBalanced() || umuAmount <= 1.0E-4D) {
            return umuAmount;
        }
        VitaData data = VitaData.get(player);
        double remaining = data.consumeElementExcess(element, umuAmount);
        if (remaining + 1.0E-4D < umuAmount) {
            data.save(player);
            VitaScoreboardManager.update(player, data.toProfile());
        }
        return remaining;
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

    private record ImbalanceProfile(double severeLowThreshold,
                                    double slightLowThreshold,
                                    double slightHighThreshold,
                                    double severeHighThreshold,
                                    double hysteresis) {

        VitaImbalanceTier classify(double value) {
            if (value < severeLowThreshold) {
                return VitaImbalanceTier.SEVERELY_LOW;
            }
            if (value < slightLowThreshold) {
                return VitaImbalanceTier.SLIGHTLY_LOW;
            }
            if (value <= slightHighThreshold) {
                return VitaImbalanceTier.BALANCED;
            }
            if (value <= severeHighThreshold) {
                return VitaImbalanceTier.SLIGHTLY_HIGH;
            }
            return VitaImbalanceTier.SEVERELY_HIGH;
        }

        double boundary(VitaImbalanceTier from, VitaImbalanceTier to) {
            int lower = Math.min(from.severityIndex(), to.severityIndex());
            return switch (lower) {
                case 0 -> severeLowThreshold;
                case 1 -> slightLowThreshold;
                case 2 -> slightHighThreshold;
                default -> severeHighThreshold;
            };
        }
    }

    public record ElementTransferResult(double requested,
                                        double transferred,
                                        boolean clamped) {

        public double deficit() {
            return Math.max(0.0D, requested - transferred);
        }

        public boolean succeeded() {
            return transferred > EPSILON;
        }
    }

}
