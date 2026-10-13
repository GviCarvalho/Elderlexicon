package com.elderlexicon.mod.spelling.flow;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.block.LineConditions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The happenings and states of the mage's body (docs/fluxo-design.md), each told by the trigger its condition runes
 * declare ({@link Happenings}). Each is always kept, for the conditions read in any way; in flow, a happening, or a
 * state as it begins, also wakes the spirit.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class FlowEvents {

    /** A fall shorter than this, in blocks, is a step down, not a landing. */
    private static final float LANDING_BLOCKS = 2.0F;
    /** How near the ground a fall starts waking the lines that wait for it, in flow. */
    private static final double FALL_WAKE_BLOCKS = 16.0D;
    /** How often, in flow, the body is looked at for the lines that measure it. */
    private static final int MEASURE_TICKS = 20;

    private FlowEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            for (String state : Happenings.begun(player)) {
                happen(player, state);
            }
            // The body is looked at once a second: the lines that measure it wake, and hold if their measure does.
            if (player.tickCount % MEASURE_TICKS == 0) {
                FlowState.happen(player, LineConditions.MEASURE);
            }
            // A fall near the ground wakes the lines that wait for it every tick; each fires once a fall.
            double below = Happenings.fall(player);
            if (below >= 0.0D && below <= FALL_WAKE_BLOCKS) {
                FlowState.happen(player, LineConditions.FALLING);
            }
            FlowState.tick(player);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            happen(player, Happenings.ATTACK, new Happenings.OtherEntity(event.getTarget()));
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F) {
            Entity hurter = event.getSource().getEntity();
            happen(player, Happenings.HURT, hurter == null || hurter == player ? null : new Happenings.OtherEntity(hurter));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer dead) {
            FlowState.leave(dead, null);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != event.getEntity()) {
            // The creature is dying: what is left of it is where it fell.
            happen(killer, Happenings.KILL,
                    new Happenings.OtherPoint((ServerLevel) event.getEntity().level(), event.getEntity().position()));
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            happen(player, Happenings.JUMP);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getDistance() >= LANDING_BLOCKS) {
            // The ille of a landing is the ground landed on.
            happen(player, Happenings.LAND, new Happenings.OtherBlock(player.serverLevel(),
                    player.blockPosition().below()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level) {
            // The block is going: what is left of it is where it was.
            happen(player, Happenings.BREAK, new Happenings.OtherPoint(level, Vec3.atCenterOf(event.getPos())));
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            happen(player, Happenings.USE);
        }
    }

    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            happen(player, Happenings.USE, new Happenings.OtherBlock(level, event.getPos()));
        }
    }

    @SubscribeEvent
    public static void onUseOnEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            happen(player, Happenings.USE, new Happenings.OtherEntity(event.getTarget()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlowState.forget(player);
            Happenings.forget(player);
        }
    }

    private static void happen(ServerPlayer player, String trigger) {
        happen(player, trigger, null);
    }

    private static void happen(ServerPlayer player, String trigger, Happenings.Other other) {
        Happenings.mark(player, trigger, other);
        FlowState.happen(player, trigger);
    }
}
