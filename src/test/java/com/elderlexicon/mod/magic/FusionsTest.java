package com.elderlexicon.mod.magic;

import com.elderlexicon.mod.magic.lexicon.Fusions;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Which fusion two runes make is read from the parts the lexicon gives each fusion. */
class FusionsTest {

    private static final Lexicon LEXICON = Lexicons.builtIn();

    @Test
    void twoSourcesMakeTheirFusionInEitherOrder() {
        assertEquals(Optional.of("fusus"), Fusions.fuse(LEXICON, "igni", "firmo"));
        assertEquals(Optional.of("fusus"), Fusions.fuse(LEXICON, "firmo", "igni"));
        assertEquals(Optional.of("lutum"), Fusions.fuse(LEXICON, "aqua", "firmo"));
        assertEquals(Optional.of("nebula"), Fusions.fuse(LEXICON, "aqua", "aura"));
        assertEquals(Optional.of("fulmen"), Fusions.fuse(LEXICON, "IGNI", " aura "));
    }

    @Test
    void aVerbWithAnySourceMakesItsForm() {
        // The first form the lexicon gives for a pair is the one it makes, every time.
        assertEquals(Optional.of("hasta"), Fusions.fuse(LEXICON, "iactare", "igni"));
        assertEquals(Optional.of("hasta"), Fusions.fuse(LEXICON, "aqua", "iactare"));
        assertEquals(Optional.of("hasta"), Fusions.fuse(LEXICON, "iactare", "fusus"), "a fused source is a source too");
        assertEquals(Optional.of("vortex"), Fusions.fuse(LEXICON, "vertere", "aura"));
        assertEquals(Optional.of("murus"), Fusions.fuse(LEXICON, "firmo", "impediunt"));
    }

    @Test
    void twoVerbsMakeTheirFusion() {
        assertEquals(Optional.of("transiectio"), Fusions.fuse(LEXICON, "vertere", "iactare"));
        assertEquals(Optional.of("transvocatio"), Fusions.fuse(LEXICON, "vocant", "vertere"));
        assertEquals(Optional.of("extractio"), Fusions.fuse(LEXICON, "exsugat", "vocant"));
    }

    @Test
    void aRuneFusedWithItselfIsItself() {
        assertEquals(Optional.of("igni"), Fusions.fuse(LEXICON, "igni", "igni"));
        assertEquals(Optional.of("vertere"), Fusions.fuse(LEXICON, "vertere", "VERTERE"));
    }

    @Test
    void whatNoFusionJoinsMakesNothing() {
        assertEquals(Optional.empty(), Fusions.fuse(LEXICON, "igni", "quantum"));
        assertEquals(Optional.empty(), Fusions.fuse(LEXICON, "iactare", "quantum"));
        assertEquals(Optional.empty(), Fusions.fuse(LEXICON, "igni", "m1"));
        assertEquals(Optional.empty(), Fusions.fuse(LEXICON, "igni", ""));
        assertEquals(Optional.empty(), Fusions.fuse(LEXICON, null, "igni"));
    }
}
