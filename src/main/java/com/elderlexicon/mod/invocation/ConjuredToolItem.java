package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.magic.matter.Qualities;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A weapon or tool the invocation bench made of any matter (docs/invocacao-design.md, section 3): a sword of fire, a
 * pickaxe of water. What it does comes from what it is made of, by the qualities any matter has: it cuts as hard as it
 * weighs against iron, the hot burns what it strikes as hard as iron would cut, the wet puts fire out, the light throws
 * back; a tool digs as fast as it weighs, and only what its weight can break gives what it is made of.
 */
public final class ConjuredToolItem extends Item {

    /** A tool of iron digs this fast. */
    private static final float IRON_SPEED = 6.0F;
    /** How long the hot sets what it strikes burning. */
    private static final int BURNING_SECONDS = 4;

    private final String format;

    public ConjuredToolItem(Properties properties, String format) {
        super(properties);
        this.format = format;
    }

    private Optional<Format> format() {
        return Optional.ofNullable(Formats.all().get(format));
    }

    /** How much it weighs against iron. */
    static double weight(ItemStack stack) {
        // Its mass against iron's in the same room: how heavy a UMU of it is, times how many UMU are pressed into each
        // square (a sword of iron ingots holds ten in each).
        return Conjured.qualities(Conjured.matter(stack)).density() * Conjured.packing(stack)
                / (ConjuredLook.IRON_DENSITY * ConjuredLook.ironPacking());
    }

    /** The bonus to the hand: as much as iron's, by the weight; the hot adds as much again in heat. */
    static double damage(double ofIron, ItemStack stack) {
        Qualities qualities = Conjured.qualities(Conjured.matter(stack));
        // The fire pressed into each square is its heat (one UMU of it is a common flame): hotter, it burns harder, by
        // the logarithm, as every heat grows (docs/condensacao-design.md, "Tudo escala").
        double heat = qualities.heat() * Conjured.packing(stack);
        return ofIron * weight(stack)
                + (qualities.hot() ? ofIron * qualities.heat() * (1.0D + Math.log(Math.max(1.0D, heat))) : 0.0D);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND || format().isEmpty()) {
            return super.getAttributeModifiers(slot, stack);
        }
        Format made = format().get();
        return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Conjured weapon",
                        damage(made.damage(), stack), AttributeModifier.Operation.ADDITION))
                .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Conjured weapon",
                        made.speed(), AttributeModifier.Operation.ADDITION))
                .build();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Qualities qualities = Conjured.qualities(Conjured.matter(stack));
        if (qualities.hot()) {
            target.setSecondsOnFire(BURNING_SECONDS);
        } else if (qualities.wet()) {
            target.clearFire();
        }
        if (qualities.light()) {
            target.knockback(qualities.lightness(), attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
        }
        stack.hurtAndBreak(1, attacker, broken -> broken.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(1, miner, broken -> broken.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        return true;
    }

    private Optional<TagKey<Block>> mines() {
        return format().map(Format::mines).filter(tag -> tag.startsWith("#"))
                .map(tag -> TagKey.create(Registries.BLOCK, ResourceLocation.tryParse(tag.substring(1))));
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        Optional<TagKey<Block>> mines = mines();
        if (mines.isEmpty() || !state.is(mines.get())) {
            return 1.0F;
        }
        return (float) Math.max(1.0D, IRON_SPEED * weight(stack));
    }

    /**
     * Whether what it digs gives what it is made of: it is the tool for it, and weighs enough to break it, as a tool
     * of wood, stone or iron does (never diamond's).
     */
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        Optional<TagKey<Block>> mines = mines();
        if (mines.isEmpty() || !state.is(mines.get())) {
            return false;
        }
        double weight = weight(stack);
        int level = weight >= 0.95D ? 2 : weight >= 0.75D ? 1 : 0;
        int needed = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? 3
                : state.is(BlockTags.NEEDS_IRON_TOOL) ? 2
                : state.is(BlockTags.NEEDS_STONE_TOOL) ? 1 : 0;
        return level >= needed;
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
