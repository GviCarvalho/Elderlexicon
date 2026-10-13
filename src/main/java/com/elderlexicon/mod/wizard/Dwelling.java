package com.elderlexicon.mod.wizard;

import java.util.Locale;

/**
 * Where a Wizard lives (docs/mago-design.md, 3 and 5i): each dresses as its place asks, so one tells them apart from
 * afar. One met with no home of these, in the wilds, lives on its own.
 */
public enum Dwelling {
    /** On its own, in the wilds: a weathered cloak, the hood up. */
    WILDS,
    /** In its house in a village: a homely blue robe. */
    VILLAGE,
    /** In its tower: indigo sown with stars, the pointed hat. */
    TOWER,
    /** At a university: a gown, as its rank asks. */
    UNIVERSITY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Dwelling byId(String id) {
        for (Dwelling dwelling : values()) {
            if (dwelling.id().equals(id)) {
                return dwelling;
            }
        }
        return WILDS;
    }
}
