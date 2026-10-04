package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spell.mark.SpellWords;

import java.util.Locale;
import java.util.Optional;

/**
 * Utility to read/write persistent Marks from entities, block entities or items.
 * Marks are stored under both the mod key (elderlexicon:mark) and a plain "Mark" fallback.
 */
public final class MarkHelper {

    private static final String MARK_KEY = ElderLexicon.MODID + ":mark";
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
        markHeldScroll(entity, mark);
        LigabisManager manager = LigabisManager.get();
        if (manager != null) {
            manager.entityMarkChanged(entity);
        }
        return true;
    }

    /**
     * A scroll in a frame or on the ground carries the mark itself too, so taking it down keeps the mark: it goes
     * wherever the item goes, and placing it again needs no new marking.
     */
    private static void markHeldScroll(Entity entity, String mark) {
        if (entity instanceof ItemFrame frame && frame.getItem().getItem() instanceof SpellScrollItem) {
            ItemStack scroll = frame.getItem().copy();
            writeMark(scroll.getOrCreateTag(), mark);
            frame.setItem(scroll, false);
        } else if (entity instanceof PlacedScrollEntity placed && !placed.getScroll().isEmpty()) {
            ItemStack scroll = placed.getScroll().copy();
            writeMark(scroll.getOrCreateTag(), mark);
            placed.setScroll(scroll);
        }
    }

    public static boolean applyMark(ServerLevel level, BlockPos pos, String rawMark) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return false;
        }
        String mark = sanitize(rawMark);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            writeMark(blockEntity.getPersistentData(), mark);
            blockEntity.setChanged();
        }
        // A plain block cannot carry a mark of its own, so the Ligabis manager keeps it.
        LigabisManager manager = LigabisManager.get();
        boolean kept = manager != null && manager.blockMarkChanged(level, pos, mark);
        return blockEntity != null || kept;
    }

    /**
     * The mark a name given at the anvil makes: lower case, spaces as {@code _}, and only letters, digits and {@code _}
     * kept ("Varinha do Gui" is {@code varinha_do_gui}). Empty when what is left cannot be written as a mark: fewer than
     * two characters, no letter, or a word of the language.
     */
    public static Optional<String> markFromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String mark = name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_").replaceAll("[^\\p{L}\\p{N}_]", "");
        boolean written = SpellWords.classify(mark, word -> Lexicons.get().isRune(word)) == SpellWords.Kind.MARK;
        return written ? Optional.of(mark) : Optional.empty();
    }

    /** Puts {@code mark} on an item (or takes its mark away, with {@code null}). */
    public static void applyMark(ItemStack stack, String mark) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String sanitized = sanitize(mark);
        if (sanitized == null && stack.getTag() == null) {
            return;
        }
        writeMark(stack.getOrCreateTag(), sanitized);
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
