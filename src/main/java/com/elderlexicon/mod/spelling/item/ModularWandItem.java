package com.elderlexicon.mod.spelling.item;

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
        WoodModule module = resolveModule(stack);
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
        WoodModule module = resolveModule(stack);
        adjusted -= computeRuneDiscount(runes, module.runeDiscounts());
        adjusted -= module.special().apply(runes, this);
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

    private WoodModule resolveModule(ItemStack stack) {
        return MODULES.getOrDefault(materialId(stack), MODULES.get(DEFAULT_MATERIAL_ID));
    }

    private static String sanitizeMaterial(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_MATERIAL_ID;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private double vertereDiscount(@Nullable List<String> runes, Set<String> targets) {
        if (runes == null || runes.isEmpty() || targets.isEmpty()) {
            return 0.0D;
        }
        double total = 0.0D;
        for (int i = 0; i < runes.size() - 1; i++) {
            String current = normalizeRuneId(runes.get(i));
            if (!"vertere".equals(current)) {
                continue;
            }
            String next = normalizeRuneId(runes.get(i + 1));
            if (targets.contains(next)) {
                total += runeCost("vertere");
            }
        }
        return total;
    }

    private static final Map<String, WoodModule> MODULES = Map.ofEntries(
            Map.entry("oak", new WoodModule("oak", "material.elderlexicon.wand.oak", 30.0D, Map.of(), SpecialHandler.NONE)),
            Map.entry("birch", new WoodModule("birch", "material.elderlexicon.wand.birch", 22.0D,
                    Map.of("iactare", 0.10D), SpecialHandler.NONE)),
            Map.entry("mangrove", new WoodModule("mangrove", "material.elderlexicon.wand.mangrove", 24.0D,
                    Map.of("vertere", 0.10D), SpecialHandler.NONE)),
            Map.entry("acacia", new WoodModule("acacia", "material.elderlexicon.wand.acacia", 26.0D,
                    Map.of("impediunt", 0.10D), SpecialHandler.NONE)),
            Map.entry("dark_oak", new WoodModule("dark_oak", "material.elderlexicon.wand.dark_oak", 27.0D,
                    Map.of("impediunt", 0.05D), SpecialHandler.NONE)),
            Map.entry("cherry", new WoodModule("cherry", "material.elderlexicon.wand.cherry", 25.0D,
                    Map.of("vocant", 0.10D), SpecialHandler.NONE)),
            Map.entry("spruce", new WoodModule("spruce", "material.elderlexicon.wand.spruce", 23.0D,
                    Map.of("exsugat", 0.10D), SpecialHandler.NONE)),
            Map.entry("crimson", new WoodModule("crimson", "material.elderlexicon.wand.crimson", 40.0D,
                    Map.of(), (runes, item) -> item.vertereDiscount(runes, Set.of("igni", "aura")))),
            Map.entry("warped", new WoodModule("warped", "material.elderlexicon.wand.warped", 40.0D,
                    Map.of(), (runes, item) -> item.vertereDiscount(runes, Set.of("firmo", "aqua"))))
    );

    private record WoodModule(String id,
                              String translationKey,
                              double capacityBonus,
                              Map<String, Double> runeDiscounts,
                              SpecialHandler special) {
    }

    @FunctionalInterface
    private interface SpecialHandler {
        SpecialHandler NONE = (runes, item) -> 0.0D;

        double apply(@Nullable List<String> runes, ModularWandItem item);
    }
}
