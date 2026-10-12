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
 * Mirror packet that pushes the authoritative repertoire to the client: the nine slots and the runes unlocked.
 */
public record ServerRepertoireSyncPacket(List<String> slots, List<String> unlocked, List<String> understood,
                                         boolean trance, boolean flow) {

    public static ServerRepertoireSyncPacket from(SpellingRepertoire repertoire) {
        return new ServerRepertoireSyncPacket(repertoire.copySlots(), new ArrayList<>(repertoire.unlocked()),
                new ArrayList<>(repertoire.understood()), repertoire.hasTrance(), repertoire.hasFlow());
    }

    public static ServerRepertoireSyncPacket decode(FriendlyByteBuf buffer) {
        return new ServerRepertoireSyncPacket(readList(buffer), readList(buffer), readList(buffer),
                buffer.readBoolean(), buffer.readBoolean());
    }

    private static List<String> readList(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buffer.readUtf(32));
        }
        return list;
    }

    public void encode(FriendlyByteBuf buffer) {
        writeList(buffer, slots);
        writeList(buffer, unlocked);
        writeList(buffer, understood);
        buffer.writeBoolean(trance);
        buffer.writeBoolean(flow);
    }

    private static void writeList(FriendlyByteBuf buffer, List<String> list) {
        buffer.writeVarInt(list.size());
        for (String entry : list) {
            buffer.writeUtf(entry == null ? "" : entry, 32);
        }
    }

    public static void handle(ServerRepertoireSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            ClientSpellingController.getInstance().applyServerRepertoire(packet.slots);
            ClientSpellingController.getInstance().applyUnlocked(packet.unlocked);
            ClientSpellingController.getInstance().applyUnderstood(packet.understood);
            ClientSpellingController.getInstance().applyAbilities(packet.trance, packet.flow);
        });
        context.setPacketHandled(true);
    }
}
