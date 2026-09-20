package com.elderlexicon.mod.vita.damage;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hooks damage/status events and applies Vita element adjustments per the mapping plan.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class VitaDamageHandler {

    private static final ResourceLocation SRC_DROWN_AURA = new ResourceLocation(ExampleMod.MODID, "damage/drown_aura");
    private static final ResourceLocation SRC_POISON_FIRMO = new ResourceLocation(ExampleMod.MODID, "damage/poison_firmo");
    private static final ResourceLocation SRC_WITHER_FIRMO = new ResourceLocation(ExampleMod.MODID, "damage/wither_firmo");
    private static final ResourceLocation SRC_WITHER_AURA = new ResourceLocation(ExampleMod.MODID, "damage/wither_aura");
    private static final ResourceLocation SRC_FIRE_AQUA = new ResourceLocation(ExampleMod.MODID, "damage/fire_aqua");
    private static final ResourceLocation SRC_FIRE_IGNI = new ResourceLocation(ExampleMod.MODID, "damage/fire_igni");
    private static final ResourceLocation SRC_FREEZE_IGNI = new ResourceLocation(ExampleMod.MODID, "damage/freeze_igni");
    private static final ResourceLocation SRC_LIGHTNING_AURA = new ResourceLocation(ExampleMod.MODID, "damage/lightning_aura");
    private static final ResourceLocation SRC_SOUL_HEAT_IGNI = new ResourceLocation(ExampleMod.MODID, "damage/soul_heat_igni");
    private static final double EPSILON = 1.0E-4D;

    private VitaDamageHandler() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        double damage = event.getAmount();
        if (damage <= EPSILON) {
            return;
        }

        boolean handled = false;

        if (is(source, DamageTypes.DROWN)) {
            double clamped = clamp(damage, 1.0D, 4.0D);
            double base = -2.0D * clamped;
            double scaled = scale("drownAura", base);
            handled |= applyElementDelta(player, VitaElement.AURA, SRC_DROWN_AURA, damage, scaled, 8.0D, 20);
        } else if (isPoisonTick(player, source)) {
            double scaled = scale("poisonFirmo", -1.0D);
            handled |= applyElementDelta(player, VitaElement.FIRMO, SRC_POISON_FIRMO, damage, scaled, 0.0D, 0);
        } else if (is(source, DamageTypes.WITHER)) {
            double firmo = scale("witherFirmo", -1.0D);
            double aura = scale("witherAura", -1.0D);
            handled |= applyElementDelta(player, VitaElement.FIRMO, SRC_WITHER_FIRMO, damage, firmo, 6.0D, 20);
            handled |= applyElementDelta(player, VitaElement.AURA, SRC_WITHER_AURA, damage, aura, 6.0D, 20);
        } else if (isAny(source, DamageTypes.IN_FIRE, DamageTypes.ON_FIRE, DamageTypes.LAVA)) {
            double clamped = clamp(damage, 1.0D, 6.0D);
            double aquaDelta = scale("fireAqua", -clamped);
            double igniDelta = scale("fireIgni", 2.0D * clamped);
            handled |= applyElementDelta(player, VitaElement.AQUA, SRC_FIRE_AQUA, damage, aquaDelta, 0.0D, 0);
            handled |= applyElementDelta(player, VitaElement.IGNI, SRC_FIRE_IGNI, damage, igniDelta, 0.0D, 0);
        } else if (is(source, DamageTypes.FREEZE)) {
            double scaled = scale("freezeIgni", -3.0D);
            handled |= applyElementDelta(player, VitaElement.IGNI, SRC_FREEZE_IGNI, damage, scaled, 12.0D, 40);
        } else if (is(source, DamageTypes.LIGHTNING_BOLT)) {
            double clamped = clamp(damage, 1.0D, 5.0D);
            double scaled = scale("lightningAura", 4.0D * clamped);
            handled |= applyElementDelta(player, VitaElement.AURA, SRC_LIGHTNING_AURA, damage, scaled, 12.0D, 20);
        } else if (isSoulHeat(source)) {
            double scaled = scale("soulHeatIgni", 3.0D);
            handled |= applyElementDelta(player, VitaElement.IGNI, SRC_SOUL_HEAT_IGNI, damage, scaled, 9.0D, 20);
        }

        if (!handled && is(source, DamageTypes.HOT_FLOOR)) {
            double scaled = scale("soulHeatIgni", 3.0D);
            applyElementDelta(player, VitaElement.IGNI, SRC_SOUL_HEAT_IGNI, damage, scaled, 9.0D, 20);
        }
    }

    private static boolean isPoisonTick(ServerPlayer player, DamageSource source) {
        return source.is(DamageTypes.MAGIC)
                && source.getEntity() == null
                && source.getDirectEntity() == null
                && player.hasEffect(MobEffects.POISON);
    }

    private static boolean isSoulHeat(DamageSource source) {
        DamageType type = source.type();
        String msgId = type.msgId();
        return "soul_fire".equals(msgId) || "soul_heat".equals(msgId);
    }

    private static double scale(String configKey, double baseDelta) {
        return baseDelta * DamageMappingConfig.getMultiplier(configKey);
    }

    private static double clamp(double value, double min, double max) {
        return Mth.clamp(value, min, max);
    }

    @SafeVarargs
    private static boolean isAny(DamageSource source, ResourceKey<DamageType>... keys) {
        for (ResourceKey<DamageType> key : keys) {
            if (is(source, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean is(DamageSource source, ResourceKey<DamageType> key) {
        return source.is(key);
    }

    private static boolean applyElementDelta(ServerPlayer player,
                                             VitaElement element,
                                             ResourceLocation sourceId,
                                             double rawDamage,
                                             double delta,
                                             double windowCap,
                                             int windowTicks) {
        if (Math.abs(delta) <= EPSILON) {
            return false;
        }
        LazyOptional<DamageAccumulator> optional = DamageAccumulatorCapability.get(player);
        if (!optional.isPresent()) {
            return false;
        }

        return optional.map(acc -> {
            long gameTime = player.serverLevel().getGameTime();
            double allowed = delta;
            if (windowTicks > 0 && windowCap > 0.0D) {
                double accumulated = acc.getAccumulated(sourceId, gameTime, windowTicks);
                double lower = -windowCap;
                double upper = windowCap;
                double desired = accumulated + delta;
                if (desired < lower) {
                    allowed = lower - accumulated;
                } else if (desired > upper) {
                    allowed = upper - accumulated;
                }
                if (Math.abs(allowed) <= EPSILON) {
                    return false;
                }
            }

            acc.recordSample(sourceId, element, rawDamage, allowed, gameTime, windowTicks);
            if (allowed < 0.0D) {
                VitaSystem.consumeElementReserve(player, element, -allowed);
            } else {
                VitaSystem.restoreElementEnergy(player, element, allowed);
            }
            return true;
        }).orElse(false);
    }
}
