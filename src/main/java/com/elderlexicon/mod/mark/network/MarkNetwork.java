package com.elderlexicon.mod.mark.network;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.mark.MarkTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network channel dedicated to Mark-related packets.
 */
public final class MarkNetwork {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "mark"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static int nextId = 0;

    private MarkNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                nextId++,
                SetMarkPacket.class,
                SetMarkPacket::encode,
                SetMarkPacket::decode,
                SetMarkPacket::handle
        );
    }

    public static void sendSetMark(MarkTarget target, String mark) {
        CHANNEL.sendToServer(new SetMarkPacket(target, mark));
    }
}
