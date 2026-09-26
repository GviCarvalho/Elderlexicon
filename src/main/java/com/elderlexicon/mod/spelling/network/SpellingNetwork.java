package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.ElderLexicon;
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
    private static final String PROTOCOL = "7";
        private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ElderLexicon.MODID, "spelling"),
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
        CHANNEL.registerMessage(
                nextPacketId++,
                RecitationPacket.class,
                RecitationPacket::encode,
                RecitationPacket::decode,
                RecitationPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                RevelationPacket.class,
                RevelationPacket::encode,
                RevelationPacket::decode,
                RevelationPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                VisibilityPacket.class,
                VisibilityPacket::encode,
                VisibilityPacket::decode,
                VisibilityPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                IllusionPacket.class,
                IllusionPacket::encode,
                IllusionPacket::decode,
                IllusionPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                LaunchPacket.class,
                LaunchPacket::encode,
                LaunchPacket::decode,
                LaunchPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                SightBondPacket.class,
                SightBondPacket::encode,
                SightBondPacket::decode,
                SightBondPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ProjectionPacket.class,
                ProjectionPacket::encode,
                ProjectionPacket::decode,
                ProjectionPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                SpiritPositionPacket.class,
                SpiritPositionPacket::encode,
                SpiritPositionPacket::decode,
                SpiritPositionPacket::handle
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

    /** Tells the server what the local player is reciting in trance. */
    public static void sendRecitation(List<String> runes, boolean finished) {
        CHANNEL.sendToServer(new RecitationPacket(0, runes, finished));
    }

    /** Shows {@code reciter}'s words to everyone who can see them (the reciter shows its own). */
    static void broadcastRecitation(ServerPlayer reciter, RecitationPacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> reciter), packet);
    }

    /** What the spirit sees in {@code player}'s revelation (surgit), for that player alone. */
    public static void sendRevelation(ServerPlayer player, RevelationPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** How much of {@code entity} is seen, to everyone who sees it and to the entity itself when it is a player. */
    public static void sendVisibility(net.minecraft.world.entity.Entity entity, VisibilityPacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }

    /** How much of a block is seen, to everyone watching its chunk. */
    public static void sendVisibility(net.minecraft.world.level.chunk.LevelChunk chunk, VisibilityPacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
    }

    /** An illusion appearing or going, to everyone watching its chunk. */
    public static void sendIllusion(net.minecraft.world.level.chunk.LevelChunk chunk, IllusionPacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
    }

    public static void sendIllusionTo(ServerPlayer viewer, IllusionPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
    }

    /** A player was thrown by a spell: everyone who sees it, and the player too, draws it flying. */
    public static void sendLaunch(net.minecraft.world.entity.Entity thrown) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> thrown), new LaunchPacket(thrown.getId()));
    }

    /** Binds {@code player}'s sight to another entity for a while: the client sees through its eyes. */
    public static void sendSightBond(ServerPlayer player, SightBondPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Sends {@code player}'s spirit out of its body, or calls it back. */
    public static void sendProjection(ServerPlayer player, ProjectionPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Tells the server where the local player's projected spirit is. */
    public static void sendSpiritPosition(net.minecraft.world.phys.Vec3 at) {
        CHANNEL.sendToServer(new SpiritPositionPacket(at));
    }

    public static void sendVisibilityTo(ServerPlayer viewer, VisibilityPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
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
