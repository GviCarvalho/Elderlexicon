package com.elderlexicon.mod.ligabis;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class LigabisGrammarTest {

    private static final Set<String> RUNES = Set.of("firmo", "igni", "aqua", "aura", "vis", "ligabis", "vertere",
            "iactare", "vocant", "exsugat", "surgit", "quantum", "chronos", "ubis");
    private static final Predicate<String> IS_RUNE = RUNES::contains;

    private static LigabisGrammar.Result parse(String... words) {
        return LigabisGrammar.parse(List.of(words), IS_RUNE);
    }

    @Test
    void aMarkBeforeLigabisIsAMirror() {
        LigabisGrammar.Parsed parsed = parse("firmo", "marca1", "ligabis").parsed();

        assertEquals(Aspect.FIRMO, parsed.aspect());
        assertEquals(List.of("marca1"), parsed.marks());
        assertTrue(Link.chain(FirmoWorld.OWNER, parsed.aspect(), parsed.marks()).get(0).isMirror());
    }

    @Test
    void aMarkOnEachSideIsAHierarchy() {
        LigabisGrammar.Parsed parsed = parse("firmo", "marca1", "ligabis", "marca2").parsed();

        assertEquals(List.of("marca1", "marca2"), parsed.marks());
        List<Link> links = Link.chain(FirmoWorld.OWNER, parsed.aspect(), parsed.marks());
        assertEquals(1, links.size());
        assertFalse(links.get(0).isMirror());
    }

    @Test
    void moreLigabisMakeAChain() {
        LigabisGrammar.Parsed parsed = parse("firmo", "m1", "ligabis", "m2", "ligabis", "m3").parsed();

        assertEquals(List.of("m1", "m2", "m3"), parsed.marks());
        assertEquals(2, Link.chain(FirmoWorld.OWNER, parsed.aspect(), parsed.marks()).size());
    }

    @Test
    void everyAspectIsRecognised() {
        assertEquals(Aspect.IGNI, parse("igni", "x", "ligabis").parsed().aspect());
        assertEquals(Aspect.AQUA, parse("aqua", "x", "ligabis").parsed().aspect());
        assertEquals(Aspect.AURA, parse("aura", "x", "ligabis").parsed().aspect());
    }

    @Test
    void theOlderFormWithTheMarkAfterLigabisStillWorks() {
        assertEquals(List.of("casa"), parse("firmo", "ligabis", "casa").parsed().marks());
    }

    @Test
    void marksAreLowercased() {
        assertEquals(List.of("marca"), parse("FIRMO", "MaRcA", "LIGABIS").parsed().marks());
    }

    @Test
    void aWordThatIsARuneIsNeverAMark() {
        // "vertere" belongs to the old one-way form and must not become a mark.
        assertEquals(List.of("casa"), parse("firmo", "ligabis", "vertere", "casa").parsed().marks());
        assertFalse(parse("firmo", "ligabis", "vocant").ok(), "no mark left");
    }

    @Test
    void anAspectIsRequired() {
        LigabisGrammar.Result result = parse("marca", "ligabis");

        assertFalse(result.ok());
        assertTrue(result.error().contains("aspecto"));
    }

    @Test
    void ligabisIsRequired() {
        assertFalse(parse("firmo", "marca").ok());
    }

    @Test
    void aMarkIsRequired() {
        assertFalse(parse("firmo", "ligabis").ok());
    }

    @Test
    void twoMarksInARowAreRejected() {
        assertFalse(parse("firmo", "a", "b", "ligabis").ok());
        assertFalse(parse("firmo", "a", "ligabis", "b", "c").ok());
    }

    @Test
    void blankAndNullWordsAreIgnored() {
        assertEquals(List.of("x"),
                LigabisGrammar.parse(Arrays.asList("firmo", " ", null, "x", "ligabis"), IS_RUNE).parsed().marks());
    }
}
