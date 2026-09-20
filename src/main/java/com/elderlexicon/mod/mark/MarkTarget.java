package com.elderlexicon.mod.mark;

import net.minecraft.core.BlockPos;

public record MarkTarget(Type type, BlockPos blockPos, int entityId) {

    public static MarkTarget block(BlockPos pos) {
        return new MarkTarget(Type.BLOCK, pos, -1);
    }

    public static MarkTarget entity(int entityId) {
        return new MarkTarget(Type.ENTITY, null, entityId);
    }

    public enum Type {
        BLOCK,
        ENTITY
    }
}
