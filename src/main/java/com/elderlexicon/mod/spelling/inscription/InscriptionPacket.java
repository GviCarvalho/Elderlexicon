package com.elderlexicon.mod.spelling.inscription;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runes written on block faces, from the server to a client: every one of the world ({@code replace}), or the ones
 * that changed (a blank text wipes the face).
 */
public record InscriptionPacket(List<Inscription> inscriptions, boolean replace) {

    private static final int MAX = 32_768;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(replace);
        buffer.writeVarInt(inscriptions.size());
        inscriptions.forEach(inscription -> inscription.write(buffer));
    }

    public static InscriptionPacket decode(FriendlyByteBuf buffer) {
        boolean replace = buffer.readBoolean();
        int size = Math.min(MAX, buffer.readVarInt());
        List<Inscription> inscriptions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            inscriptions.add(Inscription.read(buffer));
        }
        return new InscriptionPacket(inscriptions, replace);
    }

    public static void handle(InscriptionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientInscriptions.receive(packet.inscriptions(), packet.replace()));
        context.setPacketHandled(true);
    }
}
