package com.elderlexicon.mod.invocation;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Matter the invocation bench holds with no name of the game (docs/invocacao-design.md, section 2.1), and the formats
 * that are only matter with a form (a cube, a disc, a sphere): they do nothing but be what they are, thrown or brought
 * as any marked thing is.
 */
public final class ConjuredItem extends Item {

    public ConjuredItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return ConjuredLook.name(getDescriptionId(), stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ConjuredLook.tooltip(stack, tooltip);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        ConjuredLook.tick(stack, level, holder);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        return ConjuredLook.tickLying(stack, entity);
    }
}
