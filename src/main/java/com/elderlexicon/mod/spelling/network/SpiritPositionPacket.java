package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spell.function.AstralProjections;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Where the sender's projected spirit has drifted, so a revelation of magic can see it there. */
public record SpiritPositionPacket(Vec3 at) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(at.x);
        buffer.writeDouble(at.y);
        buffer.writeDouble(at.z);
    }

    public static SpiritPositionPacket decode(FriendlyByteBuf buffer) {
        return new SpiritPositionPacket(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    public static void handle(SpiritPositionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                AstralProjections.moved(sender, packet.at);
            }
        });
        context.setPacketHandled(true);
    }
}
