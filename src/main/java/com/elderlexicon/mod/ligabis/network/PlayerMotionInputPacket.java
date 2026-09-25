package com.elderlexicon.mod.ligabis.network;

import com.elderlexicon.mod.ligabis.world.LigabisManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The client's own walking input ({@code xxa}/{@code zza}), sent to the server every time it changes.
 * Minecraft never reports this for ordinary on-foot movement (only while steering a vehicle), so a golem
 * puppeting a player has no other way to know the player is trying to walk — even one blocked by a wall.
 */
public record PlayerMotionInputPacket(float xxa, float zza) {

    public static PlayerMotionInputPacket decode(FriendlyByteBuf buffer) {
        return new PlayerMotionInputPacket(buffer.readFloat(), buffer.readFloat());
    }

    public static void encode(PlayerMotionInputPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.xxa());
        buffer.writeFloat(packet.zza());
    }

    public static void handle(PlayerMotionInputPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            return;
        }
        context.enqueueWork(() -> {
            LigabisManager manager = LigabisManager.get();
            if (manager != null) {
                manager.setPlayerInput(sender.getUUID(), packet.xxa(), packet.zza());
            }
        });
        context.setPacketHandled(true);
    }
}
