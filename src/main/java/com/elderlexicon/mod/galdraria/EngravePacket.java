package com.elderlexicon.mod.galdraria;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The line the mage is writing at the galdraria table, sent as it changes (as the anvil sends a name): the table carves
 * the result anew from it.
 */
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
                table.setText(packet.text());
            }
        });
        context.setPacketHandled(true);
    }
}
