package com.elderlexicon.mod.spelling.data;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Authoritative storage for the nine-slot spelling repertoire.
 */
public final class SpellingRepertoire {

    public static final int SLOT_COUNT = 9;
    private static final List<String> DEFAULT_RUNES = List.of(
            "igni",
            "aqua",
            "aura",
            "firmo",
            "impediunt",
            "vertere",
            "vocant",
            "murus",
            "iactare"
    );

    private final NonNullList<String> slots = NonNullList.withSize(SLOT_COUNT, "");

    public SpellingRepertoire() {
        applyDefaults();
    }

    public static List<String> defaultRunes() {
        return List.copyOf(DEFAULT_RUNES);
    }

    public void applyDefaults() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.set(i, DEFAULT_RUNES.get(i));
        }
    }

    public List<String> slots() {
        return Collections.unmodifiableList(slots);
    }

    public Optional<String> runeForSlot(int index) {
        if (index < 0 || index >= SLOT_COUNT) {
            return Optional.empty();
        }
        String value = slots.get(index);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public boolean assignSlot(int index, String runeId) {
        if (index < 0 || index >= SLOT_COUNT) {
            return false;
        }
        String normalized = runeId == null ? "" : runeId.toLowerCase(Locale.ROOT).trim();
        String current = slots.get(index);
        if (current == null) {
            current = "";
        }
        if (current.equals(normalized)) {
            return false;
        }
        slots.set(index, normalized);
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (String slot : slots) {
            list.add(StringTag.valueOf(slot == null ? "" : slot));
        }
        tag.put("slots", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null) {
            applyDefaults();
            return;
        }
        ListTag list = tag.getList("slots", Tag.TAG_STRING);
        if (list.size() != SLOT_COUNT) {
            applyDefaults();
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.set(i, list.getString(i));
        }
    }

    public List<String> copySlots() {
        return new ArrayList<>(slots);
    }

    public void copyFrom(SpellingRepertoire other) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.set(i, other.slots.get(i));
        }
    }
}
