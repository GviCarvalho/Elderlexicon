package com.elderlexicon.mod.ligabis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReadingBondTest {

    private static final UUID MAGE = UUID.randomUUID();

    @Test
    void aVisLinkFromTheMagesMarkBindsTheScrolls() {
        List<Link> links = Link.chain(MAGE, Aspect.VIS, List.of("eu", "r1"));
        assertTrue(ReadingBond.bound(links, Set.of("eu"), "r1"));
        assertTrue(ReadingBond.bound(Link.chain(MAGE, Aspect.VIS, List.of("r1", "eu")), Set.of("eu"), "r1"),
                "either way round");
    }

    @Test
    void otherAspectsDoNotBindForReading() {
        List<Link> links = Link.chain(MAGE, Aspect.FIRMO, List.of("eu", "r1"));
        assertFalse(ReadingBond.bound(links, Set.of("eu"), "r1"));
    }

    @Test
    void theMageMustCarryTheOtherEnd() {
        List<Link> links = Link.chain(MAGE, Aspect.VIS, List.of("eu", "r1"));
        assertFalse(ReadingBond.bound(links, Set.of("outro"), "r1"));
        assertFalse(ReadingBond.bound(links, Set.of("eu"), "r2"));
    }

    @Test
    void theGrammarKnowsTheVisBond() {
        Optional<LigabisGrammar.Parsed> parsed = LigabisGrammar.tryParse(List.of("vis", "eu", "ligabis", "r1"),
                word -> Set.of("vis", "ligabis").contains(word));
        assertEquals(Aspect.VIS, parsed.orElseThrow().aspect());
        assertEquals(List.of("eu", "r1"), parsed.get().marks());
    }
}
