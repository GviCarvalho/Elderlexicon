package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hooks Vita logic into the Forge event bus.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class VitaEvents {

    private VitaEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        VitaSystem.tickPlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VitaSystem.initialize(player);
    }
}
