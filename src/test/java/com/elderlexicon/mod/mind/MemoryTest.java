package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A caster learns from what came of its readings, and varies what worked (docs/feiticeiro-design.md, 3.5). */
class MemoryTest {

    private static Option sentence(String text) {
        return Option.of(PhraseCatalog.imagine(List.of(text.split(" "))).orElseThrow());
    }

    @Test
    void whatDidBetterThanForeseenIsWorthMoreAgainstThatOne() {
        Memory memory = new Memory();
        UUID one = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Option fire = sentence("igni iactare");
        memory.outcome(one, fire, 4.0D, 8.0D);
        assertTrue(memory.factor(one, fire) > 1.0D);
        assertTrue(memory.worked(one, fire));
        assertEquals(1.0D, memory.factor(other, fire), "another opponent is another fight");
    }

    @Test
    void whatNeverLandsIsWorthLess() {
        Memory memory = new Memory();
        UUID one = UUID.randomUUID();
        Option fire = sentence("igni iactare");
        memory.outcome(one, fire, 4.0D, 0.0D);
        memory.outcome(one, fire, 4.0D, 0.0D);
        assertTrue(memory.factor(one, fire) < 0.6D, "factor " + memory.factor(one, fire));
        assertFalse(memory.worked(one, fire));
    }

    @Test
    void oneWhoBreaksItsTranceTwiceIsKeptAtADistance() {
        Memory memory = new Memory();
        UUID one = UUID.randomUUID();
        memory.broken(one);
        assertEquals(0.0D, memory.safeDistance(one));
        memory.broken(one);
        assertEquals(Memory.SAFE_DISTANCE, memory.safeDistance(one));
    }

    @Test
    void aVariationDoesTheSameWithAnotherEnergy() {
        PhraseCatalog catalog = PhraseCatalog.of(Lexicons.builtIn());
        Set<String> known = Set.of("igni", "aqua", "firmo", "iactare", "vocant");
        Option fire = sentence("igni iactare");
        List<Option> variations = Variations.of(fire, catalog.readableBy(known), RandomSource.create(1L), 2);
        assertFalse(variations.isEmpty());
        for (Option variation : variations) {
            assertEquals(Variations.shape(PhraseCatalog.imagine(fire.lines().get(0).words()).orElseThrow()),
                    Variations.shape(PhraseCatalog.imagine(variation.lines().get(0).words()).orElseThrow()));
            assertFalse(variation.words().contains("igni"), variation.name());
        }
        // A volley stays a volley: every line that was the same is varied the same.
        Option volley = Option.page("salva", List.of(new Option.Line(List.of("igni", "iactare"), 0),
                new Option.Line(List.of("igni", "iactare"), 2))).orElseThrow();
        Option varied = Variations.of(volley, catalog.readableBy(known), RandomSource.create(2L), 1).get(0);
        assertEquals(varied.lines().get(0).words(), varied.lines().get(1).words());
        assertEquals(2, varied.lines().get(1).delaySteps());
    }
}
