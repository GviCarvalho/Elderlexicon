package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.Recitations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What a mage in trance is reciting, rune by rune. The caster's client sends it as each rune is spoken; the server
 * passes it on to the players who can see the caster, so the words appear above the caster's head.
 *
 * @param entityId who is reciting (ignored on the way to the server: it is always the sender)
 * @param runes    the runes spoken so far
 * @param finished true once the trance ends, so the words fade out
 */
public record RecitationPacket(int entityId, List<String> runes, boolean finished) {

    /** A trance never holds more runes than this ({@code RuneBuffer.DEFAULT_CAPACITY}, with room to spare). */
    private static final int MAX_RUNES = 16;
    private static final int MAX_RUNE_LENGTH = 32;

    public RecitationPacket {
        runes = runes == null ? List.of() : List.copyOf(runes);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeVarInt(Math.min(runes.size(), MAX_RUNES));
        for (int i = 0; i < Math.min(runes.size(), MAX_RUNES); i++) {
            buffer.writeUtf(runes.get(i), MAX_RUNE_LENGTH);
        }
        buffer.writeBoolean(finished);
    }

    public static RecitationPacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        int count = Math.min(buffer.readVarInt(), MAX_RUNES);
        List<String> runes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            runes.add(buffer.readUtf(MAX_RUNE_LENGTH));
        }
        return new RecitationPacket(entityId, runes, buffer.readBoolean());
    }

    public static void handle(RecitationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
            context.enqueueWork(() -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    SpellingNetwork.broadcastRecitation(sender, new RecitationPacket(sender.getId(), packet.runes, packet.finished));
                }
            });
        } else {
            context.enqueueWork(() -> Recitations.update(packet.entityId, packet.runes, packet.finished));
        }
        context.setPacketHandled(true);
    }
}
