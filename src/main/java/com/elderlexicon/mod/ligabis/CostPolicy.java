package com.elderlexicon.mod.ligabis;

/**
 * How much UMU each effect of a link costs its owner. Amounts of integrity are measured in hit points
 * (a block's capacity is expressed in the same unit by whoever builds its profile).
 */
public record CostPolicy(double umuPerHp, double umuPerHeatChange, double umuPerWetChange, double umuPerAirTick,
                         double umuPerBlockMoved) {

    /**
     * The mod's life rate (1 HP is worth 5 UMU) and first guesses for the rest: 1 UMU per member whose
     * heat or wetness is changed, 1 UMU per 100 ticks of air moved and 1 UMU per block of movement corrected.
     */
    public static final CostPolicy DEFAULT = new CostPolicy(5.0D, 1.0D, 1.0D, 0.01D, 1.0D);

    /** Cost of moving {@code hp} of damage to another member. */
    public double damage(double hp) {
        return hp * umuPerHp;
    }

    /** Cost of making {@code members} members catch fire or go out. */
    public double heatChange(int members) {
        return members * umuPerHeatChange;
    }

    /** Cost of making {@code members} blocks wet or dry. */
    public double wetChange(int members) {
        return members * umuPerWetChange;
    }

    /** Cost of moving {@code ticks} ticks of air between members. */
    public double air(double ticks) {
        return ticks * umuPerAirTick;
    }

    /** Cost of correcting {@code blocks} blocks of movement. */
    public double move(double blocks) {
        return blocks * umuPerBlockMoved;
    }

    /** Cost of killing or destroying a member that still had {@code remainingHp} left. */
    public double death(double remainingHp) {
        return remainingHp * umuPerHp;
    }
}
