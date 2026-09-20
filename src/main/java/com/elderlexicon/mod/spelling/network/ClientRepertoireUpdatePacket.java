package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sent by the client when the player edits their repertoire in the config screen.
 */
public record ClientRepertoireUpdatePacket(List<String> slots) {

    private static final ParserDictionary DICTIONARY = ParserDictionary.load();

    public static ClientRepertoireUpdatePacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> data = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            data.add(buffer.readUtf(32));
        }
        return new ClientRepertoireUpdatePacket(data);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(slots.size());
        for (String slot : slots) {
            buffer.writeUtf(slot == null ? "" : slot, 32);
        }
    }

    public static void handle(ClientRepertoireUpdatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!isPayloadValid(packet.slots)) {
                return;
            }
            SpellingRepertoire repertoire = SpellingRepertoireHelper.get(sender);
            for (int i = 0; i < SpellingRepertoire.SLOT_COUNT; i++) {
                repertoire.assignSlot(i, packet.slots.get(i));
            }
            SpellingNetwork.syncToPlayer(sender, repertoire);
        });
        context.setPacketHandled(true);
    }

    private static boolean isPayloadValid(List<String> slots) {
        if (slots.size() != SpellingRepertoire.SLOT_COUNT) {
            return false;
        }
        for (String slot : slots) {
            if (slot == null || slot.isBlank()) {
                continue;
            }
            if (DICTIONARY.lookup(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
