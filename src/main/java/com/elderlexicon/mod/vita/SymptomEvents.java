package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.nahva.Dreams;
import com.elderlexicon.mod.vita.network.BodyPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The signs of the body that are not attributes (docs/vita-sintomas-design.md): the jump, the hands at work, the falls
 * and the bones. The jump and the breaking are the client's to move too, so on the client they follow what the server
 * last sent ({@link BodyPacket#felt()}).
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class SymptomEvents {

    private SymptomEvents() {
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity living = event.getEntity();
        double jump = feltJump(living);
        if (jump < 0.999D) {
            Vec3 motion = living.getDeltaMovement();
            living.setDeltaMovement(motion.x, motion.y * jump, motion.z);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        double dig = player.level().isClientSide() ? (player.isLocalPlayer() ? BodyPacket.felt().dig() : 1.0D)
                : Symptoms.dig(player);
        if (dig < 0.999D) {
            event.setNewSpeed((float) (event.getNewSpeed() * dig));
        }
    }

    /** Brittle bones and a broken leg make a fall hurt more. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide() || !event.getSource().is(DamageTypeTags.IS_FALL)) {
            return;
        }
        double fall = Symptoms.fall(living);
        if (fall > 1.001D) {
            event.setAmount((float) (event.getAmount() * fall));
        }
    }

    /** What reaches the bones may break one. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F
                || Dreams.isDreaming(player)) {
            return;
        }
        Fractures.strike(player, event.getSource(), event.getAmount());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            Fractures.carry(event.getOriginal(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Symptoms.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living
                && !(living instanceof Player)) {
            Symptoms.forget(living.getUUID());
        }
    }

    private static double feltJump(LivingEntity living) {
        if (!living.level().isClientSide()) {
            return Symptoms.jump(living);
        }
        return living instanceof Player player && player.isLocalPlayer() ? BodyPacket.felt().jump() : 1.0D;
    }
}
