package com.elderlexicon.mod.item;

import com.elderlexicon.mod.client.MarkEditScreen;
import com.elderlexicon.mod.mark.MarkTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;

/**
 * Tool that assigns persistent Marks to blocks or entities via a small text prompt.
 */
public class ElderBrushItem extends Item {

    private static final double RAY_RANGE = 12.0D;

    public ElderBrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            BlockPos pos = context.getClickedPos();
            MarkEditScreen.open(MarkTarget.block(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack,
                                                  Player player,
                                                  LivingEntity target,
                                                  InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) {
            MarkEditScreen.open(MarkTarget.entity(target.getId()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            MarkEditScreen.open(MarkTarget.entity(player.getId()));
            return InteractionResultHolder.success(stack);
        }
        HitResult hit = pick(player, RAY_RANGE);
        if (hit instanceof EntityHitResult entityHit) {
            MarkEditScreen.open(MarkTarget.entity(entityHit.getEntity().getId()));
            return InteractionResultHolder.success(stack);
        }
        if (hit instanceof BlockHitResult blockHit) {
            MarkEditScreen.open(MarkTarget.block(blockHit.getBlockPos()));
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private HitResult pick(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                eye,
                end,
                searchBox,
                candidate -> candidate.isPickable() && candidate != player);
        if (entityHit != null) {
            return entityHit;
        }
        return player.pick(range, 0.0F, false);
    }
}
