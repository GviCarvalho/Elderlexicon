package com.elderlexicon.mod.spelling.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A wand for testing, only found in the creative tab: it conducts any spell, however costly, never wears and
 * never breaks, so nothing flows through the caster's body (no nausea). The spell's cost is still paid by the
 * caster as usual, so what a spell costs can be read while testing it.
 */
public final class CreativeWandItem extends SpellConduitItem {

    public CreativeWandItem(Properties properties) {
        super(properties.stacksTo(1), 1.0D);
    }

    @Override
    public double conduct(ItemStack stack, double requested, @Nullable Player holder, @Nullable List<String> runes) {
        return 0.0D;
    }

    @Override
    public double remainingCapacity(ItemStack stack) {
        return Double.MAX_VALUE;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    protected Component descriptionComponent(ItemStack stack) {
        return Component.translatable("tooltip.elderlexicon.creative_wand");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level, List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(descriptionComponent(stack).copy().withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
    }
}
