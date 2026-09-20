package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Locale;
import java.util.Optional;

/**
 * Utility to read/write persistent Marks from entities, block entities or items.
 * Marks are stored under both the mod key (elderlexicon:mark) and a plain "Mark" fallback.
 */
public final class MarkHelper {

    private static final String MARK_KEY = ExampleMod.MODID + ":mark";
    private static final String LEGACY_KEY = "Mark";

    private MarkHelper() {
    }

    public static Optional<String> markForEntity(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        CompoundTag data = entity.getPersistentData();
        String mark = readMark(data);
        if (mark == null && entity instanceof ItemEntity itemEntity) {
            mark = markForItem(itemEntity.getItem()).orElse(null);
        }
        return Optional.ofNullable(sanitize(mark));
    }

    public static Optional<String> markForBlock(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sanitize(readMark(blockEntity.getPersistentData())));
    }

    public static Optional<String> markForItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sanitize(readMark(tag)));
    }

    public static boolean applyMark(Entity entity, String rawMark) {
        if (entity == null) {
            return false;
        }
        String mark = sanitize(rawMark);
        CompoundTag data = entity.getPersistentData();
        writeMark(data, mark);
        return true;
    }

    public static boolean applyMark(ServerLevel level, BlockPos pos, String rawMark) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        String mark = sanitize(rawMark);
        writeMark(blockEntity.getPersistentData(), mark);
        blockEntity.setChanged();
        return true;
    }

    public static String sanitizeMark(String raw) {
        return sanitize(raw);
    }

    private static String readMark(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        if (tag.contains(MARK_KEY, 8)) {
            return tag.getString(MARK_KEY);
        }
        if (tag.contains(LEGACY_KEY, 8)) {
            return tag.getString(LEGACY_KEY);
        }
        return null;
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static void writeMark(CompoundTag tag, String mark) {
        if (tag == null) {
            return;
        }
        if (mark == null) {
            tag.remove(MARK_KEY);
            tag.remove(LEGACY_KEY);
        } else {
            tag.putString(MARK_KEY, mark);
        }
    }
}
