package com.elderlexicon.mod.spell.scene;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Behaviour of the world-level scene: many spells, many casters, any order, any timing. */
class WorldSceneTest {

    private static final Emission.Point ORIGIN = new Emission.Point(0, 0, 0);

    private static Emission beam(SpellScene scene, int spell, UUID caster, VitaElement element, double energy,
                                 long start, int life) {
        return Emission.beam(spell, caster, element.runeId(), ElementProperties.forElement(element), energy,
                ORIGIN, 1, 0, 0, 8.0D, 0.5D, start, life);
    }

    /** One spell per element, all aimed the same way, nine pulses each, added in the given order. */
    private static SpellScene streams(List<VitaElement> elements, double energyPerPulse) {
        SpellScene scene = new SpellScene();
        List<Integer> ids = new ArrayList<>();
        elements.forEach(e -> ids.add(scene.registerSpell()));
        for (int pulse = 0; pulse < 9; pulse++) {
            for (int i = 0; i < elements.size(); i++) {
                scene.add(beam(scene, ids.get(i), Emission.NO_CASTER, elements.get(i), energyPerPulse, pulse * 5L, 5));
            }
        }
        return scene;
    }

    private static List<Outcome> run(SpellScene scene, int ticks) {
        SceneLaws laws = new SceneLaws();
        List<Outcome> all = new ArrayList<>();
        for (long t = 0; t < ticks; t++) {
            all.addAll(laws.tick(scene, t));
        }
        return all;
    }

    private static Map<Outcome.Type, Integer> countByType(List<Outcome> outcomes) {
        Map<Outcome.Type, Integer> counts = new EnumMap<>(Outcome.Type.class);
        outcomes.forEach(o -> counts.merge(o.type(), 1, Integer::sum));
        return counts;
    }

    private static Map<String, Double> leftByElement(SpellScene scene) {
        Map<String, Double> left = new java.util.TreeMap<>();
        scene.emissions().forEach(e -> left.merge(e.elementRuneId(), e.remainingEnergy(), Double::sum));
        return left;
    }

    @Test
    void resultDoesNotDependOnTheOrderOfTheSpells() {
        VitaElement a = VitaElement.AQUA;
        VitaElement u = VitaElement.AURA;
        VitaElement i = VitaElement.IGNI;
        List<List<VitaElement>> orders = List.of(
                List.of(a, u, i), List.of(a, i, u), List.of(u, a, i), List.of(u, i, a), List.of(i, a, u), List.of(i, u, a));

        Map<Outcome.Type, Integer> expectedCounts = null;
        Map<String, Double> expectedLeft = null;
        for (List<VitaElement> order : orders) {
            SpellScene scene = streams(order, 10.0D / 9);
            Map<Outcome.Type, Integer> counts = countByType(run(scene, 45));
            Map<String, Double> left = leftByElement(scene);
            if (expectedCounts == null) {
                expectedCounts = counts;
                expectedLeft = left;
                continue;
            }
            assertEquals(expectedCounts, counts, "outcomes changed with the order " + order);
            for (String element : expectedLeft.keySet()) {
                assertEquals(expectedLeft.get(element), left.get(element), 1.0E-6, element + " leftovers changed with " + order);
            }
        }
    }

    @Test
    void manySpellsShareEnergyInsteadOfSpendingItTwice() {
        List<VitaElement> crowd = List.of(VitaElement.AQUA, VitaElement.AURA, VitaElement.IGNI, VitaElement.FIRMO,
                VitaElement.BALANCED, VitaElement.AQUA, VitaElement.AURA, VitaElement.IGNI);
        SpellScene scene = streams(crowd, 10.0D / 9);
        run(scene, 45);

        for (Emission emission : scene.emissions()) {
            assertTrue(emission.remainingEnergy() >= -1.0E-9, "negative energy in " + emission.elementRuneId());
            assertTrue(emission.remainingEnergy() <= emission.energy() + 1.0E-9);
        }
    }

    @Test
    void anInertElementStaysInertNoMatterHowManyJoin() {
        SpellScene scene = streams(List.of(VitaElement.FIRMO, VitaElement.FIRMO, VitaElement.AURA), 10.0D / 9);

        assertTrue(run(scene, 45).isEmpty());
    }

    @Test
    void casterWhoPutsMoreEnergyInIsBlamedMore() {
        UUID wind = UUID.randomUUID();
        UUID waterOne = UUID.randomUUID();
        UUID waterTwo = UUID.randomUUID();
        SpellScene scene = new SpellScene();
        int windSpell = scene.registerSpell();
        int waterSpellOne = scene.registerSpell();
        int waterSpellTwo = scene.registerSpell();
        for (int pulse = 0; pulse < 9; pulse++) {
            long start = pulse * 5L;
            scene.add(beam(scene, windSpell, wind, VitaElement.AURA, 3.0D, start, 5));
            scene.add(beam(scene, waterSpellOne, waterOne, VitaElement.AQUA, 3.0D, start, 5));
            scene.add(beam(scene, waterSpellTwo, waterTwo, VitaElement.AQUA, 3.0D, start, 5));
        }

        List<Outcome> discharges = run(scene, 45).stream().filter(o -> o.type() == Outcome.Type.DISCHARGE).toList();
        assertFalse(discharges.isEmpty(), "the three casters should have made lightning");

        double windTotal = 0;
        double waterTotal = 0;
        for (Outcome outcome : discharges) {
            windTotal += outcome.shares().getOrDefault(wind, 0.0D);
            waterTotal += outcome.shares().getOrDefault(waterOne, 0.0D) + outcome.shares().getOrDefault(waterTwo, 0.0D);
            double sum = outcome.shares().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(outcome.energy(), sum, 1.0E-6, "shares must add up to the energy of the outcome");
        }
        // The wind meets both waters, so it gives as much as the two of them together.
        assertEquals(waterTotal, windTotal, 0.05D * Math.max(windTotal, waterTotal));
    }

    @Test
    void aCasterIsTheOnlyOneBlamedForTheirOwnSpells() {
        UUID mage = UUID.randomUUID();
        SpellScene scene = new SpellScene();
        int water = scene.registerSpell();
        int wind = scene.registerSpell();
        for (int pulse = 0; pulse < 9; pulse++) {
            scene.add(beam(scene, water, mage, VitaElement.AQUA, 10.0D / 9, pulse * 5L, 5));
            scene.add(beam(scene, wind, mage, VitaElement.AURA, 10.0D / 9, pulse * 5L, 5));
        }

        Outcome discharge = run(scene, 45).stream().filter(o -> o.type() == Outcome.Type.DISCHARGE).findFirst().orElseThrow();
        assertEquals(1, discharge.shares().size());
        assertTrue(discharge.shares().containsKey(mage));
    }

    @Test
    void spellsCastAtDifferentMomentsStillInteractWhileTheirEmissionsCoexist() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SpellScene scene = new SpellScene();
        // Water starts at tick 0, wind at tick 10; both last long enough to overlap in time.
        scene.add(beam(scene, scene.registerSpell(), first, VitaElement.AQUA, 10.0D, 0, 30));
        scene.add(beam(scene, scene.registerSpell(), second, VitaElement.AURA, 10.0D, 10, 30));

        SceneLaws laws = new SceneLaws();
        long firstContact = -1;
        for (long t = 0; t < 40 && firstContact < 0; t++) {
            if (!laws.tick(scene, t).isEmpty()) {
                firstContact = t;
            }
        }
        assertTrue(firstContact >= 10, "no interaction can happen before both exist, was " + firstContact);
    }

    @Test
    void emissionsInDifferentPartsOfTheWorldNeverMeet() {
        SpellScene scene = new SpellScene();
        int water = scene.registerSpell();
        int wind = scene.registerSpell();
        scene.add(Emission.beam(water, "aqua", ElementProperties.forElement(VitaElement.AQUA), 10, ORIGIN, 1, 0, 0, 8.0D, 0.5D, 0, 20));
        scene.add(Emission.beam(wind, "aura", ElementProperties.forElement(VitaElement.AURA), 10,
                new Emission.Point(500, 64, -500), 1, 0, 0, 8.0D, 0.5D, 0, 20));

        assertTrue(scene.overlapsAt(5).isEmpty());
    }

    @Test
    void emissionsAcrossAGridCellBoundaryAreStillFound() {
        SpellScene scene = new SpellScene();
        // The cell edge is at x = 8. One point sits just before it and the other just after.
        scene.add(Emission.point(scene.registerSpell(), "aqua", ElementProperties.forElement(VitaElement.AQUA), 10,
                new Emission.Point(7.9, 0, 0), 1.0D, 0, 20));
        scene.add(Emission.point(scene.registerSpell(), "aura", ElementProperties.forElement(VitaElement.AURA), 10,
                new Emission.Point(8.3, 0, 0), 1.0D, 0, 20));

        assertEquals(1, scene.overlapsAt(5).size());
    }

    @Test
    void aBusyWorldStaysCheapBecauseOnlyNeighboursAreCompared() {
        SpellScene scene = new SpellScene();
        int spell = 0;
        // 400 emissions scattered so that only a handful are near each other.
        for (int i = 0; i < 400; i++) {
            spell = scene.registerSpell();
            scene.add(Emission.beam(spell, "aqua", ElementProperties.forElement(VitaElement.AQUA), 1,
                    new Emission.Point(i * 40.0D, 64, (i % 7) * 40.0D), 1, 0, 0, 8.0D, 0.5D, 0, 20));
        }

        long started = System.nanoTime();
        for (int tick = 0; tick < 20; tick++) {
            scene.overlapsAt(tick);
        }
        double millis = (System.nanoTime() - started) / 1.0e6;

        assertTrue(scene.overlapsAt(5).isEmpty());
        assertTrue(millis < 1500.0D, "20 ticks of a busy world took " + millis + " ms");
    }

    @Test
    void expiredEmissionsAreDroppedSoTheSceneEmptiesItself() {
        SpellScene scene = new SpellScene();
        scene.add(beam(scene, scene.registerSpell(), Emission.NO_CASTER, VitaElement.AQUA, 5, 0, 5));
        scene.add(beam(scene, scene.registerSpell(), Emission.NO_CASTER, VitaElement.AURA, 5, 0, 50));

        assertEquals(0, scene.prune(4));
        assertEquals(1, scene.prune(5));
        assertFalse(scene.isEmpty());
        assertEquals(1, scene.prune(50));
        assertTrue(scene.isEmpty());
    }
}
