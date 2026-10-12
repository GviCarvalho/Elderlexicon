package com.elderlexicon.mod.vita.damage;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.nahva.Dreams;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hooks damage/status events and applies Vita element adjustments per the mapping plan.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class VitaDamageHandler {

    private static final ResourceLocation SRC_FIRE_IGNI = new ResourceLocation(ElderLexicon.MODID, "damage/fire_igni");
    private static final ResourceLocation SRC_LIGHTNING_AURA = new ResourceLocation(ElderLexicon.MODID, "damage/lightning_aura");
    private static final ResourceLocation SRC_SOUL_HEAT_IGNI = new ResourceLocation(ElderLexicon.MODID, "damage/soul_heat_igni");
    private static final double EPSILON = 1.0E-4D;

    private VitaDamageHandler() {
    }

    /**
     * The body as a chalice (docs/vita-design.md): every hurt takes the life from the element it strikes, and what no
     * element claims is taken from all of them. Some hurts also bring something in: fire heats, lightning charges the
     * breath.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity player = event.getEntity();
        if (event.isCanceled() || player.level().isClientSide) {
            return;
        }
        if (player instanceof ServerPlayer mage && Dreams.isDreaming(mage)) {
            return;
        }
        DamageSource source = event.getSource();
        double damage = event.getAmount();
        if (damage <= EPSILON) {
            return;
        }

        VitaSystem.hurtBy(player, damage, natureOf(player, source));

        if (source.is(DamageTypeTags.IS_FIRE)) {
            double clamped = clamp(damage, 1.0D, 6.0D);
            applyElementDelta(player, VitaElement.IGNI, SRC_FIRE_IGNI, damage, scale("fireIgni", 2.0D * clamped), 0.0D, 0);
        } else if (source.is(DamageTypeTags.IS_LIGHTNING)) {
            double clamped = clamp(damage, 1.0D, 5.0D);
            applyElementDelta(player, VitaElement.AURA, SRC_LIGHTNING_AURA, damage,
                    scale("lightningAura", 4.0D * clamped), 12.0D, 20);
        } else if (isSoulHeat(source)) {
            applyElementDelta(player, VitaElement.IGNI, SRC_SOUL_HEAT_IGNI, damage, scale("soulHeatIgni", 3.0D), 9.0D, 20);
        }
    }

    /**
     * What a hurt strikes in the body: the blood (aqua) for a cut, a pierce, poison or fire, which also dries it; the
     * bones (firmo) for a fall or a crush; the breath (aura) for drowning, suffocating or lightning; the heat (igni) for
     * freezing; the flesh down to the bones (firmo and aura) for the wither. None: all of them (an explosion, hunger,
     * magic).
     */
    public static VitaElement[] natureOf(LivingEntity player, DamageSource source) {
        if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)) {
            return new VitaElement[] {VitaElement.FIRMO, VitaElement.AURA};
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.STARVE)) {
            return new VitaElement[0];
        }
        if (source.is(DamageTypeTags.IS_FALL) || isAny(source, DamageTypes.FALLING_ANVIL, DamageTypes.FALLING_BLOCK,
                DamageTypes.FALLING_STALACTITE, DamageTypes.STALAGMITE, DamageTypes.FLY_INTO_WALL, DamageTypes.CRAMMING)) {
            return new VitaElement[] {VitaElement.FIRMO};
        }
        if (source.is(DamageTypeTags.IS_FIRE) || isSoulHeat(source)) {
            return new VitaElement[] {VitaElement.AQUA};
        }
        if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypeTags.IS_LIGHTNING)) {
            return new VitaElement[] {VitaElement.AURA};
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return new VitaElement[] {VitaElement.IGNI};
        }
        if (isPoisonTick(player, source) || source.is(DamageTypeTags.IS_PROJECTILE)
                || isAny(source, DamageTypes.MOB_ATTACK, DamageTypes.MOB_ATTACK_NO_AGGRO, DamageTypes.PLAYER_ATTACK,
                DamageTypes.STING, DamageTypes.THORNS, DamageTypes.CACTUS, DamageTypes.SWEET_BERRY_BUSH)) {
            return new VitaElement[] {VitaElement.AQUA};
        }
        return new VitaElement[0];
    }

    private static boolean isPoisonTick(LivingEntity player, DamageSource source) {
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

    private static boolean applyElementDelta(LivingEntity player,
                                             VitaElement element,
                                             ResourceLocation sourceId,
                                             double rawDamage,
                                             double delta,
                                             double windowCap,
                                             int windowTicks) {
        if (Math.abs(delta) <= EPSILON) {
            return false;
        }
        if (!(player instanceof net.minecraft.world.entity.player.Player mage)) {
            // A creature keeps no windows of what its hurts brought in (only a player carries them): each hurt brings
            // its own, up to what one window would allow.
            double allowed = windowCap > 0.0D ? Mth.clamp(delta, -windowCap, windowCap) : delta;
            if (allowed < 0.0D) {
                VitaSystem.consumeElementReserve(player, element, -allowed);
            } else {
                VitaSystem.restoreElementEnergy(player, element, allowed);
            }
            return true;
        }
        LazyOptional<DamageAccumulator> optional = DamageAccumulatorCapability.get(mage);
        if (!optional.isPresent()) {
            return false;
        }

        return optional.map(acc -> {
            long gameTime = player.level().getGameTime();
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
