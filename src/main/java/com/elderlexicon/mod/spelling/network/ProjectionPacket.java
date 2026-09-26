package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientSightBond;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The mage's spirit leaves its body for {@code ticks}, appearing at {@code at}; or it is called back ({@code ticks} 0). */
public record ProjectionPacket(Vec3 at, int ticks) {

    public static ProjectionPacket start(Vec3 at, int ticks) {
        return new ProjectionPacket(at, ticks);
    }

    public static ProjectionPacket end() {
        return new ProjectionPacket(Vec3.ZERO, 0);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(at.x);
        buffer.writeDouble(at.y);
        buffer.writeDouble(at.z);
        buffer.writeVarInt(ticks);
    }

    public static ProjectionPacket decode(FriendlyByteBuf buffer) {
        return new ProjectionPacket(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readVarInt());
    }

    public static void handle(ProjectionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (packet.ticks > 0) {
                ClientSightBond.project(packet.at, packet.ticks);
            } else {
                ClientSightBond.callBack();
            }
        });
        context.setPacketHandled(true);
    }
}
