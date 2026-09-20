package com.elderlexicon.mod.item;

import com.elderlexicon.mod.vita.VitaProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable projection of Vita pools used by the Life Compass UI.
 */
public record LifeCompassSlices(double total,
                                double aqua,
                                double aura,
                                double igni,
                                double firmo,
                                double aquaOverflow,
                                double auraOverflow,
                                double igniOverflow,
                                double firmoOverflow) {

    private static final double EPSILON = 1.0E-4D;
    private static final String TAG_ROOT = "life_compass";
    private static final String TAG_TOTAL = "total";
    private static final String TAG_AQUA = "aqua";
    private static final String TAG_AURA = "aura";
    private static final String TAG_IGNI = "igni";
    private static final String TAG_FIRMO = "firmo";
    private static final String TAG_AQUA_OVERFLOW = "aquaOverflow";
    private static final String TAG_AURA_OVERFLOW = "auraOverflow";
    private static final String TAG_IGNI_OVERFLOW = "igniOverflow";
    private static final String TAG_FIRMO_OVERFLOW = "firmoOverflow";

    public static LifeCompassSlices empty() {
        return new LifeCompassSlices(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public static LifeCompassSlices fromProfile(VitaProfile profile) {
        if (profile == null || profile.totalUmu() <= EPSILON) {
            return empty();
        }
        return new LifeCompassSlices(
                profile.totalUmu(),
                profile.aqua(),
                profile.aura(),
                profile.igni(),
                profile.firmo(),
                profile.aquaOverflow(),
                profile.auraOverflow(),
                profile.igniOverflow(),
                profile.firmoOverflow());
    }

    public static LifeCompassSlices fromStack(ItemStack stack) {
        if (stack == null || !stack.hasTag()) {
            return empty();
        }
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG_ROOT)) {
            return empty();
        }
        CompoundTag payload = root.getCompound(TAG_ROOT);
        return new LifeCompassSlices(
            payload.getDouble(TAG_TOTAL),
            payload.getDouble(TAG_AQUA),
            payload.getDouble(TAG_AURA),
            payload.getDouble(TAG_IGNI),
            payload.getDouble(TAG_FIRMO),
            payload.getDouble(TAG_AQUA_OVERFLOW),
            payload.getDouble(TAG_AURA_OVERFLOW),
            payload.getDouble(TAG_IGNI_OVERFLOW),
            payload.getDouble(TAG_FIRMO_OVERFLOW)
        );
    }

    public void writeToStack(ItemStack stack) {
        if (stack == null) {
            return;
        }
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag payload = new CompoundTag();
        payload.putDouble(TAG_TOTAL, Math.max(0.0D, total));
        payload.putDouble(TAG_AQUA, Math.max(0.0D, aqua));
        payload.putDouble(TAG_AURA, Math.max(0.0D, aura));
        payload.putDouble(TAG_IGNI, Math.max(0.0D, igni));
        payload.putDouble(TAG_FIRMO, Math.max(0.0D, firmo));
        payload.putDouble(TAG_AQUA_OVERFLOW, Math.max(0.0D, aquaOverflow));
        payload.putDouble(TAG_AURA_OVERFLOW, Math.max(0.0D, auraOverflow));
        payload.putDouble(TAG_IGNI_OVERFLOW, Math.max(0.0D, igniOverflow));
        payload.putDouble(TAG_FIRMO_OVERFLOW, Math.max(0.0D, firmoOverflow));
        root.put(TAG_ROOT, payload);
    }

    public boolean isEmpty() {
        return total <= EPSILON;
    }

    public double aquaRatio() {
        return ratio(aqua);
    }

    public double auraRatio() {
        return ratio(aura);
    }

    public double igniRatio() {
        return ratio(igni);
    }

    public double firmoRatio() {
        return ratio(firmo);
    }

    public boolean hasOverflow() {
        return totalOverflow() > EPSILON;
    }

    public double totalOverflow() {
        return Math.max(0.0D, aquaOverflow + auraOverflow + igniOverflow + firmoOverflow);
    }

    public double aquaOverflowRatio() {
        return overflowRatio(aquaOverflow);
    }

    public double auraOverflowRatio() {
        return overflowRatio(auraOverflow);
    }

    public double igniOverflowRatio() {
        return overflowRatio(igniOverflow);
    }

    public double firmoOverflowRatio() {
        return overflowRatio(firmoOverflow);
    }

    private double ratio(double value) {
        if (total <= EPSILON) {
            return 0.0D;
        }
        return Math.max(0.0D, value) / total;
    }

    private double overflowRatio(double value) {
        double overflowTotal = totalOverflow();
        if (overflowTotal <= EPSILON) {
            return 0.0D;
        }
        return Math.max(0.0D, value) / overflowTotal;
    }
}
