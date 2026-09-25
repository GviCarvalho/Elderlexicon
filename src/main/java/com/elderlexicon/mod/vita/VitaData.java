package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Persistent Vita storage per player, allowing imbalances across elemental components.
 */
final class VitaData {

    private static final String STORAGE_KEY = ExampleMod.MODID + "_vita";
    private static final String TAG_AQUA = "aqua";
    private static final String TAG_AURA = "aura";
    private static final String TAG_IGNI = "igni";
    private static final String TAG_FIRMO = "firmo";
    private static final String TAG_LAST_HEALTH = "lastHealth";
    private static final String TAG_HOMEOSTASIS = "homeostasis_queue";
    private static final String TAG_AQUA_TIER = "aquaTier";
    private static final String TAG_AURA_TIER = "auraTier";
    private static final String TAG_FIRMO_TIER = "firmoTier";
    private static final String TAG_IGNI_TIER = "igniTier";

    private static final double EPSILON = 1.0E-4D;
    /** Smallest step of the return to balance, so the last bit of an imbalance does not linger forever. */
    private static final double MIN_RELAX_STEP = 0.01D;

    private double aqua;
    private double aura;
    private double igni;
    private double firmo;
    private float lastHealth;
    private VitaImbalanceTier aquaTier = VitaImbalanceTier.BALANCED;
    private VitaImbalanceTier auraTier = VitaImbalanceTier.BALANCED;
    private VitaImbalanceTier firmoTier = VitaImbalanceTier.BALANCED;
    private VitaImbalanceTier igniTier = VitaImbalanceTier.BALANCED;

    private VitaData(double aqua, double aura, double igni, double firmo, float lastHealth) {
        this.aqua = aqua;
        this.aura = aura;
        this.igni = igni;
        this.firmo = firmo;
        this.lastHealth = lastHealth;
    }

    static VitaData get(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag root = persistent.getCompound(STORAGE_KEY);
        if (root.isEmpty()) {
            VitaData data = createBalanced(player.getHealth());
            data.save(player);
            return data;
        }
        return fromTag(root, player.getHealth());
    }

    VitaProfile toProfile() {
        return new VitaProfile(
                totalUmu(),
                aqua,
                aura,
                igni,
                firmo,
                elementExcess(VitaElement.AQUA),
                elementExcess(VitaElement.AURA),
                elementExcess(VitaElement.IGNI),
                elementExcess(VitaElement.FIRMO)
        );
    }

    double totalUmu() {
        return aqua + aura + igni + firmo;
    }

    private static VitaElement sanitize(VitaElement element) {
        return element == null ? VitaElement.BALANCED : element;
    }

    double elementExcess(VitaElement element) {
        VitaElement target = sanitize(element);
        if (target.isBalanced()) {
            return 0.0D;
        }
        double baseline = baselineFor(target);
        return Math.max(0.0D, get(target) - baseline);
    }

    double consumeElementExcess(VitaElement element, double umuAmount) {
        if (umuAmount <= EPSILON) {
            return 0.0D;
        }
        VitaElement target = sanitize(element);
        if (target.isBalanced()) {
            return umuAmount;
        }
        double excess = elementExcess(target);
        if (excess <= EPSILON) {
            return umuAmount;
        }
        double spent = Math.min(excess, umuAmount);
        adjustElement(target, -spent);
        return Math.max(0.0D, umuAmount - spent);
    }

    double get(VitaElement element) {
        VitaElement target = sanitize(element);
        return switch (target) {
            case AQUA -> aqua;
            case AURA -> aura;
            case IGNI -> igni;
            case FIRMO -> firmo;
            case BALANCED -> totalUmu();
        };
    }

    /**
     * Life lost takes each element in its share of life (55/38/2/5), the same way healing gives it back, so being
     * hurt and healed never unbalances the body by itself.
     */
    void consume(double umuAmount) {
        if (umuAmount <= EPSILON) {
            return;
        }
        aqua = Math.max(0.0D, aqua - umuAmount * VitaSystem.AQUA_RATIO);
        aura = Math.max(0.0D, aura - umuAmount * VitaSystem.AURA_RATIO);
        igni = Math.max(0.0D, igni - umuAmount * VitaSystem.IGNI_RATIO);
        firmo = Math.max(0.0D, firmo - umuAmount * VitaSystem.FIRMO_RATIO);
    }

    void consumeElement(VitaElement element, double umuAmount) {
        if (umuAmount <= EPSILON) {
            return;
        }

        VitaElement target = sanitize(element);
        if (target.isBalanced()) {
            consume(umuAmount);
            return;
        }

        adjustElement(target, -umuAmount);
    }

    void restoreAndStabilize(double umuAmount) {
        if (umuAmount <= EPSILON) {
            return;
        }
        aqua += umuAmount * VitaSystem.AQUA_RATIO;
        aura += umuAmount * VitaSystem.AURA_RATIO;
        igni += umuAmount * VitaSystem.IGNI_RATIO;
        firmo += umuAmount * VitaSystem.FIRMO_RATIO;
    }

    void setBalancedValues(double totalUmu) {
        aqua = totalUmu * VitaSystem.AQUA_RATIO;
        aura = totalUmu * VitaSystem.AURA_RATIO;
        igni = totalUmu * VitaSystem.IGNI_RATIO;
        firmo = totalUmu * VitaSystem.FIRMO_RATIO;
    }

    void adjustElement(VitaElement element, double delta) {
        if (Math.abs(delta) <= EPSILON) {
            return;
        }
        VitaElement target = sanitize(element);
        if (target.isBalanced()) {
            restoreAndStabilize(delta);
            return;
        }
        modifyElement(target, delta);
    }

    void addElementEnergy(VitaElement element, double amount) {
        if (amount <= EPSILON) {
            return;
        }
        adjustElement(element, amount);
    }

    void setElementDirect(VitaElement element, double value) {
        VitaElement target = sanitize(element);
        if (target.isBalanced()) {
            return;
        }
        double clamped = Math.max(0.0D, value);
        setElement(target, clamped);
    }

    VitaImbalanceTier aquaTier() {
        return aquaTier;
    }

    boolean setAquaTier(VitaImbalanceTier tier) {
        VitaImbalanceTier sanitized = tier == null ? VitaImbalanceTier.BALANCED : tier;
        if (sanitized == aquaTier) {
            return false;
        }
        aquaTier = sanitized;
        return true;
    }

    VitaImbalanceTier auraTier() {
        return auraTier;
    }

    boolean setAuraTier(VitaImbalanceTier tier) {
        VitaImbalanceTier sanitized = tier == null ? VitaImbalanceTier.BALANCED : tier;
        if (sanitized == auraTier) {
            return false;
        }
        auraTier = sanitized;
        return true;
    }

    VitaImbalanceTier firmoTier() {
        return firmoTier;
    }

    boolean setFirmoTier(VitaImbalanceTier tier) {
        VitaImbalanceTier sanitized = tier == null ? VitaImbalanceTier.BALANCED : tier;
        if (sanitized == firmoTier) {
            return false;
        }
        firmoTier = sanitized;
        return true;
    }

    VitaImbalanceTier igniTier() {
        return igniTier;
    }

    boolean setIgniTier(VitaImbalanceTier tier) {
        VitaImbalanceTier sanitized = tier == null ? VitaImbalanceTier.BALANCED : tier;
        if (sanitized == igniTier) {
            return false;
        }
        igniTier = sanitized;
        return true;
    }

    private double baselineFor(VitaElement element) {
        if (element == null) {
            return baselineTotal();
        }
        double baselineTotal = baselineTotal();
        return switch (element) {
            case AQUA -> baselineTotal * VitaSystem.AQUA_RATIO;
            case AURA -> baselineTotal * VitaSystem.AURA_RATIO;
            case IGNI -> baselineTotal * VitaSystem.IGNI_RATIO;
            case FIRMO -> baselineTotal * VitaSystem.FIRMO_RATIO;
            case BALANCED -> baselineTotal;
        };
    }

    private double baselineTotal() {
        return Math.max(0.0F, lastHealth) * VitaSystem.UMU_PER_HP;
    }

    private void setElement(VitaElement element, double value) {
        switch (element) {
            case AQUA -> aqua = value;
            case AURA -> aura = value;
            case IGNI -> igni = value;
            case FIRMO -> firmo = value;
            case BALANCED -> {
            }
        }
    }

    private void modifyElement(VitaElement element, double delta) {
        if (element == null || element.isBalanced() || Math.abs(delta) <= EPSILON) {
            return;
        }
        setElement(element, Math.max(0.0D, get(element) + delta));
    }

    /**
     * The body drifts back to its balance (book 3.2: it "absorbs the energy around it slowly ... until the scale
     * returns to balance"): each element moves {@code fraction} of the way to its share of the current life.
     *
     * @return whether anything moved
     */
    boolean relaxTowardBalance(double fraction) {
        boolean moved = false;
        for (VitaElement element : new VitaElement[] {VitaElement.AQUA, VitaElement.AURA, VitaElement.IGNI, VitaElement.FIRMO}) {
            double deviation = get(element) - baselineFor(element);
            if (Math.abs(deviation) <= EPSILON) {
                continue;
            }
            double step = deviation * fraction;
            if (Math.abs(step) < MIN_RELAX_STEP) {
                step = Math.copySign(Math.min(Math.abs(deviation), MIN_RELAX_STEP), deviation);
            }
            setElement(element, Math.max(0.0D, get(element) - step));
            moved = true;
        }
        return moved;
    }

    /** Back to a balanced body for the health it has now, forgetting the imbalance tiers. */
    void reset(float health) {
        lastHealth = Math.max(0.0F, health);
        setBalancedValues(lastHealth * VitaSystem.UMU_PER_HP);
        aquaTier = VitaImbalanceTier.BALANCED;
        auraTier = VitaImbalanceTier.BALANCED;
        firmoTier = VitaImbalanceTier.BALANCED;
        igniTier = VitaImbalanceTier.BALANCED;
    }

    float lastHealth() {
        return lastHealth;
    }

    void setLastHealth(float value) {
        this.lastHealth = Math.max(0.0F, value);
    }

    void save(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag root = persistent.getCompound(STORAGE_KEY);
        root.putDouble(TAG_AQUA, aqua);
        root.putDouble(TAG_AURA, aura);
        root.putDouble(TAG_IGNI, igni);
        root.putDouble(TAG_FIRMO, firmo);
        root.putFloat(TAG_LAST_HEALTH, lastHealth);
        root.putString(TAG_AQUA_TIER, aquaTier.name());
        root.putString(TAG_AURA_TIER, auraTier.name());
        root.putString(TAG_FIRMO_TIER, firmoTier.name());
        root.putString(TAG_IGNI_TIER, igniTier.name());
        root.remove(TAG_HOMEOSTASIS); // the old cascade queue: dropped from older saves
        persistent.put(STORAGE_KEY, root);
    }

    private static VitaData fromTag(CompoundTag tag, float fallbackHealth) {
        if (!tag.contains(TAG_AQUA)) {
            return createBalanced(fallbackHealth);
        }
        double aqua = tag.getDouble(TAG_AQUA);
        double aura = tag.getDouble(TAG_AURA);
        double igni = tag.getDouble(TAG_IGNI);
        double firmo = tag.getDouble(TAG_FIRMO);
        float lastHealth = tag.contains(TAG_LAST_HEALTH) ? tag.getFloat(TAG_LAST_HEALTH) : fallbackHealth;
        VitaData data = new VitaData(aqua, aura, igni, firmo, lastHealth);
        if (tag.contains(TAG_AQUA_TIER)) {
            try {
                data.aquaTier = VitaImbalanceTier.valueOf(tag.getString(TAG_AQUA_TIER));
            } catch (IllegalArgumentException ignored) {
                data.aquaTier = VitaImbalanceTier.BALANCED;
            }
        }
        if (tag.contains(TAG_AURA_TIER)) {
            try {
                data.auraTier = VitaImbalanceTier.valueOf(tag.getString(TAG_AURA_TIER));
            } catch (IllegalArgumentException ignored) {
                data.auraTier = VitaImbalanceTier.BALANCED;
            }
        }
        if (tag.contains(TAG_FIRMO_TIER)) {
            try {
                data.firmoTier = VitaImbalanceTier.valueOf(tag.getString(TAG_FIRMO_TIER));
            } catch (IllegalArgumentException ignored) {
                data.firmoTier = VitaImbalanceTier.BALANCED;
            }
        }
        if (tag.contains(TAG_IGNI_TIER)) {
            try {
                data.igniTier = VitaImbalanceTier.valueOf(tag.getString(TAG_IGNI_TIER));
            } catch (IllegalArgumentException ignored) {
                data.igniTier = VitaImbalanceTier.BALANCED;
            }
        }
        return data;
    }

    private static VitaData createBalanced(float health) {
        double total = Math.max(0.0F, health) * VitaSystem.UMU_PER_HP;
        VitaData data = new VitaData(0.0D, 0.0D, 0.0D, 0.0D, Math.max(0.0F, health));
        data.setBalancedValues(total);
        return data;
    }
}
