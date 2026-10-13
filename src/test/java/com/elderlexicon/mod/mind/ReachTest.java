package com.elderlexicon.mod.mind;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a caster reads must have a source and get to its target (docs/feiticeiro-design.md, 3.7): a throw with no source
 * is a harmless beam; a throw goes twenty blocks unless a distance is written, and one who knows how to write a
 * distance writes it.
 */
class ReachTest {

    private static Option option(String... words) {
        return Option.of(PhraseCatalog.imagine(List.of(words)).orElseThrow());
    }

    private static Mind.Scene at(double distance) {
        return new Mind.Scene(null, null, distance, 1.0F, 1000.0D);
    }

    @Test
    void aThrowWithNoSourceHurtsNoOne() {
        Option bare = option("iactare");
        assertNull(Mind.serves(bare.acts().get(0)), "iactare alone is a beam of light");
        assertTrue(Mind.worthWeighing(List.of(bare)).isEmpty());
        assertFalse(Mind.worthWeighing(List.of(option("igni", "iactare"))).isEmpty());
    }

    @Test
    void aThrowGetsTwentyBlocks() {
        Option fire = option("igni", "iactare");
        assertEquals(Reach.THROW, Reach.of(fire), 1.0E-9D);
        Mind.Foresight person = Mind.person(0.0D);
        assertTrue(Mind.evaluate(at(15.0D), Personality.AGGRESSIVE, fire, person).isPresent());
        assertTrue(Mind.evaluate(at(25.0D), Personality.AGGRESSIVE, fire, person).isEmpty(),
                "what falls short does nothing");
    }

    @Test
    void aDistanceWrittenTakesItFarther() {
        Option far = option("igni", "40", "ubis", "iactare");
        assertEquals(40.0D, Reach.of(far), 1.0E-9D);
        assertTrue(Mind.evaluate(at(30.0D), Personality.AGGRESSIVE, far, Mind.person(0.0D)).isPresent());
    }

    @Test
    void oneWhoKnowsHowWritesTheDistance() {
        Option fire = option("igni", "iactare");
        Optional<Option> farther = Reach.farther(fire, 30.0D, Set.of("igni", "iactare", "ubis"));
        assertTrue(farther.isPresent());
        assertTrue(farther.get().words().contains("ubis"), farther.get().words().toString());
        assertTrue(Reach.of(farther.get()) >= 30.0D);
        assertTrue(Mind.evaluate(at(30.0D), Personality.AGGRESSIVE, farther.get(), Mind.person(0.0D)).isPresent());
    }

    @Test
    void oneWhoDoesNotMustWalkUp() {
        assertTrue(Reach.farther(option("igni", "iactare"), 30.0D, Set.of("igni", "iactare")).isEmpty());
    }

    @Test
    void whatGetsThereAlreadyIsNotRewritten() {
        assertTrue(Reach.farther(option("igni", "iactare"), 10.0D, Set.of("igni", "iactare", "ubis")).isEmpty());
    }

    @Test
    void distancesAreWrittenRound() {
        assertEquals(32.0D, Reach.written(25.0D), 1.0E-9D);
        assertEquals(40.0D, Reach.written(30.0D), 1.0E-9D);
    }
}
