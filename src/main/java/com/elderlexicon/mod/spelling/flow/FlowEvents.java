package com.elderlexicon.mod.spelling.flow;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.server.level.ServerPlayer;
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

    private FlowEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            for (String state : Happenings.begun(player)) {
                happen(player, state);
            }
            FlowState.tick(player);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            happen(player, Happenings.ATTACK);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F) {
            happen(player, Happenings.HURT);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer dead) {
            FlowState.leave(dead, null);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != event.getEntity()) {
            happen(killer, Happenings.KILL);
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
            happen(player, Happenings.LAND);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player) {
            happen(player, Happenings.BREAK);
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
        if (event.getEntity() instanceof ServerPlayer player) {
            happen(player, Happenings.USE);
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
        Happenings.mark(player, trigger);
        FlowState.happen(player, trigger);
    }
}
