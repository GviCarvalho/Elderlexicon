package com.elderlexicon.mod.spelling.item;

import com.elderlexicon.mod.magic.lexicon.Foci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A wand held by a grip (a wood or a metal, docs/varinhas-design.md): the grip adds its capacity and what it favours.
 * The grip is chosen when the wand is made and stays until the wand breaks.
 */
public final class ModularWandItem extends WandItem {

    /** Where the grip is kept; the name is from when only woods held a wand. */
    private static final String TAG_GRIP = "WandMaterial";
    private static final String DEFAULT_GRIP_ID = "oak";
    private final String tooltipKey;
    private final Map<String, Double> baseDiscounts;

    public ModularWandItem(Properties properties,
                           double baseCapacity,
                           String tooltipKey,
                           Map<String, Double> baseDiscounts) {
        super(properties.stacksTo(1), baseCapacity);
        this.tooltipKey = tooltipKey;
        this.baseDiscounts = Map.copyOf(normalizeDiscountMap(baseDiscounts));
    }

    @Override
    protected Component descriptionComponent(ItemStack stack) {
        return Component.translatable(tooltipKey, formatAmount(maxCapacity(stack)));
    }

    @Override
    public Optional<Foci.Grip> grip(ItemStack stack) {
        return Optional.of(resolveGrip(stack));
    }

    @Override
    protected double initialCapacity(ItemStack stack) {
        return super.initialCapacity(stack) + resolveGrip(stack).capacityBonus();
    }

    @Override
    public double maxCapacity(ItemStack stack) {
        return super.maxCapacity(stack) + resolveGrip(stack).capacityBonus();
    }

    @Override
    protected double adjustEffectiveCost(ItemStack stack, double requested, @Nullable List<String> runes) {
        double adjusted = requested;
        adjusted -= computeRuneDiscount(runes, baseDiscounts);
        Foci.Grip grip = resolveGrip(stack);
        adjusted -= computeRuneDiscount(runes, grip.discounts());
        adjusted -= Foci.conversionDiscount(runes, grip.conversions(), this::runeCost);
        return Math.max(EPSILON, adjusted);
    }

    public void applyGrip(ItemStack stack, String gripId) {
        stack.getOrCreateTag().putString(TAG_GRIP, sanitizeGrip(gripId));
        resetCapacity(stack);
    }

    public String gripId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_GRIP)) {
            return DEFAULT_GRIP_ID;
        }
        return sanitizeGrip(tag.getString(TAG_GRIP));
    }

    /** The grip the wand is held by, as the foci data says of it (foci.json). */
    private Foci.Grip resolveGrip(ItemStack stack) {
        Map<String, Foci.Grip> grips = Foci.grips();
        Foci.Grip grip = grips.get(gripId(stack));
        return grip != null ? grip : grips.getOrDefault(DEFAULT_GRIP_ID, NO_GRIP);
    }

    private static String sanitizeGrip(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_GRIP_ID;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** What a wand is when its grip is not in the data: a plain wand, with nothing favoured. */
    private static final Foci.Grip NO_GRIP = new Foci.Grip(DEFAULT_GRIP_ID, "material.elderlexicon.wand.oak", 0.0D,
            Map.of(), Set.of(), "", "", Foci.NO_COLOR);
}
