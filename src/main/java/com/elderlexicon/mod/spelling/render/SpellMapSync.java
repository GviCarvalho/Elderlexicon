package com.elderlexicon.mod.spelling.render;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sends a scroll's painted runes to players who come to see it. A detached page keeps its drawing in vanilla map
 * data, but it is not a map item, so Minecraft never sends that data on its own (it only does for maps carried or
 * framed); without this a placed or framed scroll showed as blank paper after a restart.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class SpellMapSync {

    private SpellMapSync() {
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player, scrollOf(event.getTarget()));
        }
    }

    /** Sends the drawing of {@code scroll} to {@code player}, if it has one. */
    public static void send(ServerPlayer player, ItemStack scroll) {
        if (scroll.isEmpty() || !(scroll.getItem() instanceof SpellScrollItem)) {
            return;
        }
        Integer mapId = SpellMapHelper.getMapId(scroll);
        MapItemSavedData data = SpellMapHelper.getSavedData(scroll, player.level());
        if (mapId == null || data == null) {
            return;
        }
        data.tickCarriedBy(player, scroll);
        Packet<?> packet = data.getUpdatePacket(mapId, player);
        if (packet != null) {
            player.connection.send(packet);
        }
    }

    private static ItemStack scrollOf(Entity entity) {
        if (entity instanceof ItemFrame frame) {
            return frame.getItem();
        }
        if (entity instanceof PlacedScrollEntity placed) {
            return placed.getScroll();
        }
        return ItemStack.EMPTY;
    }
}
