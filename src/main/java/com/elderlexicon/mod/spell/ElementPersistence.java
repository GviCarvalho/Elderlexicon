package com.elderlexicon.mod.spell;

import java.util.Locale;
import java.util.Set;

/**
 * Whether what an element brings into the world stays or goes.
 * <ul>
 *   <li><b>Permanent</b> matter appears and stays (water, earth, mud, magma, marked things). Chronos is how long
 *       it stays: when the window ends it is gone, and while it lasts it stands back up if broken (book 4.3.2:
 *       "igni exsugat chronos firmo vocant", a barrier that "stays active and regenerates").</li>
 *   <li><b>Ephemeral</b> matter appears and goes (fire, air, Vis, lightning, steam, mist, dust). Chronos is how
 *       long it keeps acting on that spot: "aura chronos 10 vocant" blows there for 10 seconds.</li>
 * </ul>
 */
public enum ElementPersistence {
    PERMANENT,
    EPHEMERAL;

    private static final Set<String> PERMANENT_RUNES = Set.of("aqua", "firmo", "lutum", "fusus");

    /** By rune id, so fusions keep their own nature (steam goes, though it maps to aqua). */
    public static ElementPersistence of(String elementRuneId) {
        if (elementRuneId == null) {
            return EPHEMERAL;
        }
        return PERMANENT_RUNES.contains(elementRuneId.trim().toLowerCase(Locale.ROOT)) ? PERMANENT : EPHEMERAL;
    }
}
