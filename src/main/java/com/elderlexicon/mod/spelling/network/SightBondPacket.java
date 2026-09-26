package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientSightBond;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The mage's sight is bound to the entity {@code entityId} for {@code ticks}: the client sees through its eyes. */
public record SightBondPacket(int entityId, int ticks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeVarInt(ticks);
    }

    public static SightBondPacket decode(FriendlyByteBuf buffer) {
        return new SightBondPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(SightBondPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientSightBond.start(packet.entityId, packet.ticks));
        context.setPacketHandled(true);
    }
}
