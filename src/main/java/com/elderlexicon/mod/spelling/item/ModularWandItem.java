package com.elderlexicon.mod.spelling.item;

import com.elderlexicon.mod.magic.lexicon.Foci;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Configurable wand that stores a wood module to augment capacity and discounts.
 */
public final class ModularWandItem extends SpellConduitItem {

    private static final String TAG_MATERIAL = "WandMaterial";
    private static final String DEFAULT_MATERIAL_ID = "oak";
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        Foci.Wood module = resolveModule(stack);
        tooltip.add(Component.translatable(
                        "tooltip.elderlexicon.wand.material",
                        Component.translatable(module.translationKey()))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected double initialCapacity(ItemStack stack) {
        return super.initialCapacity(stack) + resolveModule(stack).capacityBonus();
    }

    @Override
    public double maxCapacity(ItemStack stack) {
        return super.maxCapacity(stack) + resolveModule(stack).capacityBonus();
    }

    @Override
    protected double adjustEffectiveCost(ItemStack stack, double requested, @Nullable List<String> runes) {
        double adjusted = requested;
        adjusted -= computeRuneDiscount(runes, baseDiscounts);
        Foci.Wood module = resolveModule(stack);
        adjusted -= computeRuneDiscount(runes, module.discounts());
        adjusted -= Foci.conversionDiscount(runes, module.conversions(), this::runeCost);
        return Math.max(EPSILON, adjusted);
    }

    public void applyMaterial(ItemStack stack, String materialId) {
        stack.getOrCreateTag().putString(TAG_MATERIAL, sanitizeMaterial(materialId));
        resetCapacity(stack);
    }

    public String materialId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MATERIAL)) {
            return DEFAULT_MATERIAL_ID;
        }
        return sanitizeMaterial(tag.getString(TAG_MATERIAL));
    }

    /** The wood the wand is made of, as the foci data says of it (foci.json). */
    private Foci.Wood resolveModule(ItemStack stack) {
        Map<String, Foci.Wood> woods = Foci.woods();
        Foci.Wood wood = woods.get(materialId(stack));
        return wood != null ? wood : woods.getOrDefault(DEFAULT_MATERIAL_ID, NO_WOOD);
    }

    private static String sanitizeMaterial(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_MATERIAL_ID;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** What a wand is when its wood is not in the data: a plain wand, with nothing favoured. */
    private static final Foci.Wood NO_WOOD = new Foci.Wood(DEFAULT_MATERIAL_ID, "material.elderlexicon.wand.oak", 0.0D,
            Map.of(), Set.of());
}
