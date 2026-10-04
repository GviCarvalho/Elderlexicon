package com.elderlexicon.mod.spelling.flow;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The happenings of the mage's body (docs/fluxo-design.md), each told by the trigger its condition runes declare:
 * {@code attack} (ferit), {@code hurt} (patitur), {@code kill} (necat). Each is always kept as just happened, for the
 * conditions read in any way; in flow, it also wakes the spirit.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class FlowEvents {

    public static final String ATTACK = "attack";
    public static final String HURT = "hurt";
    public static final String KILL = "kill";

    private FlowEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            FlowState.tick(player);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            happen(player, ATTACK);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F) {
            happen(player, HURT);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer dead) {
            FlowState.leave(dead, null);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != event.getEntity()) {
            happen(killer, KILL);
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
