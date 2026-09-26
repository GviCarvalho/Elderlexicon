package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientVisibility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** How much of an entity or a block is seen, 0 (unseen) to 10 (whole), sent to those who can see it. */
public record VisibilityPacket(int entityId, @Nullable BlockPos block, int level) {

    public static VisibilityPacket entity(int entityId, int level) {
        return new VisibilityPacket(entityId, null, level);
    }

    public static VisibilityPacket block(BlockPos pos, int level) {
        return new VisibilityPacket(-1, pos.immutable(), level);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(block != null);
        if (block != null) {
            buffer.writeBlockPos(block);
        } else {
            buffer.writeVarInt(entityId);
        }
        buffer.writeByte(level);
    }

    public static VisibilityPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readBoolean()) {
            BlockPos pos = buffer.readBlockPos();
            return new VisibilityPacket(-1, pos, buffer.readUnsignedByte());
        }
        int entityId = buffer.readVarInt();
        return new VisibilityPacket(entityId, null, buffer.readUnsignedByte());
    }

    public static void handle(VisibilityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (packet.block != null) {
                ClientVisibility.setBlock(packet.block, packet.level);
            } else {
                ClientVisibility.set(packet.entityId, packet.level);
            }
        });
        context.setPacketHandled(true);
    }
}
