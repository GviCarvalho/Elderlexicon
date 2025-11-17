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

    private static final double EPSILON = 1.0E-4D;

    private double aqua;
    private double aura;
    private double igni;
    private double firmo;
    private float lastHealth;

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
        return new VitaProfile(totalUmu(), aqua, aura, igni, firmo);
    }

    double totalUmu() {
        return aqua + aura + igni + firmo;
    }

    double get(VitaElement element) {
        VitaElement target = element == null ? VitaElement.BALANCED : element;
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

        VitaElement target = element == null ? VitaElement.BALANCED : element;
        if (target.isBalanced()) {
            consume(umuAmount);
            return;
        }

        double available = get(target);
        setElement(target, available - umuAmount);
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
        VitaElement target = element == null ? VitaElement.BALANCED : element;
        if (target.isBalanced()) {
            restoreAndStabilize(delta);
            return;
        }
        setElement(target, get(target) + delta);
    }

    void addElementEnergy(VitaElement element, double amount) {
        if (amount <= EPSILON) {
            return;
        }
        adjustElement(element, amount);
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
        return new VitaData(aqua, aura, igni, firmo, lastHealth);
    }

    private static VitaData createBalanced(float health) {
        double total = Math.max(0.0F, health) * VitaSystem.UMU_PER_HP;
        VitaData data = new VitaData(0.0D, 0.0D, 0.0D, 0.0D, Math.max(0.0F, health));
        data.setBalancedValues(total);
        return data;
    }
}
