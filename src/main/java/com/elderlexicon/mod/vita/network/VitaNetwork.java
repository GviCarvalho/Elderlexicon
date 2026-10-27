package com.elderlexicon.mod.vita.network;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network channel for what a player's body feels.
 */
public final class VitaNetwork {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ElderLexicon.MODID, "vita"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static int nextId = 0;

    private VitaNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                nextId++,
                BodyPacket.class,
                BodyPacket::encode,
                BodyPacket::decode,
                BodyPacket::handle
        );
    }

    public static void sendBody(ServerPlayer player, BodyPacket packet) {
        if (player.connection == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
