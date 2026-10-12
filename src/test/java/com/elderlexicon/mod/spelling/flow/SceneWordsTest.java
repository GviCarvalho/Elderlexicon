package com.elderlexicon.mod.spelling.flow;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which lines keep ego and ille as lasting marks: the bonds (docs/fluxo-design.md). */
class SceneWordsTest {

    private static boolean bonds(String line) {
        return SceneWords.bonds(Arrays.asList(line.split(" ")));
    }

    @Test
    void aBondKeepsItsMarksForGood() {
        assertTrue(bonds("firmo ego ligabis ille"));
        assertTrue(bonds("ferit vis ego ligabis ille"));
    }

    @Test
    void aBondOfASenseReadsItsThingOnce() {
        assertFalse(bonds("surgit ille ligabis"));
    }

    @Test
    void aSpellWithNoBondBindsTheSceneForItsCasting() {
        assertFalse(bonds("ferit vis vertere igni ille ubis vocant"));
    }
}
