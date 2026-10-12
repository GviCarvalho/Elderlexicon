package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.Composition;
import com.elderlexicon.mod.spell.life.Beings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Persistent Vita storage per living being, allowing imbalances across elemental components. The body's balance is its
 * core (docs/particulas-design.md, section 6): the share of each element in its life; the body heals, hurts and settles
 * back in that proportion.
 * <p>
 * A player's core is a person's 55/38/2/5 until a vertere changes it, and is kept here. Any other creature's is its
 * kind's in the table, or the one a transformation gave it ({@link Beings#coreOf}), read anew each time; a person's when
 * the table has none (docs/feiticeiro-design.md, 7.4). A creature's body is only kept while it is out of whole: a whole one
 * has no Vita written down, and reads as full.
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
    /** Set once the body is read as a chalice (docs/vita-design.md): older saves are filled up to their health once. */
    private static final String TAG_CHALICE = "chalice";

    private static final double EPSILON = 1.0E-4D;
    private static final VitaElement[] ELEMENTS = {VitaElement.AQUA, VitaElement.AURA, VitaElement.IGNI, VitaElement.FIRMO};
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
    /** The most health the body has: the chalice holds five UMU for each point of it. Not saved: read with the body. */
    private float maxHealth = 20.0F;
    private boolean chalice;

    private VitaData(double aqua, double aura, double igni, double firmo, float lastHealth) {
        this.aqua = aqua;
        this.aura = aura;
        this.igni = igni;
        this.firmo = firmo;
        this.lastHealth = lastHealth;
    }

    static VitaData get(LivingEntity living) {
        CompoundTag persistent = living.getPersistentData();
        CompoundTag root = persistent.getCompound(STORAGE_KEY);
        Optional<Composition> kind = living instanceof Player ? Optional.empty() : Beings.coreOf(living);
        if (root.isEmpty()) {
            VitaData data = new VitaData(0.0D, 0.0D, 0.0D, 0.0D, Math.max(0.0F, living.getHealth()));
            kind.ifPresent(data::takeCore);
            data.setBalancedValues(Math.max(0.0F, living.getHealth()) * VitaSystem.UMU_PER_HP);
            data.maxHealth = Math.max(1.0F, living.getMaxHealth());
            data.chalice = true;
            data.save(living);
            return data;
        }
        VitaData data = fromTag(root, living.getHealth());
        kind.ifPresent(data::takeCore);
        data.maxHealth = Math.max(1.0F, living.getMaxHealth());
        return data;
    }

    /** The body of {@code living} as it is, read without writing anything down (a whole creature's reads as full). */
    static VitaData peek(LivingEntity living) {
        CompoundTag root = living.getPersistentData().getCompound(STORAGE_KEY);
        Optional<Composition> kind = living instanceof Player ? Optional.empty() : Beings.coreOf(living);
        VitaData data;
        if (root.isEmpty()) {
            data = new VitaData(0.0D, 0.0D, 0.0D, 0.0D, Math.max(0.0F, living.getHealth()));
            kind.ifPresent(data::takeCore);
            data.setBalancedValues(Math.max(0.0F, living.getHealth()) * VitaSystem.UMU_PER_HP);
            data.chalice = true;
        } else {
            data = fromTag(root, living.getHealth());
            kind.ifPresent(data::takeCore);
        }
        data.maxHealth = Math.max(1.0F, living.getMaxHealth());
        return data;
    }

    /** Whether {@code living} has a body written down (a creature's is only while it is out of whole). */
    static boolean kept(LivingEntity living) {
        return living.getPersistentData().contains(STORAGE_KEY);
    }

    /** A creature whole again: its body is no longer kept, and reads as full. */
    static void forget(LivingEntity living) {
        living.getPersistentData().remove(STORAGE_KEY);
    }

    /** Whether the body is whole: every element at its full, none beyond it, and the life at its most. */
    boolean whole() {
        if (lastHealth + EPSILON < maxHealth) {
            return false;
        }
        for (VitaElement element : ELEMENTS) {
            if (Math.abs(get(element) - full(element)) > 0.01D) {
                return false;
            }
        }
        return true;
    }

    /** The core of a creature's kind, which the table (or a transformation) says: it is not the body's to keep. */
    private void takeCore(Composition core) {
        coreAqua = core.share(VitaElement.AQUA);
        coreAura = core.share(VitaElement.AURA);
        coreIgni = core.share(VitaElement.IGNI);
        coreFirmo = core.share(VitaElement.FIRMO);
    }

    // ------------------------------------------------------------------ the chalice (docs/vita-design.md)

    /** What the body holds when whole: five UMU for each point of its most health. */
    double capacity() {
        return maxHealth * VitaSystem.UMU_PER_HP;
    }

    float maxHealth() {
        return maxHealth;
    }

    /** What the body holds of {@code element} when whole: its share of the chalice. */
    double full(VitaElement element) {
        return capacity() * share(element);
    }

    /**
     * How whole the body is: the share of its full that the element most lacking holds (the life follows it). An
     * element the core has none of does not count.
     */
    double wholeness() {
        double least = Double.MAX_VALUE;
        for (VitaElement element : ELEMENTS) {
            double full = full(element);
            if (full > EPSILON) {
                least = Math.min(least, get(element) / full);
            }
        }
        return least == Double.MAX_VALUE ? 1.0D : Math.max(0.0D, least);
    }

    /** An element hurt: it is left with at most {@code level} of its full. */
    void dropTo(VitaElement element, double level) {
        if (element == null || element.isBalanced()) {
            return;
        }
        setElement(element, Math.min(get(element), Math.max(0.0D, level) * full(element)));
    }

    /** A hurt with no nature: every element loses {@code fraction} of its full. */
    void loseShare(double fraction) {
        for (VitaElement element : ELEMENTS) {
            setElement(element, Math.max(0.0D, get(element) - fraction * full(element)));
        }
    }

    /** A healing: what lacks is filled first, every element up to {@code level} of its full. */
    void healTo(double level) {
        for (VitaElement element : ELEMENTS) {
            setElement(element, Math.max(get(element), Math.min(1.0D, level) * full(element)));
        }
    }

    /**
     * The body expels what it holds beyond its full (homeostasis): each element above it moves {@code fraction} of
     * the way back. What lacks is not filled: only healing fills it.
     *
     * @return whether anything moved
     */
    boolean expelExcess(double fraction) {
        boolean moved = false;
        for (VitaElement element : ELEMENTS) {
            double excess = get(element) - full(element);
            if (excess <= EPSILON) {
                continue;
            }
            double step = Math.max(excess * fraction, Math.min(excess, MIN_RELAX_STEP));
            setElement(element, get(element) - step);
            moved = true;
        }
        return moved;
    }

    /**
     * The chalice holds no more than its capacity: what came into {@code added} beyond it pushes the others out, each
     * in proportion to what it holds (with {@code added} null, all of them give way together).
     */
    void contain(VitaElement added) {
        double overflow = totalUmu() - capacity();
        if (overflow <= EPSILON) {
            return;
        }
        if (added == null || added.isBalanced()) {
            double keep = capacity() / totalUmu();
            for (VitaElement element : ELEMENTS) {
                setElement(element, get(element) * keep);
            }
            return;
        }
        double others = totalUmu() - get(added);
        if (others <= EPSILON || overflow >= others) {
            for (VitaElement element : ELEMENTS) {
                if (element != added) {
                    setElement(element, 0.0D);
                }
            }
            setElement(added, Math.min(get(added), capacity()));
            return;
        }
        double keep = (others - overflow) / others;
        for (VitaElement element : ELEMENTS) {
            if (element != added) {
                setElement(element, get(element) * keep);
            }
        }
    }

    /** Whether the body was read as a chalice before (an older save is not, once). */
    boolean chalice() {
        return chalice;
    }

    void setChalice() {
        chalice = true;
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
        contain(null);
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
        if (delta > 0.0D) {
            contain(element);
        }
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

    void save(LivingEntity living) {
        CompoundTag persistent = living.getPersistentData();
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
        root.putBoolean(TAG_CHALICE, chalice);
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
        data.chalice = tag.getBoolean(TAG_CHALICE);
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

    /** A whole body of {@code maxHealth}, with {@code health} of it, balanced: for the tests of the chalice. */
    static VitaData balanced(float health, float maxHealth) {
        VitaData data = createBalanced(health);
        data.maxHealth = maxHealth;
        data.chalice = true;
        return data;
    }

    private static VitaData createBalanced(float health) {
        double total = Math.max(0.0F, health) * VitaSystem.UMU_PER_HP;
        VitaData data = new VitaData(0.0D, 0.0D, 0.0D, 0.0D, Math.max(0.0F, health));
        data.setBalancedValues(total);
        return data;
    }
}
