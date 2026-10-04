package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.flow.FlowState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The mage pressed the flow key: enter the state of flow, or leave it (docs/fluxo-design.md). */
public record FlowTogglePacket() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public static FlowTogglePacket decode(FriendlyByteBuf buffer) {
        return new FlowTogglePacket();
    }

    public static void handle(FlowTogglePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                FlowState.toggle(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
