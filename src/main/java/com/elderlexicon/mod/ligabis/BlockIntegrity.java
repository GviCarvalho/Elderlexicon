package com.elderlexicon.mod.ligabis;

/**
 * How much damage a block can absorb for its children before it gives way, in hit points. It grows with
 * the block's resistance to explosions, so a parent of obsidian outlasts a parent of dirt. The square root
 * keeps ordinary blocks apart from each other without making the toughest ones unbreakable.
 * <p>
 * The exact curve is a first guess to be tuned in play.
 */
public final class BlockIntegrity {

    /** Even the weakest block can take a hit. */
    public static final double MINIMUM = 1.0D;

    private BlockIntegrity() {
    }

    public static double capacity(double blastResistance) {
        if (blastResistance <= 0.0D) {
            return MINIMUM;
        }
        return Math.max(MINIMUM, Math.sqrt(blastResistance));
    }
}
