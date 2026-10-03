package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Persistent Vita storage per player, allowing imbalances across elemental components. The body's balance is its core
 * (docs/particulas-design.md, section 6): the share of each element in its life, a person's 55/38/2/5 until a vertere
 * changes it; the body heals, hurts and settles back in that proportion.
 */
final class VitaData {

    private static final String STORAGE_KEY = ElderLexicon.MODID + "_vita";
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
    private static final String TAG_CORE = "core";

    private static final double EPSILON = 1.0E-4D;
    /** Smallest step of the return to balance, so the last bit of an imbalance does not linger forever. */
    private static final double MIN_RELAX_STEP = 0.01D;

    private double aqua;
    private double aura;
    private double igni;
    private double firmo;
    private float lastHealth;
    /** The core: the share of the life each element is, a person's until a vertere changes it. */
    private double coreAqua = VitaSystem.AQUA_RATIO;
    private double coreAura = VitaSystem.AURA_RATIO;
    private double coreIgni = VitaSystem.IGNI_RATIO;
    private double coreFirmo = VitaSystem.FIRMO_RATIO;
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

    /** The share of the life {@code element} is in this body's core. */
    double share(VitaElement element) {
        return switch (sanitize(element)) {
            case AQUA -> coreAqua;
            case AURA -> coreAura;
            case IGNI -> coreIgni;
            case FIRMO -> coreFirmo;
            case BALANCED -> 1.0D;
        };
    }

    /**
     * A new core (docs/particulas-design.md, stage 6): the shares are kept, normalized to the whole life, and what the
     * body holds is converted into them, as much in all as before.
     */
    void reshape(double aquaShare, double auraShare, double igniShare, double firmoShare) {
        double total = Math.max(0.0D, aquaShare) + Math.max(0.0D, auraShare) + Math.max(0.0D, igniShare)
                + Math.max(0.0D, firmoShare);
        if (total <= EPSILON) {
            return;
        }
        coreAqua = Math.max(0.0D, aquaShare) / total;
        coreAura = Math.max(0.0D, auraShare) / total;
        coreIgni = Math.max(0.0D, igniShare) / total;
        coreFirmo = Math.max(0.0D, firmoShare) / total;
        setBalancedValues(totalUmu());
    }

    /**
     * Life lost takes each element in its share of life (the core), the same way healing gives it back, so being hurt and
     * healed never unbalances the body by itself.
     */
    void consume(double umuAmount) {
        if (umuAmount <= EPSILON) {
            return;
        }
        aqua = Math.max(0.0D, aqua - umuAmount * coreAqua);
        aura = Math.max(0.0D, aura - umuAmount * coreAura);
        igni = Math.max(0.0D, igni - umuAmount * coreIgni);
        firmo = Math.max(0.0D, firmo - umuAmount * coreFirmo);
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
        aqua += umuAmount * coreAqua;
        aura += umuAmount * coreAura;
        igni += umuAmount * coreIgni;
        firmo += umuAmount * coreFirmo;
    }

    void setBalancedValues(double totalUmu) {
        aqua = totalUmu * coreAqua;
        aura = totalUmu * coreAura;
        igni = totalUmu * coreIgni;
        firmo = totalUmu * coreFirmo;
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
        return baselineTotal() * share(element);
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
        CompoundTag core = new CompoundTag();
        core.putDouble(TAG_AQUA, coreAqua);
        core.putDouble(TAG_AURA, coreAura);
        core.putDouble(TAG_IGNI, coreIgni);
        core.putDouble(TAG_FIRMO, coreFirmo);
        root.put(TAG_CORE, core);
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
        if (tag.contains(TAG_CORE)) {
            CompoundTag core = tag.getCompound(TAG_CORE);
            double total = core.getDouble(TAG_AQUA) + core.getDouble(TAG_AURA) + core.getDouble(TAG_IGNI)
                    + core.getDouble(TAG_FIRMO);
            if (total > EPSILON) {
                data.coreAqua = core.getDouble(TAG_AQUA) / total;
                data.coreAura = core.getDouble(TAG_AURA) / total;
                data.coreIgni = core.getDouble(TAG_IGNI) / total;
                data.coreFirmo = core.getDouble(TAG_FIRMO) / total;
            }
        }
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
