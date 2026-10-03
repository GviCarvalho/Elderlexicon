package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The core of a thing (docs/particulas-design.md, section 6, "Transformar: o cerne"): the share of each primordial in
 * it, a hundred parts in all. What a thing is comes from its core, so changing the core changes what it is. The core is
 * a proportion, not an amount: changing it converts the thing's own particles from one primordial into another, as many
 * in all as before, and nothing has to come from outside (user, 01/10/2026). {@code aqua quantum 16 vertere m1} makes
 * water sixteen parts of the hundred of m1.
 */
public final class Core {

    /** The parts a core is divided into: a quantum on a core counts in them. */
    public static final double WHOLE = 100.0D;
    private static final double EPSILON = 1.0E-9D;
    /** How near a hundred the parts written must come to be a hundred. */
    private static final double EXACT = 1.0E-6D;

    private Core() {
    }

    /** The core asked for with every part given a number ({@link #reshape(Composition, Map, Set)}). */
    public static Optional<Composition> reshape(Composition core, Map<VitaElement, Double> asked) {
        return reshape(core, asked, Set.of());
    }

    /**
     * The core asked for, when the parts asked can make one (user, 01/10/2026).
     * <ul>
     *   <li>Each primordial written with a number takes exactly that many parts of the hundred.</li>
     *   <li>The ones written with none ({@code aqua vertere m1}) share what the numbers leave, evenly: one alone takes
     *       all of it, two take half each, and then the ones not written at all keep nothing.</li>
     *   <li>When every one written has a number, the ones not written share what is left in the proportion they had, so
     *       their mix stays as it was, or evenly when they held nothing.</li>
     * </ul>
     * A core is a hundred parts: numbers past a hundred, all four numbered short of it, or a part below nothing make
     * none, and the thing is left as it is. Nothing asked leaves it as it is too.
     *
     * @param asked the primordials written with a number, and how many parts
     * @param even  the primordials written with none
     */
    public static Optional<Composition> reshape(Composition core, Map<VitaElement, Double> asked,
                                                Set<VitaElement> even) {
        EnumMap<VitaElement, Double> parts = new EnumMap<>(VitaElement.class);
        double fixed = 0.0D;
        List<VitaElement> free = new ArrayList<>();
        List<VitaElement> sharing = new ArrayList<>();
        for (VitaElement aspect : Particles.ASPECTS) {
            Double written = asked.get(aspect);
            if (written != null) {
                if (written < 0.0D) {
                    return Optional.empty();
                }
                parts.put(aspect, written / WHOLE);
                fixed += written / WHOLE;
            } else if (even.contains(aspect)) {
                sharing.add(aspect);
            } else {
                free.add(aspect);
            }
        }
        if (parts.isEmpty() && sharing.isEmpty()) {
            return Optional.of(core);
        }
        if (fixed > 1.0D + EXACT || free.isEmpty() && sharing.isEmpty() && fixed < 1.0D - EXACT) {
            return Optional.empty();
        }
        double rest = Math.max(0.0D, 1.0D - fixed);
        if (!sharing.isEmpty()) {
            for (VitaElement aspect : sharing) {
                parts.put(aspect, rest / sharing.size());
            }
        } else if (rest > EXACT) {
            double held = 0.0D;
            for (VitaElement aspect : free) {
                held += core.share(aspect);
            }
            for (VitaElement aspect : free) {
                parts.put(aspect, held > EPSILON ? core.share(aspect) / held * rest : rest / free.size());
            }
        }
        if (parts.values().stream().mapToDouble(Double::doubleValue).sum() <= EPSILON) {
            return Optional.empty();
        }
        return Optional.of(Composition.of(parts));
    }

    /** How many of the hundred the parts asked come to, for the spirit to say why they make no core. */
    public static double asked(Map<VitaElement, Double> asked) {
        return asked.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * The particles of a thing converted into a new core: as many in all as it holds, now in the core's proportion (L1).
     * What it owes is no part of what it is, and stays owed.
     */
    public static Particles convert(Particles held, Composition core) {
        return Particles.in(core, held.present().total()).minus(held.owed());
    }

    /**
     * The spirit's work of converting {@code from} into {@code to} (L2), in UMU: every particle converted climbs or
     * descends the ladder from the primordial it was to the one it becomes ({@link MatterLaws#WORK_PER_STEP} of it for
     * each rung), and the spirit takes the shortest way, the least climbing that turns the one count into the other.
     * Stone made earth moves its fire three rungs down, its air two and its water one.
     */
    public static double work(Particles from, Particles to) {
        long carried = 0L;
        long climbed = 0L;
        for (int rung = 0; rung + 1 < Particles.ASPECTS.size(); rung++) {
            VitaElement aspect = Particles.ASPECTS.get(rung);
            carried = Math.addExact(carried, Math.subtractExact(to.count(aspect), from.count(aspect)));
            climbed = Math.addExact(climbed, Math.abs(carried));
        }
        return MatterLaws.WORK_PER_STEP * climbed / Particles.PER_UMU;
    }

    /**
     * The state of what a thing becomes comes from its particles (user, 01/10/2026): a natural thing is in its own
     * natural state, so lava made all water is water, and lava that keeps fire in it is what that fire makes of it (in
     * steam's proportion, vapour). Still the same thing, it stays as it is; what becomes formless keeps the state it is
     * in, until the physics gives state by agitation (stages 9 and 10).
     */
    public static State state(State now, Optional<Substance> was, Optional<Substance> becomes) {
        if (becomes.isEmpty() || becomes.equals(was)) {
            return now;
        }
        return becomes.get().nature();
    }
}
