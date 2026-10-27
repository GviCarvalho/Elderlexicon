package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.magic.matter.Qualities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An arrow whose tip the invocation bench made of any matter (docs/invocacao-design.md, section 3): it strikes as hard
 * as its tip weighs against iron, and a hot tip flies burning.
 */
public final class ConjuredArrowItem extends ArrowItem {

    /** The arrow of the game hits for 2; one tipped with iron, as much. */
    private static final double IRON_DAMAGE = 2.0D;
    private static final int BURNING_SECONDS = 100;

    public ConjuredArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter) {
        Arrow arrow = new Arrow(level, shooter);
        double ofIron = Formats.all().containsKey("arrow") ? Formats.all().get("arrow").damage() : IRON_DAMAGE;
        arrow.setBaseDamage(Math.max(0.5D, ConjuredToolItem.damage(ofIron, stack)));
        Qualities qualities = Conjured.qualities(Conjured.matter(stack));
        if (qualities.hot()) {
            arrow.setSecondsOnFire(BURNING_SECONDS);
        }
        return arrow;
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
