package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells the mage whether they are in flow now, for the HUD (docs/fluxo-design.md). */
public record FlowStatePacket(boolean flowing) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(flowing);
    }

    public static FlowStatePacket decode(FriendlyByteBuf buffer) {
        return new FlowStatePacket(buffer.readBoolean());
    }

    public static void handle(FlowStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientFlow.set(packet.flowing));
        context.setPacketHandled(true);
    }
}
