package com.elderlexicon.mod.spell.scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The physics of a {@link SpellScene}. The laws only look at {@link ElementProperties}, never at
 * rune names, and only move energy out of the emissions, so their outcomes are bounded by what
 * the casters paid.
 * <ul>
 *   <li><b>Friction</b>: matter moving at different speeds through moisture builds up charge;
 *       enough charge discharges as lightning.</li>
 *   <li><b>Thermal</b>: heat meeting moisture makes steam; enough steam pressure bursts.</li>
 * </ul>
 * One instance follows one cast, because charge and pressure carry over between ticks.
 */
public final class SceneLaws {

    /** Share of the weaker emission's energy that a fully coupled pair converts each tick. */
    static final double CONVERSION = 0.5D;
    /** UMU of stored charge that triggers a discharge. */
    static final double DISCHARGE_THRESHOLD = 4.0D;
    /** UMU of stored steam that triggers a burst. */
    static final double BURST_THRESHOLD = 6.0D;
    private static final double MIN_ENERGY = 1.0E-3D;

    private final Map<Long, Double> charge = new HashMap<>();
    private final Map<Long, Double> pressure = new HashMap<>();

    public List<Outcome> tick(SpellScene scene, long tick) {
        List<Outcome> outcomes = new ArrayList<>();
        for (SpellScene.Overlap overlap : scene.overlapsAt(tick)) {
            Emission a = overlap.first();
            Emission b = overlap.second();
            if (a.remainingEnergy() < MIN_ENERGY || b.remainingEnergy() < MIN_ENERGY) {
                continue;
            }
            Emission.Point contact = a.contactPoint(b).orElse(null);
            if (contact == null) {
                continue;
            }
            long key = pairKey(a.spellId(), b.spellId());
            applyFriction(a, b, key, contact, outcomes);
            applyThermal(a, b, key, contact, outcomes);
        }
        return outcomes;
    }

    private void applyFriction(Emission a, Emission b, long key, Emission.Point contact, List<Outcome> outcomes) {
        ElementProperties pa = a.properties();
        ElementProperties pb = b.properties();
        double coupling = clamp01(Math.abs(pa.momentum() - pb.momentum()) * (pa.moisture() + pb.moisture()));
        double taken = convert(a, b, coupling);
        if (taken <= 0.0D) {
            return;
        }
        double stored = charge.merge(key, taken, Double::sum);
        if (stored >= DISCHARGE_THRESHOLD) {
            charge.put(key, 0.0D);
            outcomes.add(new Outcome(Outcome.Type.DISCHARGE, contact, stored));
        }
    }

    private void applyThermal(Emission a, Emission b, long key, Emission.Point contact, List<Outcome> outcomes) {
        ElementProperties pa = a.properties();
        ElementProperties pb = b.properties();
        double coupling = clamp01(pa.heat() * pb.moisture() + pb.heat() * pa.moisture());
        double taken = convert(a, b, coupling);
        if (taken <= 0.0D) {
            return;
        }
        outcomes.add(new Outcome(Outcome.Type.STEAM, contact, taken));
        double stored = pressure.merge(key, taken, Double::sum);
        if (stored >= BURST_THRESHOLD) {
            pressure.put(key, 0.0D);
            outcomes.add(new Outcome(Outcome.Type.STEAM_BURST, contact, stored));
        }
    }

    /** Takes the same share of energy out of both emissions and returns the total taken. */
    private static double convert(Emission a, Emission b, double coupling) {
        if (coupling <= 0.0D) {
            return 0.0D;
        }
        double amount = CONVERSION * coupling * Math.min(a.remainingEnergy(), b.remainingEnergy());
        return a.consume(amount) + b.consume(amount);
    }

    private static long pairKey(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return ((long) low << 32) | (high & 0xFFFFFFFFL);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
