package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mind learns what sentences do from the lexicon alone (docs/feiticeiro-design.md, 3.2): written by what each rune
 * is, read by the grammar, imagined by the flow, one kept for each thing done.
 */
class PhraseCatalogTest {

    private static PhraseCatalog catalog;

    @BeforeAll
    static void make() {
        long start = System.nanoTime();
        catalog = PhraseCatalog.of(Lexicons.builtIn());
        long millis = (System.nanoTime() - start) / 1_000_000L;
        Map<String, Long> byOperation = new TreeMap<>(catalog.phrases().stream()
                .collect(Collectors.groupingBy(phrase -> phrase.acts().get(0).operation(), Collectors.counting())));
        System.out.println("[catalog] " + catalog.phrases().size() + " sentences in " + millis + " ms, first acts: "
                + byOperation);
    }

    @Test
    void itKnowsHowToThrowFireAtWhatItAims() {
        List<Phrase> found = catalog.doing(act -> act.operation().equals("project") && act.element() == VitaElement.IGNI
                && !act.reversed() && act.toward() != Act.Toward.SELF);
        assertFalse(found.isEmpty(), "no sentence throws fire");
        System.out.println("[catalog] throws fire: " + found.stream().limit(5).map(Phrase::text).toList());
    }

    @Test
    void itKnowsHowToDrawWaterOutOfWhatItAims() {
        List<Phrase> found = catalog.doing(act -> act.operation().equals("manifest") && act.element() == VitaElement.AQUA
                && act.reversed() && act.toward() == Act.Toward.AIM);
        assertFalse(found.isEmpty(), "no sentence draws water in");
        System.out.println("[catalog] draws water: " + found.stream().limit(5).map(Phrase::text).toList());
    }

    @Test
    void itKnowsHowToHideItself() {
        List<Phrase> found = catalog.doing(act -> act.toward() == Act.Toward.SELF && act.visibility().isPresent()
                && act.visibility().getAsDouble() == 0.0D);
        assertFalse(found.isEmpty(), "no sentence hides the caster");
        System.out.println("[catalog] hides itself: " + found.stream().limit(5).map(Phrase::text).toList());
    }

    @Test
    void eachThingDoneIsKeptOnce() {
        Set<String> signatures = new HashSet<>();
        for (Phrase phrase : catalog.phrases()) {
            assertTrue(signatures.add(phrase.signature()), "twice: " + phrase.signature());
        }
    }

    @Test
    void whoKnowsFewRunesReadsFewSentences() {
        List<Phrase> fire = catalog.readableBy(Set.of("igni", "vocant", "iactare"));
        assertFalse(fire.isEmpty());
        assertTrue(fire.stream().allMatch(phrase -> Set.of("igni", "vocant", "iactare").containsAll(phrase.runes())));
        assertTrue(fire.size() < catalog.phrases().size());
    }

    @Test
    void theCheapestSentenceIsKeptForWhatItDoes() {
        // A bare fire thrown at the aim: the grammar's shortest sentence for it.
        List<Phrase> bare = catalog.doing(act -> act.key().equals("project:igni>aim"));
        bare.forEach(phrase -> System.out.println("[catalog] project:igni>aim = " + phrase.text() + " (" + phrase.cost()
                + " UMU, " + phrase.signature() + ")"));
        assertEquals(1, bare.stream().filter(phrase -> phrase.acts().size() == 1).count());
    }
}
