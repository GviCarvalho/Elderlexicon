package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies special Ligabis immortality behavior using Forge events.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class LigabisEvents {

    private LigabisEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        long gameTime = level.getGameTime();
        LigabisLinkManager.LinkData link = LigabisLinkManager.find(entity, gameTime);
        if (link == null) {
            return;
        }

        // Clean up expired links
        if (link.expiresAt() <= gameTime) {
            LigabisLinkManager.cleanup(gameTime);
            return;
        }

        if (!link.isProtected(entity)) {
            return;
        }

        if (!link.masterIntact(level)) {
            LigabisLinkManager.removeLink(link);
            return;
        }

        // One-way: master pode morrer normalmente
        if (link.direction() == LigabisFunctionHandler.Direction.ONE_WAY && link.isMaster(entity)) {
            return;
        }

        // Totem effect: cancel death
        event.setCanceled(true);
        applyTotemEffect(entity);
        link.markProc(entity.getUUID(), gameTime);
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        long gameTime = level.getGameTime();
        if (LigabisLinkManager.shouldSuppressDrops(entity, gameTime)) {
            event.setCanceled(true);
            event.getDrops().clear();
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        var source = event.getSource();
        if (!source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypes.HOT_FLOOR)) {
            return;
        }
        long gameTime = level.getGameTime();
        LigabisLinkManager.LinkData link = LigabisLinkManager.find(entity, gameTime);
        if (link == null) {
            return;
        }
        if (link.direction() != LigabisFunctionHandler.Direction.ONE_WAY) {
            return;
        }
        if (!link.isProtected(entity)) {
            return;
        }
        if (!link.masterIntact(level)) {
            LigabisLinkManager.removeLink(link);
            return;
        }
        if (!LigabisLinkManager.masterIsHot(link, level)) {
            event.setCanceled(true);
            entity.clearFire();
            entity.setRemainingFireTicks(0);
        }
    }

    private static void applyTotemEffect(LivingEntity entity) {
        float healAmount = Math.max(1.0F, entity.getMaxHealth() * 0.5F);
        entity.setHealth(healAmount);
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        entity.fallDistance = 0.0F;
        entity.invulnerableTime = 20;
        clearDeathState(entity);
        entity.removeAllEffects();
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0));
    }

    private static void clearDeathState(LivingEntity entity) {
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("deathTime");
            field.setAccessible(true);
            field.setInt(entity, 0);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("f_20915_");
            field.setAccessible(true);
            field.setInt(entity, 0);
        } catch (ReflectiveOperationException ignored) {
            // best effort
        }
    }
}
