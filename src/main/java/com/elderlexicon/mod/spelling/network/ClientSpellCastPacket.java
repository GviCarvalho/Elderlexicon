package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.server.ServerSpellingController;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sent from the client once the Spelling input window closes.
 */
public record ClientSpellCastPacket(List<String> runes, long activationTimestampMs) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static ClientSpellCastPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> runes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            runes.add(buffer.readUtf(32));
        }
        long activationTimestamp = buffer.readLong();
        return new ClientSpellCastPacket(runes, activationTimestamp);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(runes.size());
        for (String rune : runes) {
            buffer.writeUtf(rune == null ? "" : rune, 32);
        }
        buffer.writeLong(activationTimestampMs);
    }

    public static void handle(ClientSpellCastPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            LOGGER.debug("Spelling packet from {} ({} runes, activation {} ms)",
                    sender.getGameProfile().getName(),
                    packet.runes.size(),
                    packet.activationTimestampMs);
            ServerSpellingController.getInstance().handleSpellCast(sender, packet.runes, packet.activationTimestampMs);
        });
        context.setPacketHandled(true);
    }
}
