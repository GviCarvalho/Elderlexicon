package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spelling.network.RevelationPacket;

import java.util.List;

/**
 * The revelation the local player is seeing (surgit): while it lasts the world is a void and only what the spirit
 * found is drawn in it ({@link RevelationRenderer}).
 */
public final class ClientRevelation {

    private static int ticksLeft;
    private static int color;
    private static List<RevelationPacket.SeenEntity> entities = List.of();
    private static List<RevelationPacket.SeenBlock> blocks = List.of();
    private static List<RevelationPacket.SeenSpirit> spirits = List.of();

    private ClientRevelation() {
    }

    public static boolean active() {
        return ticksLeft > 0;
    }

    public static int color() {
        return color;
    }

    public static List<RevelationPacket.SeenEntity> entities() {
        return entities;
    }

    public static List<RevelationPacket.SeenBlock> blocks() {
        return blocks;
    }

    public static List<RevelationPacket.SeenSpirit> spirits() {
        return spirits;
    }

    public static void apply(RevelationPacket packet) {
        if (packet.ticksLeft() <= 0) {
            end();
            return;
        }
        ticksLeft = packet.ticksLeft();
        color = packet.color();
        entities = packet.entities();
        blocks = packet.blocks();
        spirits = packet.spirits();
    }

    /** Counts the gaze down, in case its end never arrives. */
    public static void tick() {
        if (ticksLeft > 0 && --ticksLeft <= 0) {
            end();
        }
    }

    public static void end() {
        ticksLeft = 0;
        entities = List.of();
        blocks = List.of();
        spirits = List.of();
    }
}
