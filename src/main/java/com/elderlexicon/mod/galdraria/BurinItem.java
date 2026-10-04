package com.elderlexicon.mod.galdraria;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The burin of galdraria (docs/galdraria-design.md): the tool that carves runes into things at the galdraria table.
 * Each rune carved wears it by one; mended at the anvil with iron.
 */
public final class BurinItem extends Item {

    /** How many runes a burin carves before it is worn out. */
    public static final int DURABILITY = 100;

    public BurinItem(Properties properties) {
        super(properties.durability(DURABILITY));
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairWith) {
        return repairWith.is(Items.IRON_INGOT);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.elderlexicon.burin",
                stack.getMaxDamage() - stack.getDamageValue()).withStyle(ChatFormatting.GRAY));
    }
}
