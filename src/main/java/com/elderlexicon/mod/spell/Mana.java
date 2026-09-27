package com.elderlexicon.mod.spell;

/**
 * Mana, the mage's Vis: in the mod it is the experience the mage has gathered (docs/condensacao-design.md), at ten
 * points of experience to one UMU, with no ceiling (the book's Anchor of a Hundred is not kept here). Condensed and
 * released it is pure energy; for now it is only light.
 */
public final class Mana {

    /** Points of experience in one UMU of Vis. */
    public static final double XP_PER_UMU = 10.0D;

    private Mana() {
    }

    /**
     * All the experience points of a mage at {@code level} with {@code progress} (0 to 1) toward the next, counted in
     * doubles so that absurd levels do not overflow (the game's own formula for each stretch of levels).
     */
    public static double points(int level, float progress) {
        double l = Math.max(0, level);
        double total;
        if (l <= 16) {
            total = l * l + 6.0D * l;
        } else if (l <= 31) {
            total = 2.5D * l * l - 40.5D * l + 360.0D;
        } else {
            total = 4.5D * l * l - 162.5D * l + 2220.0D;
        }
        return total + Math.max(0.0F, Math.min(1.0F, progress)) * toNext(l);
    }

    private static double toNext(double level) {
        if (level >= 30) {
            return 112.0D + (level - 30.0D) * 9.0D;
        }
        if (level >= 15) {
            return 37.0D + (level - 15.0D) * 5.0D;
        }
        return 7.0D + level * 2.0D;
    }

    public static double umuOf(double points) {
        return Math.max(0.0D, points) / XP_PER_UMU;
    }

    /** How long the light of released Vis lingers where it lands, in seconds: longer the more of it, slowly. */
    public static double lightSeconds(double umu) {
        return Math.min(30.0D, 2.0D + Math.log1p(Math.max(0.0D, umu)));
    }
}
