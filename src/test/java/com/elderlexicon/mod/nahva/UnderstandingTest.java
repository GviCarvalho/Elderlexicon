package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** A rune is understood by living it, then learned in its dream (docs/aprendizado-runas-design.md). */
class UnderstandingTest {

    @Test
    void igniIsUnderstoodByBeingColdAndHotAndBurningAndFreezing() {
        Understanding.RuneLesson igni = Understanding.lesson("igni").orElseThrow();
        assertEquals(List.of("igni_cold", "igni_hypothermia", "igni_warm", "igni_boiling", "burning", "freezing"),
                igni.experiences());
        assertEquals("igni", igni.realm());
        assertTrue(RealmLayout.REALMS.contains(igni.realm()), "its dream has a realm in Nahvá");
    }

    @Test
    void whatWasLivedAndUnderstoodIsKept() {
        SpellingRepertoire repertoire = new SpellingRepertoire();
        assertTrue(repertoire.experience("igni", "burning"));
        assertFalse(repertoire.experience("igni", "burning"), "lived once");
        repertoire.understand("igni");
        SpellingRepertoire loaded = new SpellingRepertoire();
        loaded.load(repertoire.save());
        assertEquals(Set.of("burning"), loaded.experienced("igni"));
        assertTrue(loaded.isUnderstood("igni"));
        assertFalse(loaded.isUnlocked("ferit"));
    }

    @Test
    void eachRealmIsFarFromEveryOther() {
        assertEquals("igni", RealmLayout.realmAt(0.0D));
        assertEquals("igni", RealmLayout.realmAt(300.0D));
        assertEquals("", RealmLayout.realmAt(-RealmLayout.SPACING));
        assertEquals("tower", RealmLayout.realmAt(2 * RealmLayout.SPACING + 40.0D), "the infinite tower has its own realm");
        assertTrue(RealmLayout.SPACING > IgniRealm.LENGTH * 4, "no realm can be seen from another");
    }
}
