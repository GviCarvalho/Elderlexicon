package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientForms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The body a player is in (docs/particulas-design.md, stage 6): the creature {@code entityId}'s body is shown as, or
 * {@code ""} for a person's own.
 */
public record FormPacket(int entityId, String entityType) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeUtf(entityType);
    }

    public static FormPacket decode(FriendlyByteBuf buffer) {
        return new FormPacket(buffer.readVarInt(), buffer.readUtf());
    }

    public static void handle(FormPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientForms.set(packet.entityId(), packet.entityType()));
        context.setPacketHandled(true);
    }
}
