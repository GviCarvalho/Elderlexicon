package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientLaunches;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A player was just thrown by a spell: those who see it draw it flying, laid along its course, until it lands. */
public record LaunchPacket(int entityId) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
    }

    public static LaunchPacket decode(FriendlyByteBuf buffer) {
        return new LaunchPacket(buffer.readVarInt());
    }

    public static void handle(LaunchPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientLaunches.start(packet.entityId));
        context.setPacketHandled(true);
    }
}
