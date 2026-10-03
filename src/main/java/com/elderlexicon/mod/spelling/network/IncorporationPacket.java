package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientIncorporation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The mage's kern goes into the body {@code entityId} for {@code ticks}; {@code ticks} 0 brings it home. */
public record IncorporationPacket(int entityId, int ticks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeVarInt(ticks);
    }

    public static IncorporationPacket decode(FriendlyByteBuf buffer) {
        return new IncorporationPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(IncorporationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (packet.ticks() <= 0) {
                ClientIncorporation.end();
            } else {
                ClientIncorporation.start(packet.entityId(), packet.ticks());
            }
        });
        context.setPacketHandled(true);
    }
}
