package com.elderlexicon.mod.nahva;

import java.util.List;

/**
 * Where the realms of Nahvá lie (docs/aprendizado-runas-design.md): one after another along z, every mage's instance one
 * after another along x, all so far apart that none is ever in sight of another.
 */
public final class RealmLayout {

    /**
     * The realms, in the order they are laid out along z. The second place is left empty: the first tower stood there,
     * and the tower of Lig'dar is laid out anew elsewhere rather than over its remains. Last, the mages' own islands.
     */
    public static final List<String> REALMS = List.of("igni", "", "tower", "kern");
    /** How far apart realms and instances are. */
    public static final int SPACING = 8192;

    private RealmLayout() {
    }

    /** The realm a position of Nahvá is in, by where the realms are laid out. */
    public static String realmAt(double z) {
        int index = (int) Math.round(z / SPACING);
        return index >= 0 && index < REALMS.size() ? REALMS.get(index) : "";
    }
}
