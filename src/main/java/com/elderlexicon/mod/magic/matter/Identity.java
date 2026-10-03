package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * What some particles are, read in the table (docs/particulas-design.md, section 6). An anchor of vis chooses the part
 * of the table: without one, they are the thing whose code is nearest, within the tolerance, or formless matter when
 * near none; with one, they are the being whose proportion is nearest, sound or malformed by how far off it is. Only
 * what they hold counts: what they owe says how the thing is (cold, dry), not what it is.
 */
public sealed interface Identity {

    /** Nothing is held: every count is zero or owed. */
    record Nothing() implements Identity {
    }

    /** A natural thing, whose code is near what is held. */
    record Thing(Substance substance) implements Identity {
    }

    /** Matter near no code: it has no name and behaves by what it holds ({@link Qualities}). */
    record Formless(Composition composition) implements Identity {
    }

    /**
     * A being of the kind whose proportion is nearest: how far off the body is in each primordial (positive for too
     * much) and how far off in all, the sum of those.
     */
    record Creature(Being being, Map<VitaElement, Double> deviation, double distance) implements Identity {

        /** Off by less than this in a primordial, a body is sound in it (docs/vita-design.md). */
        public static final double SOUND = 0.03D;

        public Creature {
            EnumMap<VitaElement, Double> copy = new EnumMap<>(VitaElement.class);
            copy.putAll(deviation);
            deviation = Collections.unmodifiableMap(copy);
        }

        /** Whether it is off by less than {@link #SOUND} in every primordial. */
        public boolean sound() {
            return deviation.values().stream().allMatch(off -> Math.abs(off) < SOUND);
        }
    }
}
