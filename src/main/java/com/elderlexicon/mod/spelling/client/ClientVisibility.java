package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spell.sight.Visibility;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How much of each entity and block this client is told it may see (surgit m1 quantum N); absent means whole. Read
 * from the chunk builder's threads too ({@link HiddenBlocks}), hence the concurrent maps.
 */
public final class ClientVisibility {

    private static final Map<Integer, Integer> ENTITIES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> BLOCKS = new ConcurrentHashMap<>();

    private ClientVisibility() {
    }

    public static void set(int entityId, int level) {
        if (level >= Visibility.SEEN) {
            ENTITIES.remove(entityId);
        } else {
            ENTITIES.put(entityId, Math.max(Visibility.HIDDEN, level));
        }
    }

    public static OptionalInt of(int entityId) {
        Integer level = ENTITIES.get(entityId);
        return level == null ? OptionalInt.empty() : OptionalInt.of(level);
    }

    public static Map<Integer, Integer> entities() {
        return ENTITIES;
    }

    public static void setBlock(BlockPos pos, int level) {
        BlockPos key = pos.immutable();
        if (level >= Visibility.SEEN) {
            BLOCKS.remove(key);
        } else {
            BLOCKS.put(key, Math.max(Visibility.HIDDEN, level));
        }
        // The block's chunk section is built again, with or without it, and so are its neighbours', whose faces
        // against it open or close.
        Minecraft.getInstance().levelRenderer.setBlocksDirty(key.getX() - 1, key.getY() - 1, key.getZ() - 1,
                key.getX() + 1, key.getY() + 1, key.getZ() + 1);
    }

    public static OptionalInt ofBlock(BlockPos pos) {
        if (BLOCKS.isEmpty()) {
            return OptionalInt.empty();
        }
        Integer level = BLOCKS.get(pos);
        return level == null ? OptionalInt.empty() : OptionalInt.of(level);
    }

    public static Map<BlockPos, Integer> blocks() {
        return BLOCKS;
    }

    public static void clear() {
        ENTITIES.clear();
        BLOCKS.clear();
    }
}
