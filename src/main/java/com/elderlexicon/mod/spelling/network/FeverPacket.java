package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.nahva.client.FeverClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the dreamer which rune and scene of their fever dream they are in, for the fever on their screen
 * (docs/aprendizado-runas-design.md, 4b); -1 when they wake.
 */
public record FeverPacket(String rune, int scene) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(rune, 32);
        buffer.writeVarInt(scene);
    }

    public static FeverPacket decode(FriendlyByteBuf buffer) {
        return new FeverPacket(buffer.readUtf(32), buffer.readVarInt());
    }

    public static void handle(FeverPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> FeverClient.set(packet.rune, packet.scene));
        context.setPacketHandled(true);
    }
}
