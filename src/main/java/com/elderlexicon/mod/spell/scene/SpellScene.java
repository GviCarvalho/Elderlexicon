package com.elderlexicon.mod.spell.scene;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared stage for every spell released by one cast. Function handlers register {@link Emission}s
 * here; the interaction laws (later) read them to decide what the emissions do to each other.
 */
public final class SpellScene {

    /** Emissions beyond this many are dropped so a cast can never flood the server. */
    public static final int MAX_EMISSIONS = 64;

    /** Overlap of two emissions that belong to different spells. */
    public record Overlap(Emission first, Emission second) { }

    private final List<Emission> emissions = new ArrayList<>();
    private int nextSpellId;

    /** Each spell of a block asks for its own id, so overlaps between different spells can be told apart. */
    public synchronized int registerSpell() {
        return nextSpellId++;
    }

    /** @return false when the scene is full and the emission was dropped */
    public synchronized boolean add(Emission emission) {
        if (emission == null || emissions.size() >= MAX_EMISSIONS) {
            return false;
        }
        emissions.add(emission);
        return true;
    }

    public synchronized List<Emission> emissions() {
        return List.copyOf(emissions);
    }

    public synchronized List<Emission> activeAt(long tick) {
        return emissions.stream().filter(emission -> emission.activeAt(tick)).toList();
    }

    /** Pairs of emissions from different spells that are alive at {@code tick} and touching. */
    public synchronized List<Overlap> overlapsAt(long tick) {
        List<Emission> active = emissions.stream().filter(emission -> emission.activeAt(tick)).toList();
        List<Overlap> pairs = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                Emission a = active.get(i);
                Emission b = active.get(j);
                if (a.spellId() != b.spellId() && a.overlaps(b)) {
                    pairs.add(new Overlap(a, b));
                }
            }
        }
        return pairs;
    }
}
