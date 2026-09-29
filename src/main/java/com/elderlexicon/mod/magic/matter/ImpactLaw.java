package com.elderlexicon.mod.magic.matter;

/**
 * The law of impact (docs/plano-rosa-dos-elementos.md, section 3): what flies carries the energy of its motion, half its
 * mass times the square of its speed, and where it strikes that energy becomes a blow. It is the same for everything
 * that flies (a condensed orb, pushed matter, a marked thing), and always the same for the same inputs.
 * <ul>
 *   <li>The mass is the UMU times the density of what flies: earth weighs, water half as much, fire and air almost
 *       nothing ({@link Qualities#density()}).</li>
 *   <li>The speed is in blocks a tick.</li>
 *   <li>Part of the energy hurts what it strikes, part breaks the blocks around the point, each costing its hardness
 *       (dirt 0.5, stone 1.5, obsidian 50; what cannot be broken is never broken), part pushes; the blow is heard louder
 *       and deeper the more energy it has.</li>
 * </ul>
 * Nothing it does is worth more than the energy it had.
 */
public final class ImpactLaw {

    /** The share of the energy that goes into breaking blocks; the rest hurts, pushes and is heard. */
    public static final double BREAKING_SHARE = 0.5D;
    /** Damage for each unit of energy, and the most one blow does. */
    public static final double DAMAGE_PER_ENERGY = 0.5D;
    public static final double MAX_DAMAGE = 60.0D;
    /** Below this energy the blow only touches: nothing breaks, no one is hurt, and it is barely heard. */
    public static final double FELT = 0.25D;
    /** From this energy the blow goes off like an explosion. */
    public static final double BLAST = 40.0D;
    /** Blocks never break farther than this from the point. */
    public static final int MAX_RADIUS = 6;
    /** What breaking a block costs beyond its hardness, so even the softest block takes something. */
    private static final double BREAK_BASE = 0.2D;

    private ImpactLaw() {
    }

    /** The energy of {@code mass} flying at {@code speed} blocks a tick. */
    public static double energy(double mass, double speed) {
        return 0.5D * Math.max(0.0D, mass) * speed * speed;
    }

    /** The energy of {@code umu} of something of these qualities flying at {@code speed}. */
    public static double energy(Qualities qualities, double umu, double speed) {
        return energy(qualities.density() * Math.max(0.0D, umu), speed);
    }

    /** How much the blow hurts what it strikes. */
    public static double damage(double energy) {
        return energy < FELT ? 0.0D : Math.min(MAX_DAMAGE, energy * DAMAGE_PER_ENERGY);
    }

    /** How much of the energy can go into breaking blocks. */
    public static double breaking(double energy) {
        return energy < FELT ? 0.0D : energy * BREAKING_SHARE;
    }

    /**
     * What breaking a block of {@code hardness} costs; infinite for what cannot be broken (a negative hardness, as
     * bedrock's).
     */
    public static double cost(double hardness) {
        return hardness < 0.0D ? Double.POSITIVE_INFINITY : hardness + BREAK_BASE;
    }

    /** How far from the point the blow can reach to break blocks, in blocks. */
    public static int radius(double energy) {
        double breaking = breaking(energy);
        if (breaking < cost(0.0D)) {
            return -1;
        }
        return (int) Math.min(MAX_RADIUS, Math.floor(Math.cbrt(breaking) - 0.5D));
    }

    /** How hard it pushes what it strikes, as speed added (blocks a tick). */
    public static double knockback(double energy) {
        return energy < FELT ? 0.0D : Math.min(3.0D, Math.sqrt(energy) * 0.15D);
    }

    /** How loud the blow is heard. */
    public static float volume(double energy) {
        return (float) Math.max(0.3D, Math.min(4.0D, 0.4D + 0.8D * Math.log10(1.0D + energy)));
    }

    /** The pitch of the blow: the more energy, the deeper. */
    public static float pitch(double energy) {
        return (float) Math.max(0.5D, Math.min(1.4D, 1.4D - 0.35D * Math.log10(1.0D + energy)));
    }

    /** Whether the blow goes off like an explosion. */
    public static boolean blast(double energy) {
        return energy >= BLAST;
    }
}
