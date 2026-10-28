package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.LecternGrimoireClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Opens, for the one who used a lectern, the grimoire lying on it (docs/grimorio-design.md, the lectern): its pages,
 * at the page it lies open at.
 */
public record LecternGrimoirePacket(BlockPos lectern, int page, ItemStack book) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(lectern);
        buffer.writeVarInt(page);
        buffer.writeItem(book);
    }

    public static LecternGrimoirePacket decode(FriendlyByteBuf buffer) {
        return new LecternGrimoirePacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readItem());
    }

    public static void handle(LecternGrimoirePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> LecternGrimoireClient.open(packet.lectern, packet.page, packet.book));
        context.setPacketHandled(true);
    }
}
