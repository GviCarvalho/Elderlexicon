package com.elderlexicon.mod.spelling.item;

import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Base item capable of absorbing a fixed amount of UMU before breaking.
 * Subclasses can extend the description while reusing the conduction logic.
 */
public class SpellConduitItem extends Item {

    private static final String REMAINING_KEY = "ConduitRemaining";
    protected static final double EPSILON = 1.0E-4D;
    protected static final double DEFAULT_SOURCE_COST = 1.0D;
    private static final Map<String, Double> DEFAULT_FUNCTION_COSTS = Map.of(
            "iactare", 2.0D,
            "vocant", 1.0D,
            "vertere", 1.0D
    );
    private final double capacity;

    public SpellConduitItem(Properties properties, double capacity) {
        super(properties.durability(Math.max(1, (int) Math.ceil(capacity))));
        this.capacity = Math.max(0D, capacity);
    }

    /**
     * Attempts to conduct the requested UMU through this conduit.
     *
     * @param stack     the conduit stack
     * @param requested the UMU that should be absorbed
     * @param holder    the player holding the conduit (may be {@code null})
     * @return UMU that could not be absorbed and should be applied to the player
     */
    public double conduct(ItemStack stack,
                          double requested,
                          @Nullable Player holder,
                          @Nullable List<String> runes) {
        if (requested <= 0D || stack.isEmpty()) {
            return requested;
        }
        double effectiveRequested = adjustEffectiveCost(stack, requested, runes);
        double sanitizedEffective = Math.max(EPSILON, effectiveRequested);
        double remaining = readRemaining(stack);
        if (remaining <= 0D) {
            destroy(stack, holder);
            return requested;
        }
        double maxEffectiveAbsorbable = Math.min(remaining, sanitizedEffective);
        double absorbedActual = requested * (maxEffectiveAbsorbable / sanitizedEffective);
        double leftover = Math.max(0.0D, requested - absorbedActual);
        remaining -= maxEffectiveAbsorbable;
        writeRemaining(stack, remaining);
        updateVisualDamage(stack, remaining);
        if (remaining <= EPSILON) {
            destroy(stack, holder);
        }
        return leftover;
    }

    protected double adjustEffectiveCost(ItemStack stack, double requested, @Nullable List<String> runes) {
        return requested;
    }

    /**
     * True when the player holds a conduit that still has capacity. A spell cast this way runs
     * through the conduit and must not touch the caster's Vita reserves.
     */
    public static boolean holdsReadyConduit(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        return isReady(player.getMainHandItem()) || isReady(player.getOffhandItem());
    }

    private static boolean isReady(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof SpellConduitItem conduit
                && conduit.remainingCapacity(stack) > EPSILON;
    }

    public double remainingCapacity(ItemStack stack) {
        return readRemaining(stack);
    }

    protected void destroy(ItemStack stack, @Nullable Player holder) {
        stack.shrink(1);
        if (holder != null) {
            holder.broadcastBreakEvent(resolveHand(holder, stack));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(descriptionComponent(stack).copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                        "tooltip.elderlexicon.spell_conduit.remaining",
                        formatAmount(Math.max(0D, remainingCapacity(stack))))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    private void updateVisualDamage(ItemStack stack, double remaining) {
        double resolvedCapacity = maxCapacity(stack);
        if (resolvedCapacity <= 0D) {
            stack.setDamageValue(0);
            return;
        }
        int maxDamage = getMaxDamage(stack);
        if (maxDamage <= 0) {
            return;
        }
        double used = Math.max(0D, resolvedCapacity - remaining);
        double fraction = Math.min(1D, Math.max(0D, used / resolvedCapacity));
        int damageValue = (int) Math.round(fraction * maxDamage);
        stack.setDamageValue(damageValue);
    }

    private double readRemaining(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(REMAINING_KEY)) {
            double cap = maxCapacity(stack);
            tag.putDouble(REMAINING_KEY, cap);
            return cap;
        }
        return tag.getDouble(REMAINING_KEY);
    }

    private void writeRemaining(ItemStack stack, double remaining) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putDouble(REMAINING_KEY, Math.max(0D, remaining));
    }

    private InteractionHand resolveHand(Player holder, ItemStack stack) {
        if (holder.getMainHandItem() == stack) {
            return InteractionHand.MAIN_HAND;
        }
        if (holder.getOffhandItem() == stack) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    protected final String formatAmount(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001D) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public double maxCapacity() {
        return capacity;
    }

    public double maxCapacity(ItemStack stack) {
        return Math.max(0.0D, initialCapacity(stack));
    }

    protected double initialCapacity(ItemStack stack) {
        return capacity;
    }

    protected Component descriptionComponent(ItemStack stack) {
        return Component.translatable("tooltip.elderlexicon.spell_conduit.capacity", formatAmount(maxCapacity(stack)));
    }

    protected Map<String, Double> normalizeDiscountMap(Map<String, Double> raw) {
        Map<String, Double> sanitized = new HashMap<>();
        if (raw == null) {
            return sanitized;
        }
        raw.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value <= 0.0D) {
                return;
            }
            sanitized.put(normalizeRuneId(key), Math.min(1.0D, value));
        });
        return sanitized;
    }

    protected double computeRuneDiscount(@Nullable List<String> runes, Map<String, Double> discounts) {
        if (runes == null || runes.isEmpty() || discounts == null || discounts.isEmpty()) {
            return 0.0D;
        }
        double total = 0.0D;
        for (String rune : runes) {
            String normalized = normalizeRuneId(rune);
            if (normalized.isEmpty()) {
                continue;
            }
            Double percent = discounts.get(normalized);
            if (percent == null || percent <= 0.0D) {
                continue;
            }
            double baseCost = runeCost(normalized);
            if (baseCost <= 0.0D) {
                continue;
            }
            total += baseCost * percent;
        }
        return total;
    }

    protected double runeCost(String runeId) {
        String normalized = normalizeRuneId(runeId);
        if (normalized.isEmpty()) {
            return 0.0D;
        }
        Double functionCost = DEFAULT_FUNCTION_COSTS.get(normalized);
        if (functionCost != null) {
            return functionCost;
        }
        VitaElement element = VitaElement.fromRuneId(normalized);
        if (element != null && !element.isBalanced()) {
            return DEFAULT_SOURCE_COST;
        }
        return 0.0D;
    }

    protected String normalizeRuneId(String runeId) {
        if (runeId == null) {
            return "";
        }
        return runeId.trim().toLowerCase(Locale.ROOT);
    }

    protected void resetCapacity(ItemStack stack) {
        stack.removeTagKey(REMAINING_KEY);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return maxCapacity(stack) > EPSILON;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        double max = maxCapacity(stack);
        if (max <= EPSILON) {
            return 0;
        }
        double remaining = Math.max(0.0D, Math.min(max, remainingCapacity(stack)));
        return Math.round(13.0F * (float) (remaining / max));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        double max = maxCapacity(stack);
        double remaining = Math.max(0.0D, Math.min(max, remainingCapacity(stack)));
        float ratio = max <= EPSILON ? 0.0F : (float) (remaining / max);
        return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F);
    }
}
