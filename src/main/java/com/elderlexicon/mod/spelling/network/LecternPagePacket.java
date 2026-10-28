package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.LecternGrimoires;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The page a reader left the grimoire on a lectern open at, when they closed it. */
public record LecternPagePacket(BlockPos lectern, int page) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(lectern);
        buffer.writeVarInt(page);
    }

    public static LecternPagePacket decode(FriendlyByteBuf buffer) {
        return new LecternPagePacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(LecternPagePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                LecternGrimoires.leaveOpenAt(sender, packet.lectern, packet.page);
            }
        });
        context.setPacketHandled(true);
    }
}
