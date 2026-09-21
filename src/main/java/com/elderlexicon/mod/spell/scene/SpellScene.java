package com.elderlexicon.mod.spell.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared stage of one world. Every spell any caster releases registers its {@link Emission}s here,
 * so spells interact whoever cast them and whenever they were cast, as long as their emissions
 * are alive in the same place. The interaction laws read it to decide what the emissions do to
 * each other.
 */
public final class SpellScene {

    /** Emissions beyond this many are dropped so the world can never be flooded. */
    public static final int MAX_EMISSIONS = 512;

    /** Overlap of two emissions that belong to different spells. */
    public record Overlap(Emission first, Emission second) { }

    private final List<Emission> emissions = new ArrayList<>();
    private int nextSpellId;

    /** Each spell asks for its own id, so overlaps between different spells can be told apart. */
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

    public synchronized boolean isEmpty() {
        return emissions.isEmpty();
    }

    public synchronized List<Emission> emissions() {
        return List.copyOf(emissions);
    }

    public synchronized List<Emission> activeAt(long tick) {
        return emissions.stream().filter(emission -> emission.activeAt(tick)).toList();
    }

    /** Drops every emission that has run its course; returns how many were dropped. */
    public synchronized int prune(long tick) {
        int before = emissions.size();
        emissions.removeIf(emission -> emission.expiredAt(tick));
        return before - emissions.size();
    }

    /**
     * Pairs of emissions from different spells that are alive at {@code tick} and touching. Only
     * emissions that share a cell of the spatial grid are compared, so the cost follows how many
     * emissions are near each other, not how many exist. The result is sorted, so it does not depend
     * on the order the emissions were added in.
     */
    public synchronized List<Overlap> overlapsAt(long tick) {
        List<Emission> active = emissions.stream().filter(emission -> emission.activeAt(tick)).toList();
        if (active.size() < 2) {
            return List.of();
        }
        Map<Long, List<Emission>> grid = new HashMap<>();
        for (Emission emission : active) {
            for (long cell : emission.cells()) {
                grid.computeIfAbsent(cell, key -> new ArrayList<>()).add(emission);
            }
        }
        Set<Long> seen = new HashSet<>();
        List<Overlap> pairs = new ArrayList<>();
        for (List<Emission> neighbours : grid.values()) {
            for (int i = 0; i < neighbours.size(); i++) {
                for (int j = i + 1; j < neighbours.size(); j++) {
                    Emission a = neighbours.get(i);
                    Emission b = neighbours.get(j);
                    if (a.spellId() == b.spellId()) {
                        continue;
                    }
                    int low = Math.min(a.id(), b.id());
                    int high = Math.max(a.id(), b.id());
                    if (!seen.add(((long) low << 32) | (high & 0xFFFFFFFFL))) {
                        continue;
                    }
                    if (a.overlaps(b)) {
                        pairs.add(low == a.id() ? new Overlap(a, b) : new Overlap(b, a));
                    }
                }
            }
        }
        pairs.sort(Comparator.<Overlap>comparingInt(o -> o.first().id()).thenComparingInt(o -> o.second().id()));
        return pairs;
    }
}
