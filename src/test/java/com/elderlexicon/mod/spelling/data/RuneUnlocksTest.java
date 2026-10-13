package com.elderlexicon.mod.spelling.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Runes are learned one by one, with no hierarchy; the repertoire keeps which ones (docs/repertorio-design.md). */
class RuneUnlocksTest {

    @Test
    void aNewMageKnowsNoRune() {
        SpellingRepertoire repertoire = new SpellingRepertoire();
        assertEquals(Set.of(), repertoire.unlocked());
        assertFalse(repertoire.isUnlocked("igni"));
        assertTrue(repertoire.slots().stream().allMatch(String::isEmpty), "and has nothing in hand to recite");
    }

    @Test
    void anyRuneIsLearnedOnItsOwnAndForgottenOutOfTheSlots() {
        SpellingRepertoire repertoire = new SpellingRepertoire();
        assertTrue(repertoire.unlock("ferit"), "no other rune is needed first");
        assertFalse(repertoire.unlock("FERIT"), "learned once");
        assertTrue(repertoire.isUnlocked("ferit"));
        repertoire.assignSlot(8, "ferit");
        assertTrue(repertoire.lock("ferit"));
        assertEquals("", repertoire.slots().get(8), "a rune forgotten leaves the slot it was in");
        assertFalse(repertoire.isUnlocked("ferit"));
    }

    @Test
    void aNewPlayerIsNoMageYetAndWhatTheyBecomeIsKept() {
        SpellingRepertoire repertoire = new SpellingRepertoire();
        assertFalse(repertoire.hasTrance(), "before a night with 100 XP, this is only Minecraft");
        assertFalse(repertoire.hasFlow());
        assertTrue(repertoire.setTrance(true));
        assertFalse(repertoire.setTrance(true), "woken once");
        SpellingRepertoire loaded = new SpellingRepertoire();
        loaded.load(repertoire.save());
        assertTrue(loaded.hasTrance());
        assertFalse(loaded.hasFlow());
    }

    @Test
    void whatWasLearnedIsKept() {
        SpellingRepertoire repertoire = new SpellingRepertoire();
        repertoire.unlock("quantum");
        repertoire.lock("aura");
        SpellingRepertoire loaded = new SpellingRepertoire();
        loaded.load(repertoire.save());
        assertEquals(repertoire.unlocked(), loaded.unlocked());
        assertEquals(repertoire.slots(), loaded.slots());
    }

    @Test
    void aMageSavedBeforeRunesWereLearnedKnowsWhatTheyWereGivenAndHad() {
        CompoundTag old = new CompoundTag();
        ListTag slots = new ListTag();
        for (String rune : List.of("igni", "chronos", "", "", "", "", "", "", "")) {
            slots.add(StringTag.valueOf(rune));
        }
        old.put("slots", slots);
        SpellingRepertoire loaded = new SpellingRepertoire();
        loaded.load(old);
        assertTrue(loaded.isUnlocked("chronos"), "a rune they had in hand");
        assertTrue(loaded.isUnlocked("igni"));
        assertFalse(loaded.isUnlocked("ferit"));
    }
}
