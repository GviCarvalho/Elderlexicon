package com.elderlexicon.mod.galdraria;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The mage at the galdraria table carving what they wrote; the table checks the item, the burin and the runes. */
public record EngravePacket(String text) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(text, Engravings.MAX_CHARS);
    }

    public static EngravePacket decode(FriendlyByteBuf buffer) {
        return new EngravePacket(buffer.readUtf(Engravings.MAX_CHARS));
    }

    public static void handle(EngravePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof GaldrariaMenu table && table.stillValid(player)) {
                table.engrave(player, packet.text());
            }
        });
        context.setPacketHandled(true);
    }
}
