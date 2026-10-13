package com.elderlexicon.mod.mind;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A caster's short memory of the ones it fights (docs/feiticeiro-design.md, 3.5): for each, what each option did against
 * them next to what it foresaw, and how often they broke its trance. It is kept for a life, not saved: a new fight with
 * a new evoker starts fresh.
 */
public final class Memory {

    /** How many opponents it remembers at once: the oldest is forgotten first. */
    private static final int OPPONENTS = 4;
    /** How much a new outcome weighs against what it remembered (a running mean). */
    private static final double FRESH = 0.5D;
    /** How far what it learned moves an option's worth, at most. */
    private static final double LEAST = 0.4D;
    private static final double MOST = 1.8D;
    /** After this many broken trances by the same one, it opens its trance only from afar. */
    private static final int BREAKS_TO_LEARN = 2;
    /** How far "afar" is. */
    public static final double SAFE_DISTANCE = 7.0D;

    private final Map<UUID, Opponent> opponents = new LinkedHashMap<>(OPPONENTS, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Opponent> eldest) {
            return size() > OPPONENTS;
        }
    };

    /** Below this much harm done (points of health), a reading did nothing. */
    private static final double NOTHING = 0.25D;
    /** After one reading that did nothing, an option is worth this much less; after two, nothing against that one. */
    private static final double ONCE_FAILED = 0.3D;
    private static final int FAILURES_TO_GIVE_UP = 2;

    /** What it remembers of one opponent. */
    private static final class Opponent {
        final Map<String, Double> factors = new HashMap<>();
        final Map<String, Integer> failures = new HashMap<>();
        int breaks;
    }

    /**
     * How far each option got, as far as it learned: the nearest it was read from and did nothing to anyone. Kept for
     * any opponent: what does not get there, does not get there.
     */
    private final Map<String, Double> fellShort = new HashMap<>();

    private Opponent of(UUID opponent) {
        return opponents.computeIfAbsent(opponent, id -> new Opponent());
    }

    /**
     * What it learned of {@code option} against {@code opponent}: 1 when nothing; more when it did better than foreseen,
     * less when it did worse (a throw that never lands, a body that has nothing to draw).
     */
    public double factor(UUID opponent, Option option) {
        Opponent known = opponents.get(opponent);
        if (known == null) {
            return 1.0D;
        }
        int failed = known.failures.getOrDefault(option.name(), 0);
        if (failed >= FAILURES_TO_GIVE_UP) {
            return 0.0D; // it did nothing twice: it gives up on it, against this one
        }
        double factor = known.factors.getOrDefault(option.name(), 1.0D);
        return failed > 0 ? Math.min(factor, ONCE_FAILED) : factor;
    }

    /**
     * What it learned of {@code option} against {@code opponent} {@code distance} away: as {@link #factor}, and nothing
     * when it learned it does not get that far.
     */
    public double factor(UUID opponent, Option option, double distance) {
        double reach = reachOf(option);
        return reach < Double.POSITIVE_INFINITY && distance + 0.5D >= reach ? 0.0D : factor(opponent, option);
    }

    /** How far {@code option} gets, as far as it learned (unbounded until it fell short). */
    public double reachOf(Option option) {
        return fellShort.getOrDefault(option.name(), Double.POSITIVE_INFINITY);
    }

    /** {@code option} was read against {@code opponent}: it foresaw {@code foreseen} harm, and {@code done} came of it. */
    public void outcome(UUID opponent, Option option, double foreseen, double done) {
        double ratio = clamp((Math.max(0.0D, done) + 1.0D) / (Math.max(0.0D, foreseen) + 1.0D));
        Opponent known = of(opponent);
        known.factors.merge(option.name(), ratio, (old, fresh) -> clamp(old * (1.0D - FRESH) + fresh * FRESH));
        if (foreseen > 0.0D && done < NOTHING) {
            known.failures.merge(option.name(), 1, Integer::sum);
        } else if (done >= NOTHING) {
            known.failures.remove(option.name());
        }
    }

    /**
     * {@code option} was read against {@code opponent} {@code distance} away, foreseeing {@code foreseen} harm, and
     * {@code done} came of it: as {@link #outcome(UUID, Option, double, double)}, and when it did nothing, it learns it
     * may not get that far (twice from as far or farther, and it is sure).
     */
    public void outcome(UUID opponent, Option option, double foreseen, double done, double distance) {
        outcome(opponent, option, foreseen, done);
        if (foreseen > 0.0D && done < NOTHING && opponents.get(opponent).failures.getOrDefault(option.name(), 0)
                >= FAILURES_TO_GIVE_UP) {
            fellShort.merge(option.name(), distance, Math::min);
        }
    }

    /** Whether {@code option} did nothing the last time against {@code opponent}. */
    public boolean failed(UUID opponent, Option option) {
        Opponent known = opponents.get(opponent);
        return known != null && known.failures.getOrDefault(option.name(), 0) > 0;
    }

    /** {@code opponent} broke its trance. */
    public void broken(UUID opponent) {
        of(opponent).breaks++;
    }

    /** How far {@code opponent} must be for it to open its trance: anywhere, until they broke it often enough. */
    public double safeDistance(UUID opponent) {
        Opponent known = opponents.get(opponent);
        return known != null && known.breaks >= BREAKS_TO_LEARN ? SAFE_DISTANCE : 0.0D;
    }

    /** The options it learned did better than foreseen against {@code opponent}: what it varies. */
    public boolean worked(UUID opponent, Option option) {
        return factor(opponent, option) > 1.15D;
    }

    private static double clamp(double value) {
        return Math.max(LEAST, Math.min(MOST, value));
    }
}
