package com.elderlexicon.mod.spell.scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The physics of a {@link SpellScene}. The laws only look at {@link ElementProperties}, never at
 * rune names or casters, and only move energy out of the emissions, so their outcomes are bounded
 * by what the casters paid.
 * <ul>
 *   <li><b>Friction</b>: matter moving at different speeds through moisture builds up charge;
 *       enough charge discharges as lightning.</li>
 *   <li><b>Thermal</b>: heat meeting moisture makes steam; enough steam pressure bursts.</li>
 * </ul>
 * Every tick works from a snapshot: first each touching pair says how much energy it would like to
 * convert, then an emission that is asked for more than it has gives each partner a proportional
 * part. That makes the result independent of the order the spells were written or added in, and lets
 * any number of spells share the same emission instead of the first pair taking all of it.
 * <p>
 * One instance follows one world, because charge and pressure carry over between ticks.
 */
public final class SceneLaws {

    /** Share of the weaker emission's energy that a fully coupled pair converts each tick. */
    static final double CONVERSION = 0.5D;
    /** UMU of stored charge that triggers a discharge. */
    static final double DISCHARGE_THRESHOLD = 4.0D;
    /** UMU of stored steam that triggers a burst. */
    static final double BURST_THRESHOLD = 6.0D;
    private static final double MIN_ENERGY = 0.05D;
    /** Steam puffs smaller than this are stored as pressure but not shown. */
    private static final double MIN_VISIBLE_STEAM = 0.05D;

    private enum Law { FRICTION, THERMAL }

    private record Demand(SpellScene.Overlap overlap, Emission.Extent contact, Law law, double amount) { }

    /** Energy stored by a law for one pair of spells, and how much of it each caster paid. */
    private static final class Store {
        private double amount;
        private final Map<UUID, Double> shares = new HashMap<>();

        void add(double taken, Emission a, double takenA, Emission b, double takenB) {
            amount += taken;
            shares.merge(a.caster(), takenA, Double::sum);
            shares.merge(b.caster(), takenB, Double::sum);
        }

        void clear() {
            amount = 0.0D;
            shares.clear();
        }
    }

    private final Map<Long, Store> charge = new HashMap<>();
    private final Map<Long, Store> pressure = new HashMap<>();

    /** Forgets everything stored; called when the scene has gone quiet. */
    public void reset() {
        charge.clear();
        pressure.clear();
    }

    public List<Outcome> tick(SpellScene scene, long tick) {
        List<SpellScene.Overlap> overlaps = scene.overlapsAt(tick);
        if (overlaps.isEmpty()) {
            return List.of();
        }

        // 1. What every touching pair would like to convert, from the energies as they are now.
        List<Demand> demands = new ArrayList<>();
        Map<Emission, Double> asked = new IdentityHashMap<>();
        Map<Emission, Double> available = new IdentityHashMap<>();
        for (SpellScene.Overlap overlap : overlaps) {
            Emission a = overlap.first();
            Emission b = overlap.second();
            if (a.remainingEnergy() < MIN_ENERGY || b.remainingEnergy() < MIN_ENERGY) {
                continue;
            }
            Emission.Extent contact = a.overlapExtent(b).orElse(null);
            if (contact == null) {
                continue;
            }
            for (Law law : Law.values()) {
                double coupling = coupling(law, a.properties(), b.properties());
                if (coupling <= 0.0D) {
                    continue;
                }
                double amount = CONVERSION * coupling * Math.min(a.remainingEnergy(), b.remainingEnergy());
                demands.add(new Demand(overlap, contact, law, amount));
                asked.merge(a, amount, Double::sum);
                asked.merge(b, amount, Double::sum);
                available.put(a, a.remainingEnergy());
                available.put(b, b.remainingEnergy());
            }
        }

        // 2. An emission asked for more than it has gives every partner the same fraction.
        Map<Emission, Double> scale = new IdentityHashMap<>();
        asked.forEach((emission, total) ->
                scale.put(emission, Math.min(1.0D, available.get(emission) / total)));

        // 3. Convert, keep what was stored and release it when it passes the threshold.
        List<Outcome> outcomes = new ArrayList<>();
        for (Demand demand : demands) {
            Emission a = demand.overlap().first();
            Emission b = demand.overlap().second();
            double give = demand.amount() * Math.min(scale.get(a), scale.get(b));
            double takenA = a.consume(give);
            double takenB = b.consume(give);
            double taken = takenA + takenB;
            if (taken <= 0.0D) {
                continue;
            }
            long key = pairKey(a.spellId(), b.spellId());
            if (demand.law() == Law.FRICTION) {
                Store store = charge.computeIfAbsent(key, k -> new Store());
                store.add(taken, a, takenA, b, takenB);
                if (store.amount >= DISCHARGE_THRESHOLD) {
                    outcomes.add(release(Outcome.Type.DISCHARGE, demand.contact(), store));
                }
            } else {
                Store store = pressure.computeIfAbsent(key, k -> new Store());
                store.add(taken, a, takenA, b, takenB);
                if (taken >= MIN_VISIBLE_STEAM) {
                    outcomes.add(new Outcome(Outcome.Type.STEAM, demand.contact().from(), demand.contact().to(), taken,
                            sharesOf(a, takenA, b, takenB)));
                }
                if (store.amount >= BURST_THRESHOLD) {
                    outcomes.add(release(Outcome.Type.STEAM_BURST, demand.contact(), store));
                }
            }
        }
        return outcomes;
    }

    private static Map<UUID, Double> sharesOf(Emission a, double takenA, Emission b, double takenB) {
        Map<UUID, Double> shares = new HashMap<>();
        shares.merge(a.caster(), takenA, Double::sum);
        shares.merge(b.caster(), takenB, Double::sum);
        return shares;
    }

    private static Outcome release(Outcome.Type type, Emission.Extent contact, Store store) {
        Outcome outcome = new Outcome(type, contact.from(), contact.to(), store.amount, store.shares);
        store.clear();
        return outcome;
    }

    private static double coupling(Law law, ElementProperties a, ElementProperties b) {
        return switch (law) {
            case FRICTION -> clamp01(Math.abs(a.momentum() - b.momentum()) * (a.moisture() + b.moisture()));
            case THERMAL -> clamp01(a.heat() * b.moisture() + b.heat() * a.moisture());
        };
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
