package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientSpellingController;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Mirror packet that pushes the authoritative repertoire to the client.
 */
public record ServerRepertoireSyncPacket(List<String> slots) {

    public static ServerRepertoireSyncPacket from(SpellingRepertoire repertoire) {
        return new ServerRepertoireSyncPacket(repertoire.copySlots());
    }

    public static ServerRepertoireSyncPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(buffer.readUtf(32));
        }
        return new ServerRepertoireSyncPacket(slots);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(slots.size());
        for (String slot : slots) {
            buffer.writeUtf(slot == null ? "" : slot, 32);
        }
    }

    public static void handle(ServerRepertoireSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            ClientSpellingController.getInstance().applyServerRepertoire(packet.slots);
        });
        context.setPacketHandled(true);
    }
}
