package com.elderlexicon.mod.spelling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The illusions this client is shown: images of blocks by place, and images of marked things. */
public final class ClientIllusions {

    /**
     * An image of the entity {@code sourceId} travelling from {@code from} to {@code to} at {@code speed} blocks a tick,
     * from the game time {@code startTime}, and staying there.
     */
    public record ThingImage(int sourceId, Vec3 from, Vec3 to, double speed, double startTime) {

        /** Where the image is at the game time {@code now}. */
        public Vec3 at(double now) {
            double length = from.distanceTo(to);
            if (speed <= 0.0D || length < 1.0E-6D) {
                return to;
            }
            double done = Math.min(1.0D, Math.max(0.0D, (now - startTime) * speed / length));
            return from.lerp(to, done);
        }
    }

    private static final Map<BlockPos, BlockState> BLOCKS = new ConcurrentHashMap<>();
    private static final Map<Integer, ThingImage> THINGS = new ConcurrentHashMap<>();

    private ClientIllusions() {
    }

    public static void showBlock(BlockPos pos, BlockState state) {
        BLOCKS.put(pos.immutable(), state);
    }

    public static void removeBlock(BlockPos pos) {
        BLOCKS.remove(pos);
    }

    public static void showThing(int imageId, int sourceId, Vec3 from, Vec3 to, double speed, int elapsed) {
        Minecraft minecraft = Minecraft.getInstance();
        double now = minecraft.level == null ? 0.0D : minecraft.level.getGameTime();
        THINGS.put(imageId, new ThingImage(sourceId, from, to, speed, now - elapsed));
    }

    public static void removeThing(int imageId) {
        THINGS.remove(imageId);
    }

    public static Map<BlockPos, BlockState> blocks() {
        return BLOCKS;
    }

    public static Map<Integer, ThingImage> things() {
        return THINGS;
    }

    public static void clear() {
        BLOCKS.clear();
        THINGS.clear();
    }
}
