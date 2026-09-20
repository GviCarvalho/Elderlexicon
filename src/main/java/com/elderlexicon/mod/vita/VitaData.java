package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;

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
    private static final String TAG_STEP_ELEMENT = "element";
    private static final String TAG_STEP_DELTA = "delta";
    private static final String TAG_AQUA_TIER = "aquaTier";
    private static final String TAG_AURA_TIER = "auraTier";
    private static final String TAG_FIRMO_TIER = "firmoTier";
    private static final String TAG_IGNI_TIER = "igniTier";

    private static final double EPSILON = 1.0E-4D;
    private static final VitaElement[] CYCLE_ORDER = {
            VitaElement.AQUA,
            VitaElement.IGNI,
            VitaElement.AURA,
            VitaElement.FIRMO
    };
    private static final double DIRECT_FACTOR = 0.45D;
    private static final double SECOND_FACTOR = 0.20D;
    private static final double TERTIARY_FACTOR = 0.10D;
    private static final double DAMPING_THRESHOLD = 0.05D;

    private double aqua;
    private double aura;
    private double igni;
    private double firmo;
    private float lastHealth;
    private final Deque<HomeostasisStep> homeostasisQueue = new ArrayDeque<>();
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

    void consume(double umuAmount) {
        if (umuAmount <= EPSILON) {
            return;
        }
        double perElement = umuAmount / 4.0D;
        aqua -= perElement;
        aura -= perElement;
        igni -= perElement;
        firmo -= perElement;
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
        modifyElement(target, delta, true);
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

    private void modifyElement(VitaElement element, double delta, boolean cascade) {
        if (element == null || element.isBalanced() || Math.abs(delta) <= EPSILON) {
            return;
        }
        double updated = Math.max(0.0D, get(element) + delta);
        setElement(element, updated);
        if (cascade) {
            queueHomeostasis(element, delta);
        }
    }

    private void queueHomeostasis(VitaElement element, double delta) {
        if (element == null || element.isBalanced() || Math.abs(delta) <= EPSILON) {
            return;
        }
        VitaElement direct = cycleStep(element, 1);
        VitaElement second = cycleStep(element, 2);
        VitaElement third = cycleStep(element, 3);

        enqueueStep(direct, -delta * DIRECT_FACTOR);
        enqueueStep(second, delta * SECOND_FACTOR);
        enqueueStep(third, -delta * TERTIARY_FACTOR);
    }

    private void enqueueStep(VitaElement element, double amount) {
        if (element == null || Math.abs(amount) < DAMPING_THRESHOLD) {
            return;
        }
        homeostasisQueue.addLast(new HomeostasisStep(element, amount));
    }

    private static VitaElement cycleStep(VitaElement element, int steps) {
        int index = cycleIndex(element);
        int nextIndex = Math.floorMod(index + steps, CYCLE_ORDER.length);
        return CYCLE_ORDER[nextIndex];
    }

    private static int cycleIndex(VitaElement element) {
        for (int i = 0; i < CYCLE_ORDER.length; i++) {
            if (CYCLE_ORDER[i] == element) {
                return i;
            }
        }
        return 0;
    }

    boolean runHomeostasisTick() {
        while (!homeostasisQueue.isEmpty()) {
            HomeostasisStep step = homeostasisQueue.pollFirst();
            if (step == null || Math.abs(step.delta()) <= EPSILON) {
                continue;
            }
            modifyElement(step.element(), step.delta(), false);
            return true;
        }
        return false;
    }

    private record HomeostasisStep(VitaElement element, double delta) {
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
        ListTag queueTag = new ListTag();
        for (HomeostasisStep step : homeostasisQueue) {
            CompoundTag stepTag = new CompoundTag();
            stepTag.putString(TAG_STEP_ELEMENT, step.element().name());
            stepTag.putDouble(TAG_STEP_DELTA, step.delta());
            queueTag.add(stepTag);
        }
        root.put(TAG_HOMEOSTASIS, queueTag);
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
        if (tag.contains(TAG_HOMEOSTASIS, Tag.TAG_LIST)) {
            ListTag queueTag = tag.getList(TAG_HOMEOSTASIS, Tag.TAG_COMPOUND);
            for (int i = 0; i < queueTag.size(); i++) {
                CompoundTag stepTag = queueTag.getCompound(i);
                String elementName = stepTag.getString(TAG_STEP_ELEMENT);
                double delta = stepTag.getDouble(TAG_STEP_DELTA);
                try {
                    VitaElement element = VitaElement.valueOf(elementName);
                    if (element != null && !element.isBalanced() && Math.abs(delta) > EPSILON) {
                        data.homeostasisQueue.addLast(new HomeostasisStep(element, delta));
                    }
                } catch (IllegalArgumentException ignored) {
                }
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
