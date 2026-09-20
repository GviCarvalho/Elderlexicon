package com.elderlexicon.mod.spelling.data;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles syncing repertoire data from server to client on key lifecycle events.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class SpellingRepertoireSyncEvents {

    private SpellingRepertoireSyncEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SpellingNetwork.syncToPlayer(serverPlayer, SpellingRepertoireHelper.get(serverPlayer));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SpellingNetwork.syncToPlayer(serverPlayer, SpellingRepertoireHelper.get(serverPlayer));
        }
    }
}
