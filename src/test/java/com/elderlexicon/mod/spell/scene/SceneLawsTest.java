package com.elderlexicon.mod.spell.scene;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneLawsTest {

    private static final Emission.Point ORIGIN = new Emission.Point(0, 0, 0);

    /** Two casts of {@code pulses} pulses each, one per element, aimed the same way. */
    private static SpellScene twoStreams(VitaElement first, VitaElement second, int pulses, double energyPerPulse) {
        SpellScene scene = new SpellScene();
        int a = scene.registerSpell();
        int b = scene.registerSpell();
        for (int i = 0; i < pulses; i++) {
            long start = i * 5L;
            scene.add(Emission.beam(a, first.runeId(), ElementProperties.forElement(first), energyPerPulse,
                    ORIGIN, 1, 0, 0, 8.0D, 0.5D, start, 5));
            scene.add(Emission.beam(b, second.runeId(), ElementProperties.forElement(second), energyPerPulse,
                    ORIGIN, 1, 0, 0, 8.0D, 0.5D, start, 5));
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

    private static boolean has(List<Outcome> outcomes, Outcome.Type type) {
        return outcomes.stream().anyMatch(outcome -> outcome.type() == type);
    }

    @Test
    void waterAndWindBuildChargeAndDischarge() {
        List<Outcome> outcomes = run(twoStreams(VitaElement.AQUA, VitaElement.AURA, 9, 10.0D / 9), 45);

        assertTrue(has(outcomes, Outcome.Type.DISCHARGE));
    }

    @Test
    void fireAndWaterMakeSteam() {
        List<Outcome> outcomes = run(twoStreams(VitaElement.IGNI, VitaElement.AQUA, 9, 10.0D / 9), 45);

        assertTrue(has(outcomes, Outcome.Type.STEAM));
    }

    @Test
    void enoughSteamBursts() {
        List<Outcome> outcomes = run(twoStreams(VitaElement.IGNI, VitaElement.AQUA, 9, 5.0D), 45);

        assertTrue(has(outcomes, Outcome.Type.STEAM_BURST));
    }

    @Test
    void combinationsWithoutMoistureDoNothing() {
        assertTrue(run(twoStreams(VitaElement.IGNI, VitaElement.AURA, 9, 10.0D / 9), 45).isEmpty());
        assertTrue(run(twoStreams(VitaElement.FIRMO, VitaElement.AURA, 9, 10.0D / 9), 45).isEmpty());
        assertTrue(run(twoStreams(VitaElement.IGNI, VitaElement.FIRMO, 9, 10.0D / 9), 45).isEmpty());
    }

    @Test
    void sameElementDoesNotInteract() {
        assertTrue(run(twoStreams(VitaElement.AQUA, VitaElement.AQUA, 9, 10.0D / 9), 45).isEmpty());
    }

    @Test
    void lawsNeverConsumeMoreThanWasPaid() {
        for (VitaElement other : new VitaElement[]{VitaElement.AURA, VitaElement.IGNI, VitaElement.BALANCED}) {
            SpellScene scene = twoStreams(VitaElement.AQUA, other, 9, 5.0D);
            double paid = scene.emissions().stream().mapToDouble(Emission::energy).sum();
            run(scene, 45);
            double left = scene.emissions().stream().mapToDouble(Emission::remainingEnergy).sum();

            assertTrue(left >= -1.0E-9, "negative energy with " + other);
            assertTrue(paid - left <= paid + 1.0E-9, "consumed more than paid with " + other);
            assertTrue(scene.emissions().stream().allMatch(e -> e.remainingEnergy() >= -1.0E-9));
        }
    }

    @Test
    void emissionsThatNeverTouchProduceNothing() {
        SpellScene scene = new SpellScene();
        scene.add(Emission.beam(scene.registerSpell(), "aqua", ElementProperties.forElement(VitaElement.AQUA), 10,
                ORIGIN, 1, 0, 0, 8.0D, 0.5D, 0, 5));
        scene.add(Emission.beam(scene.registerSpell(), "aura", ElementProperties.forElement(VitaElement.AURA), 10,
                new Emission.Point(0, 0, 20), 0, 0, 1, 8.0D, 0.5D, 0, 5));

        assertTrue(run(scene, 10).isEmpty());
    }

    @Test
    void dischargeHappensAtTheContactPointAndIsWorthItsThreshold() {
        List<Outcome> outcomes = run(twoStreams(VitaElement.AQUA, VitaElement.AURA, 9, 10.0D / 9), 45);

        Outcome discharge = outcomes.stream().filter(o -> o.type() == Outcome.Type.DISCHARGE).findFirst().orElseThrow();
        assertEquals(0.0D, discharge.at().y(), 1.0E-9);
        assertTrue(discharge.energy() >= 4.0D);
    }
}
