package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spell.function.Incorporations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** What the mage does with the body their kern is in, this tick: walk, jump, look, and strike ({@code strike} >= 0). */
public record IncorporationInputPacket(float forward, float strafe, boolean jump, float yaw, float pitch, int strike) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(forward);
        buffer.writeFloat(strafe);
        buffer.writeBoolean(jump);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeVarInt(strike + 1);
    }

    public static IncorporationInputPacket decode(FriendlyByteBuf buffer) {
        return new IncorporationInputPacket(buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(),
                buffer.readFloat(), buffer.readFloat(), buffer.readVarInt() - 1);
    }

    public static void handle(IncorporationInputPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Incorporations.input(player, new Incorporations.Input(clamp(packet.forward()), clamp(packet.strafe()),
                        packet.jump(), packet.yaw(), packet.pitch(), packet.strike()));
            }
        });
        context.setPacketHandled(true);
    }

    private static float clamp(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }
}
