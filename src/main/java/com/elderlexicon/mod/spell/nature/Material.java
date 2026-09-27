package com.elderlexicon.mod.spell.nature;

/**
 * What a block is made of, as the laws of nature see it (docs/interacoes-design.md): how much heat it holds per degree,
 * how well it passes heat on, whether it is a gas that carries pressure and how hot it is by its own nature. The laws
 * only look at these numbers, never at which element or spell put the block there.
 */
public enum Material {
    /** Carries pressure and lets it out to the open sky; hot air rises. */
    AIR(0.1D, 0.1D, 0.05D, true, 0.0D),
    /** Boils into vapor, freezes into ice. */
    WATER(1.0D, 0.5D, 0.005D, false, 0.0D),
    ICE(0.9D, 0.8D, 0.005D, false, 0.0D),
    SNOW(0.3D, 0.2D, 0.01D, false, 0.0D),
    /** Molten rock: born hot, sets into stone as it cools. */
    LAVA(3.0D, 0.4D, 0.0005D, false, 15.0D),
    STONE(0.8D, 0.5D, 0.01D, false, 0.0D),
    SOIL(0.6D, 0.3D, 0.01D, false, 0.0D),
    SAND(0.6D, 0.3D, 0.01D, false, 0.0D),
    OBSIDIAN(0.8D, 0.5D, 0.01D, false, 0.0D),
    BASALT(0.8D, 0.5D, 0.01D, false, 0.0D),
    /** Any other solid. */
    SOLID(0.8D, 0.4D, 0.01D, false, 0.0D),
    /** Outside the loaded world: nothing passes through it. */
    VOID(0.0D, 0.0D, 0.0D, false, 0.0D);

    /** UMU it takes to warm one block of it by one degree of {@code Heat}. */
    public final double capacity;
    /** How well heat passes through it, from 0 to 1. */
    public final double conduction;
    /** The share of its extra heat it loses to its surroundings each step. */
    public final double loss;
    /** A gas: pressure moves through it and, out in the open, escapes. */
    public final boolean gas;
    /** How hot it is by nature, when something first touches it (lava is molten). */
    public final double inherent;

    Material(double capacity, double conduction, double loss, boolean gas, double inherent) {
        this.capacity = capacity;
        this.conduction = conduction;
        this.loss = loss;
        this.gas = gas;
        this.inherent = inherent;
    }

    /** Whether pressure can sit in it and move through it: gases, and water, where vapor forms as bubbles. */
    public boolean carriesPressure() {
        return gas || this == WATER;
    }
}
