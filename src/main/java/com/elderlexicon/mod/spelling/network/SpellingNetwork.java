package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.server.ServerSpellingController;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

import java.util.List;

/**
 * Creates and owns the dedicated SimpleChannel for Spelling packets.
 */
public final class SpellingNetwork {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTOCOL = "1";
        private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "spelling"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int nextPacketId = 0;

    private SpellingNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                nextPacketId++,
                ClientSpellCastPacket.class,
                ClientSpellCastPacket::encode,
                ClientSpellCastPacket::decode,
                ClientSpellCastPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ServerRepertoireSyncPacket.class,
                ServerRepertoireSyncPacket::encode,
                ServerRepertoireSyncPacket::decode,
                ServerRepertoireSyncPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ClientRepertoireUpdatePacket.class,
                ClientRepertoireUpdatePacket::encode,
                ClientRepertoireUpdatePacket::decode,
                ClientRepertoireUpdatePacket::handle
        );
        CHANNEL.registerMessage(
            nextPacketId++,
            ServerSpellCastResultPacket.class,
            ServerSpellCastResultPacket::encode,
            ServerSpellCastResultPacket::decode,
            ServerSpellCastResultPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                RequestImprovisedWandPacket.class,
                RequestImprovisedWandPacket::encode,
                RequestImprovisedWandPacket::decode,
                RequestImprovisedWandPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ClientGrimoireUpdatePacket.class,
                ClientGrimoireUpdatePacket::encode,
                ClientGrimoireUpdatePacket::decode,
                ClientGrimoireUpdatePacket::handle
        );
        LOGGER.info("Spelling network channel ready (protocol {}).", PROTOCOL);
    }

    public static void sendSpellCast(List<String> runes, long activationTimestamp) {
        CHANNEL.sendToServer(new ClientSpellCastPacket(runes, activationTimestamp));
    }

    public static void sendRepertoireUpdate(List<String> slots) {
        CHANNEL.sendToServer(new ClientRepertoireUpdatePacket(slots));
    }

    public static void syncToPlayer(ServerPlayer player, SpellingRepertoire repertoire) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), ServerRepertoireSyncPacket.from(repertoire));
    }

    public static void sendSpellCastResult(ServerPlayer player, ServerSpellingController.SpellCastResponse response) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), ServerSpellCastResultPacket.from(response));
    }

    public static void requestImprovisedWand(InteractionHand hand) {
        CHANNEL.sendToServer(new RequestImprovisedWandPacket(hand));
    }

    public static void sendGrimoireUpdate(InteractionHand hand,
                                          List<String> pages,
                                          int currentPage,
                                          boolean detachPage,
                                          String detachedText,
                                          int detachedIndex) {
        CHANNEL.sendToServer(new ClientGrimoireUpdatePacket(hand, pages, currentPage, detachPage, detachedText, detachedIndex));
    }
}
