package com.elderlexicon.mod.magic.flow;

import com.elderlexicon.mod.spell.Density;
import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * How intense captured energy is when a verb releases it (docs/condensacao-design.md): energy is intensity × extension,
 * and each aspect measures its intensity its own way. Fire keeps the heat of what was captured (all of it in one point
 * when released at once); earth, water and air pressed into one point are as dense or as pressed as all of it. These are
 * laws of nature, one per aspect, and nothing else in the flow knows the elements apart.
 */
public final class IntensityLaws {

    private static final double EPSILON = 1.0E-4D;

    /** The intensity of {@code worked} UMU captured from {@code sources} sources, or null when it is only common. */
    @FunctionalInterface
    public interface Law {
        Double intensity(double worked, int sources, boolean atOnce);
    }

    private static final Map<VitaElement, Law> LAWS = new EnumMap<>(VitaElement.class);

    static {
        // Fire guards the heat of what was captured; released at once, all of it in one blow.
        LAWS.put(VitaElement.IGNI, (worked, sources, atOnce) -> {
            double heat = Heat.of(worked, sources, atOnce);
            return heat > Heat.COMMON + EPSILON ? heat : null;
        });
        // Earth pressed into one block is as dense as all of it.
        LAWS.put(VitaElement.FIRMO, (worked, sources, atOnce) ->
                atOnce && worked > EPSILON ? Density.of(worked, sources, true) : null);
        // Water and air pressed into one point: its pressure is all of it.
        Law pressure = (worked, sources, atOnce) -> atOnce && worked > EPSILON ? worked : null;
        LAWS.put(VitaElement.AQUA, pressure);
        LAWS.put(VitaElement.AURA, pressure);
    }

    private IntensityLaws() {
    }

    /** Gives an aspect its law, or another one. */
    public static synchronized void register(VitaElement element, Law law) {
        LAWS.put(element, law);
    }

    public static synchronized Optional<Law> of(VitaElement element) {
        return Optional.ofNullable(LAWS.get(element));
    }
}
