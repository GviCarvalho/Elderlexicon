package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A caster keeps itself whole too (docs/feiticeiro-design.md, 3.8): it heals out of the world, keeps foes off, gets
 * away, puts out the fire on it; and it gives up on what did nothing.
 */
class KeepingWholeTest {

    private static Option option(String... words) {
        return Option.of(PhraseCatalog.imagine(List.of(words)).orElseThrow());
    }

    private static Mind.Scene scene(double distance, float share, boolean burning) {
        return new Mind.Scene(null, null, distance, share, 1000.0D, mark -> 0.0D, burning, element -> true);
    }

    @Test
    void drawingInAtNoBodyHeals() {
        Act draw = option("aqua", "quantum", "-10", "vocant").acts().get(0);
        assertEquals(Intent.DRAW, Mind.serves(draw), "aimed at a body it draws out of it");
        assertEquals(Intent.HEAL, Mind.serves(draw, Aim.SKY), "aimed at the sky it draws out of the world");
    }

    @Test
    void theCureFillsEveryEnergy() {
        Mind.Foresight hurt = Mind.person(0.3D);
        Option water = option("aqua", "quantum", "-10", "vocant");
        Optional<Option> cure = Option.page("cura", List.of(
                new Option.Line(List.of("aqua", "quantum", "-20", "vocant"), 0),
                new Option.Line(List.of("aura", "quantum", "-20", "vocant"), 1),
                new Option.Line(List.of("igni", "quantum", "-10", "vocant"), 2),
                new Option.Line(List.of("firmo", "quantum", "-10", "vocant"), 3)));
        assertTrue(cure.isPresent());
        Mind.Scene wounded = scene(Double.POSITIVE_INFINITY, 0.5F, false);
        assertTrue(Mind.evaluate(wounded, Personality.CAUTIOUS, water, hurt).isEmpty(),
                "water alone gives nothing back when every energy lacks");
        Optional<Mind.Choice> healing = Mind.evaluate(wounded, Personality.CAUTIOUS, cure.get(), hurt);
        assertTrue(healing.isPresent());
        assertEquals(Intent.HEAL, healing.get().intent());
        assertEquals(Aim.SKY, healing.get().aim());
        assertTrue(healing.get().healed() > 0.0D);
    }

    @Test
    void aZoneAroundItKeepsAFoeOff() {
        Option ward = option("vis", "impediunt");
        assertEquals(Intent.WARD, Mind.serves(ward.acts().get(0)));
        Mind.Foresight person = Mind.person(0.0D);
        assertTrue(Mind.evaluate(scene(2.0D, 0.6F, false), Personality.CAUTIOUS, ward, person).isPresent());
        assertTrue(Mind.evaluate(scene(10.0D, 0.6F, false), Personality.CAUTIOUS, ward, person).isEmpty(),
                "a foe far off is no reason to ward");
    }

    @Test
    void throwingItselfAwayGetsAway() {
        Option flee = option("ego", "iactare");
        Act act = flee.acts().get(0);
        assertEquals(Intent.FLEE, Mind.serves(act, Aim.AWAY));
        assertFalse(Mind.turnsOnSelf(act));
        Optional<Mind.Choice> choice = Mind.evaluate(scene(2.0D, 0.3F, false), Personality.CAUTIOUS, flee,
                Mind.person(0.0D));
        assertTrue(choice.isPresent());
        assertEquals(Aim.AWAY, choice.get().aim());
    }

    @Test
    void waterAtItsFeetPutsOutTheFire() {
        Option douse = option("aqua", "vocant");
        assertEquals(Intent.DOUSE, Mind.serves(douse.acts().get(0), Aim.FEET));
        assertTrue(Mind.evaluate(scene(Double.POSITIVE_INFINITY, 0.8F, true), Personality.AGGRESSIVE, douse,
                Mind.person(0.0D)).isPresent());
        assertTrue(Mind.evaluate(scene(Double.POSITIVE_INFINITY, 0.8F, false), Personality.AGGRESSIVE, douse,
                Mind.person(0.0D)).isEmpty(), "nothing burns: nothing to put out");
    }

    @Test
    void whatDidNothingTwiceIsGivenUp() {
        Memory memory = new Memory();
        UUID foe = UUID.randomUUID();
        Option fire = option("igni", "iactare");
        memory.outcome(foe, fire, 4.0D, 0.0D, 15.0D);
        assertTrue(memory.factor(foe, fire) <= 0.3D, "once: worth much less");
        memory.outcome(foe, fire, 4.0D, 0.0D, 15.0D);
        assertEquals(0.0D, memory.factor(foe, fire), "twice: not again against this one");
        assertEquals(15.0D, memory.reachOf(fire), 1.0E-9D);
        assertEquals(0.0D, memory.factor(UUID.randomUUID(), fire, 16.0D), "nor from as far, against anyone");
        assertEquals(1.0D, memory.factor(UUID.randomUUID(), fire, 8.0D), 1.0E-9D);
    }

    @Test
    void whatWorkedClearsTheFailure() {
        Memory memory = new Memory();
        UUID foe = UUID.randomUUID();
        Option fire = option("igni", "iactare");
        memory.outcome(foe, fire, 4.0D, 0.0D);
        memory.outcome(foe, fire, 4.0D, 4.0D);
        assertFalse(memory.failed(foe, fire));
        assertTrue(memory.factor(foe, fire) > 0.3D);
    }

    @Test
    void preparationWritesPagesToKeepItselfWhole() {
        Preparation preparation = new Preparation(PhraseCatalog.get(),
                java.util.Set.of("aqua", "aura", "vis", "iactare", "vocant", "impediunt", "quantum", "ego"),
                Personality.CAUTIOUS).finish();
        List<String> names = preparation.pages().stream().map(Option::name).toList();
        assertTrue(names.stream().anyMatch(name -> name.startsWith("cura")), names.toString());
        assertTrue(names.contains("escudo"), names.toString());
        assertTrue(names.contains("fuga"), names.toString());
        assertTrue(names.contains("apagar o fogo"), names.toString());
        assertTrue(names.stream().anyMatch(name -> name.startsWith("golpe") || name.startsWith("salva")),
                names.toString());
        RandomSource.create(1L);
    }
}
