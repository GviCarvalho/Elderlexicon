package com.elderlexicon.mod.invocation;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A shield whose body the invocation bench made of any matter (docs/invocacao-design.md, section 3). */
public final class ConjuredShieldItem extends ShieldItem {

    public ConjuredShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId();
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
