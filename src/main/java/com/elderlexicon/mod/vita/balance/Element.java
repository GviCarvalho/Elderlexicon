package com.elderlexicon.mod.vita.balance;

/**
 * Simple enum describing the four Vita components in their cyclic order.
 */
public enum Element {
    AQUA,
    IGNI,
    AURA,
    FIRMO;

    private static final Element[] CYCLE = values();

    public Element next() {
        return CYCLE[(ordinal() + 1) % CYCLE.length];
    }

    public Element secondNext() {
        return CYCLE[(ordinal() + 2) % CYCLE.length];
    }

    public Element thirdNext() {
        return CYCLE[(ordinal() + 3) % CYCLE.length];
    }
}
