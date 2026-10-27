package com.elderlexicon.mod.vita.network;

import com.elderlexicon.mod.vita.Symptoms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * What the body of a player feels (docs/vita-sintomas-design.md), sent to that player: the four elements as signed
 * severities (below zero a lack, above an excess), the broken bones, how high they can jump and how fast they break
 * blocks (the client must agree with the server on both), and the episode they are in. Only how it feels, never why.
 */
public record BodyPacket(float igni, float aqua, float aura, float firmo, boolean leg, boolean arm, float jump,
                         float dig, Symptoms.Episode episode, int episodeLeft) {

    public static final BodyPacket WHOLE = new BodyPacket(0, 0, 0, 0, false, false, 1, 1, Symptoms.Episode.NONE, 0);

    /** What the local player last felt: written on the client by the packet, read by its senses and its events. */
    private static volatile BodyPacket felt = WHOLE;

    public static BodyPacket felt() {
        return felt;
    }

    /** Whether this is worth sending after {@code other}: an episode begins, a bone breaks or mends, a sign moves. */
    public boolean differs(BodyPacket other) {
        return episode != other.episode || leg != other.leg || arm != other.arm
                || Math.abs(igni - other.igni) > 0.02F || Math.abs(aqua - other.aqua) > 0.02F
                || Math.abs(aura - other.aura) > 0.02F || Math.abs(firmo - other.firmo) > 0.02F
                || Math.abs(jump - other.jump) > 0.01F || Math.abs(dig - other.dig) > 0.01F;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(igni);
        buffer.writeFloat(aqua);
        buffer.writeFloat(aura);
        buffer.writeFloat(firmo);
        buffer.writeBoolean(leg);
        buffer.writeBoolean(arm);
        buffer.writeFloat(jump);
        buffer.writeFloat(dig);
        buffer.writeEnum(episode);
        buffer.writeVarInt(episodeLeft);
    }

    public static BodyPacket decode(FriendlyByteBuf buffer) {
        return new BodyPacket(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat(), buffer.readFloat(),
                buffer.readEnum(Symptoms.Episode.class), buffer.readVarInt());
    }

    public static void handle(BodyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> felt = packet);
        context.setPacketHandled(true);
    }
}
