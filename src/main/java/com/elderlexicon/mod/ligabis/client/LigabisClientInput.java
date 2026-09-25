package com.elderlexicon.mod.ligabis.client;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.ligabis.network.LigabisNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reports the local player's own walking input to the server every client tick, since Minecraft only does
 * this on its own while a player is steering a vehicle. A golem puppeting a player relies on it to know
 * the player is trying to walk even when blocked by something (see {@code PlayerMotionInputPacket}).
 * <p>
 * Sent unconditionally rather than only when it changes: it is a couple of bytes, twenty times a second,
 * and it avoids trusting {@code player.xxa}/{@code zza} to stay at a stable non-zero value while a key
 * stays held (which it may not, depending on how the client itself decays those fields between ticks) —
 * the server just always gets whatever the client currently has.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class LigabisClientInput {

    private LigabisClientInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        LigabisNetwork.sendMotionInput(player.xxa, player.zza);
    }
}
