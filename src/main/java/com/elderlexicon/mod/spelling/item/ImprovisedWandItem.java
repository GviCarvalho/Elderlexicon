package com.elderlexicon.mod.spelling.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A wand that is only a haste (a stick, bone, bamboo or blaze rod): it conducts up to its capacity and may have a gem
 * set in it, but is held by nothing.
 */
public class ImprovisedWandItem extends WandItem {

    private final String tooltipKey;
    private final Map<String, Double> runeDiscounts;

    public ImprovisedWandItem(Properties properties) {
        this(properties, 6D, "tooltip.elderlexicon.improvised_wand");
    }

    public ImprovisedWandItem(Properties properties, double capacity, String tooltipKey) {
        this(properties, capacity, tooltipKey, Map.of());
    }

    public ImprovisedWandItem(Properties properties,
                              double capacity,
                              String tooltipKey,
                              Map<String, Double> runeDiscounts) {
        super(properties.stacksTo(1), capacity);
        this.tooltipKey = tooltipKey;
        this.runeDiscounts = Map.copyOf(normalizeDiscountMap(runeDiscounts));
    }

    @Override
    protected Component descriptionComponent(ItemStack stack) {
        return Component.translatable(tooltipKey, formatAmount(maxCapacity(stack)));
    }

    @Override
    protected double adjustEffectiveCost(ItemStack stack, double requested, @Nullable List<String> runes) {
        double discount = computeRuneDiscount(runes, runeDiscounts);
        return Math.max(EPSILON, requested - discount);
    }
}
