package com.elderlexicon.mod.ligabis.network;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Network channel dedicated to Ligabis packets. */
public final class LigabisNetwork {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "ligabis"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static int nextId = 0;

    private LigabisNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                nextId++,
                PlayerMotionInputPacket.class,
                PlayerMotionInputPacket::encode,
                PlayerMotionInputPacket::decode,
                PlayerMotionInputPacket::handle
        );
    }

    public static void sendMotionInput(float xxa, float zza) {
        CHANNEL.sendToServer(new PlayerMotionInputPacket(xxa, zza));
    }
}
