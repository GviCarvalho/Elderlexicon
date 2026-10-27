package com.elderlexicon.arena;

import com.elderlexicon.mod.mind.Degree;
import com.elderlexicon.mod.mind.Doctrine;
import com.elderlexicon.mod.mind.Doctrine.Situation;
import com.elderlexicon.mod.mind.Doctrine.Stance;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Threat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** The ways a caster in the arena chooses: as it did before, as it does now with no doctrine, and by what it learns. */
final class Policies {

    private Policies() {
    }

    /** As every caster did before 3.9: it reads whenever it can, the best it sees, and walks up when out of reach. */
    static Fight.Policy before() {
        return (fight, seen, possible) -> {
            Optional<Mind.Choice> best = fight.bestOfAll();
            if (best.isPresent() && possible.contains(stanceOf(best.get()))) {
                return stanceOf(best.get());
            }
            return seen.distance() > 16.0D ? Stance.CLOSE_IN : Stance.WAIT;
        };
    }

    /**
     * An elder with no doctrine (3.9): it opens its trance only when the blow cannot come before it ends, 1.25 times
     * over, or when cornered; else it backs away to where it wants the foe; else it walks up.
     */
    static Fight.Policy elder() {
        return (fight, seen, possible) -> {
            // It keeps off only one that comes to strike: one with a bow, or one that reads, follows it anyway.
            double wanted = fight.cal.bow() != null || fight.cal.reads() ? 0.0D : Threat.safeDistance(Math.max(Threat.BLOW,
                    fight.cal.foeReach()), Math.max(fight.cal.guessed(), fight.cal.foeSpeed()), Fight.TRANCE,
                    Degree.ELDER.foresight());
            boolean safe = fight.cal.bow() != null || fight.cal.reads()
                    || seen.trancesToBlow() >= Degree.ELDER.foresight();
            Optional<Mind.Choice> best = fight.bestOfAll();
            if ((safe || fight.cornered > 0) && best.isPresent() && possible.contains(stanceOf(best.get()))) {
                return stanceOf(best.get());
            }
            if (seen.distance() < wanted && fight.cornered == 0) {
                return Stance.BACK_OFF;
            }
            return seen.distance() > 16.0D ? Stance.CLOSE_IN : Stance.WAIT;
        };
    }

    static Stance stanceOf(Mind.Choice choice) {
        for (Stance stance : Stance.values()) {
            if (stance.intent() == choice.intent()) {
                return stance;
            }
        }
        return Stance.WAIT;
    }

    /** What a doctrine says, and what an elder with none would do where it never was. */
    static Fight.Policy following(Doctrine doctrine) {
        Fight.Policy fallback = elder();
        return (fight, seen, possible) -> doctrine.choose(seen, possible)
                .orElseGet(() -> fallback.choose(fight, seen, possible));
    }

    /**
     * Learning by trying (Q-learning, a table): what each stance is worth in each situation, moved a little toward what
     * came of it and what the next situation is worth. It tries something else now and then ({@code explore}).
     */
    static final class Learning implements Fight.Policy {

        /** How much one fight moves what it learned: less the more it met that situation and tried that stance. */
        private static final double LEAST_RATE = 0.01D;
        private static final double LATER = 0.97D;

        final Doctrine.Steps steps = Doctrine.Steps.standard();
        final Map<String, double[]> worth = new HashMap<>();
        final Map<String, Integer> met = new HashMap<>();
        private final Map<String, int[]> tried = new HashMap<>();
        double explore;
        private final Random random;
        private String lastKey;
        private Stance lastStance;

        Learning(Random random) {
            this.random = random;
        }

        private double[] row(String key) {
            return worth.computeIfAbsent(key, k -> new double[Stance.values().length]);
        }

        @Override
        public Stance choose(Fight fight, Situation seen, Set<Stance> possible) {
            String key = steps.key(seen);
            met.merge(key, 1, Integer::sum);
            Stance stance;
            if (random.nextDouble() < explore) {
                Stance[] options = possible.toArray(new Stance[0]);
                stance = options[random.nextInt(options.length)];
            } else {
                stance = best(row(key), possible);
            }
            lastKey = key;
            lastStance = stance;
            return stance;
        }

        @Override
        public void reward(Fight fight, double reward, Situation seen, Set<Stance> possible, boolean over) {
            if (lastKey == null) {
                return;
            }
            double[] values = row(lastKey);
            int[] times = tried.computeIfAbsent(lastKey, k -> new int[Stance.values().length]);
            double rate = Math.max(LEAST_RATE, 1.0D / Math.sqrt(++times[lastStance.ordinal()]));
            double next = over ? 0.0D : row(steps.key(seen))[best(row(steps.key(seen)), possible).ordinal()];
            values[lastStance.ordinal()] += rate * (reward + LATER * next - values[lastStance.ordinal()]);
            if (over) {
                lastKey = null;
            }
        }

        private static Stance best(double[] values, Set<Stance> possible) {
            Stance best = null;
            for (Stance stance : possible) {
                if (best == null || values[stance.ordinal()] > values[best.ordinal()]) {
                    best = stance;
                }
            }
            return best;
        }
    }
}
