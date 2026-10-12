package com.elderlexicon.mod.spelling.data;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative storage for the nine-slot spelling repertoire, and for the runes the mage has unlocked: only those can
 * be put in it. A new mage knows no rune: each is learned by understanding it and escaping its dream
 * (docs/aprendizado-runas-design.md). In creative, every rune counts as unlocked.
 */
public final class SpellingRepertoire {

    public static final int SLOT_COUNT = 9;

    private final NonNullList<String> slots = NonNullList.withSize(SLOT_COUNT, "");
    private final Set<String> unlocked = new LinkedHashSet<>();
    /** What the mage has lived of each rune not yet learned (docs/aprendizado-runas-design.md), by rune. */
    private final Map<String, Set<String>> experienced = new LinkedHashMap<>();
    /** The runes the mage has understood whole, which only the dream still keeps from them. */
    private final Set<String> understood = new LinkedHashSet<>();
    /** Whether the mage can enter the trance (docs/habilidades-design.md): woken by a night with 100 XP. */
    private boolean trance;
    /** Whether the mage can enter the state of flow: won in the infinite tower of Nahvá. */
    private boolean flow;

    public SpellingRepertoire() {
        applyDefaults();
    }

    /** The runes a new mage is given to recite in trance, as the lexicon says. */
    public static List<String> defaultRunes() {
        return List.copyOf(Lexicons.get().repertoire());
    }

    public void applyDefaults() {
        List<String> defaults = defaultRunes();
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.set(i, i < defaults.size() ? defaults.get(i) : "");
        }
        unlocked.clear();
        unlocked.addAll(defaults);
    }

    /** The runes this mage has unlocked, in the order they were learned. */
    public Set<String> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public boolean isUnlocked(String runeId) {
        return runeId != null && unlocked.contains(runeId.toLowerCase(Locale.ROOT).trim());
    }

    /** Unlocks a rune; true when it was not unlocked before. */
    public boolean unlock(String runeId) {
        return runeId != null && !runeId.isBlank() && unlocked.add(runeId.toLowerCase(Locale.ROOT).trim());
    }

    /** Locks a rune again, taking it out of the slots it was in; true when it was unlocked. */
    public boolean lock(String runeId) {
        String normalized = runeId == null ? "" : runeId.toLowerCase(Locale.ROOT).trim();
        if (!unlocked.remove(normalized)) {
            return false;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (normalized.equals(slots.get(i))) {
                slots.set(i, "");
            }
        }
        return true;
    }

    public boolean hasTrance() {
        return trance;
    }

    /** Gives (or takes) the trance; true when it changed. */
    public boolean setTrance(boolean value) {
        boolean changed = trance != value;
        trance = value;
        return changed;
    }

    public boolean hasFlow() {
        return flow;
    }

    /** Gives (or takes) the state of flow; true when it changed. */
    public boolean setFlow(boolean value) {
        boolean changed = flow != value;
        flow = value;
        return changed;
    }

    /** Records that the mage lived {@code experience} of {@code runeId}; true when it is new. */
    public boolean experience(String runeId, String experience) {
        return experienced.computeIfAbsent(normalize(runeId), key -> new LinkedHashSet<>()).add(experience);
    }

    /** What the mage has lived of {@code runeId}. */
    public Set<String> experienced(String runeId) {
        return Collections.unmodifiableSet(experienced.getOrDefault(normalize(runeId), Set.of()));
    }

    /** The runes understood whole, waiting for their dream. */
    public Set<String> understood() {
        return Collections.unmodifiableSet(understood);
    }

    public boolean isUnderstood(String runeId) {
        return understood.contains(normalize(runeId));
    }

    /** Marks {@code runeId} understood whole; true when it was not before. */
    public boolean understand(String runeId) {
        return understood.add(normalize(runeId));
    }

    /** Replaces the understood runes (the client mirror, as the server says). */
    public void setUnderstood(Collection<String> runes) {
        understood.clear();
        runes.forEach(this::understand);
    }

    private static String normalize(String runeId) {
        return runeId == null ? "" : runeId.toLowerCase(Locale.ROOT).trim();
    }

    /** Replaces the unlocked runes (the client mirror, as the server says). */
    public void setUnlocked(Collection<String> runes) {
        unlocked.clear();
        runes.forEach(this::unlock);
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
        ListTag learned = new ListTag();
        for (String rune : unlocked) {
            learned.add(StringTag.valueOf(rune));
        }
        tag.put("unlocked", learned);
        CompoundTag lived = new CompoundTag();
        experienced.forEach((rune, experiences) -> {
            ListTag list2 = new ListTag();
            experiences.forEach(experience -> list2.add(StringTag.valueOf(experience)));
            lived.put(rune, list2);
        });
        tag.put("experienced", lived);
        ListTag whole = new ListTag();
        understood.forEach(rune -> whole.add(StringTag.valueOf(rune)));
        tag.put("understood", whole);
        tag.putBoolean("trance", trance);
        tag.putBoolean("flow", flow);
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
        unlocked.clear();
        if (tag.contains("unlocked", Tag.TAG_LIST)) {
            tag.getList("unlocked", Tag.TAG_STRING).forEach(rune -> unlock(rune.getAsString()));
        } else {
            // Saved before runes were unlocked: the mage keeps what they had in hand.
            unlocked.addAll(defaultRunes());
            slots.forEach(this::unlock);
        }
        experienced.clear();
        CompoundTag lived = tag.getCompound("experienced");
        for (String rune : lived.getAllKeys()) {
            lived.getList(rune, Tag.TAG_STRING).forEach(experience -> experience(rune, experience.getAsString()));
        }
        understood.clear();
        tag.getList("understood", Tag.TAG_STRING).forEach(rune -> understand(rune.getAsString()));
        trance = tag.getBoolean("trance");
        flow = tag.getBoolean("flow");
    }

    public List<String> copySlots() {
        return new ArrayList<>(slots);
    }

    public void copyFrom(SpellingRepertoire other) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.set(i, other.slots.get(i));
        }
        unlocked.clear();
        unlocked.addAll(other.unlocked);
        experienced.clear();
        other.experienced.forEach((rune, experiences) -> experienced.put(rune, new LinkedHashSet<>(experiences)));
        understood.clear();
        understood.addAll(other.understood);
        trance = other.trance;
        flow = other.flow;
    }
}
