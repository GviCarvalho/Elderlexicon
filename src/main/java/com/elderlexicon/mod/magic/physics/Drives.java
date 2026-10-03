package com.elderlexicon.mod.magic.physics;

/**
 * The four drives as numbers (docs/particulas-design.md, section 2). Every particle has one drive and applies it to any
 * particle near it; how it takes the drives of the others comes from its own. These are the only numbers the laws of
 * {@link Box} know: one per kind of particle, never one per pair, and never one per substance. The few that are not
 * per kind (where water melts and boils, where anything glows, where air comes apart) are the same for every thing.
 * <ul>
 *   <li>igni wants to move: it agitates (its free particles are the agitation, the heat);</li>
 *   <li>aura wants room: it spreads, and goes where there is less;</li>
 *   <li>aqua wants to join: it holds together (cohesion);</li>
 *   <li>firmo wants to stay: it weighs and holds in place (mass).</li>
 * </ul>
 * Indexed in the order of the ladder of states: firmo, aqua, aura, igni.
 */
final class Drives {

    static final int FIRMO = 0;
    static final int AQUA = 1;
    static final int AURA = 2;
    static final int IGNI = 3;

    /** The agitation of the world at rest, about 20 °C: a block holding no free igni and owing none is this warm. */
    static final double AT_REST = 1.0D;

    /**
     * How hard a held particle holds the matter together: firmo by its mass, aqua by its cohesion. Air in the pores is
     * room, not structure; fire held in matter holds nothing, and wedges it apart.
     */
    static final double[] HOLD = {7.0D, 1.0D, 0.0D, 0.0D};
    /**
     * How hard a held particle of air pushes the matter apart. A solid is still: every particle in it stays, as mass
     * does, and keeps the air in its pores; when the air spreads more than that, the solid crumbles and flies (dust).
     */
    static final double SPREAD = 7.1D;

    /**
     * How much agitation a particle takes to warm by the world's rest: one particle of fire warms eighty of earth, twenty
     * of water, three hundred and twenty of air. Fire is strong, as the real one is: the fire held in a block of wood
     * could warm a block of stone by fifteen times the world's rest.
     */
    static final double[] CAPACITY = {1.0D / 80.0D, 4.0D / 80.0D, 0.25D / 80.0D, 0.5D / 80.0D};
    /** How readily a particle passes agitation on: earth well, air hardly. */
    static final double[] CONDUCTION = {1.0D, 0.5D, 0.03D, 0.5D};
    /** How much a particle weighs: what sinks and what floats. */
    static final double[] WEIGHT = {3.0D, 1.0D, 0.05D, 0.0D};

    /** Held matter is solid while its agitation is below this many times how hard it holds together. */
    static final double MELT = 0.93D;
    /** And it boils past this many times: water, which holds by cohesion alone, melts at 0.93 and boils at 1.27. */
    static final double BOIL = 1.27D;
    /**
     * Anything glows past this agitation, about 525 °C (the Draper point, the same for every thing), and its glow
     * carries agitation away as light. Gas that glows is flame: fire.
     */
    static final double GLOW = 2.7D;
    /**
     * Past this agitation, about 5600 °C (the face of the sun), the agitation beats the expansion: air comes apart
     * into plasma, which is lightning.
     */
    static final double PLASMA = 20.0D;
    /** How readily the glow carries agitation from the brighter of two neighbours to the other. */
    static final double RADIATE = 0.02D;

    /**
     * The air in the pores of a solid escapes once the agitation passes this share of how hard the solid around each
     * particle of air keeps it: much solid around little air keeps it long (stone), a little around much lets it go.
     */
    static final double PORES = 0.12D;
    /**
     * Water in other matter boils later by this share of how hard the earth around each particle of water holds it: a
     * little water in much earth is held hard (the water in stone stays until it melts), much water loosely (mud and
     * wood dry near water's boiling).
     */
    static final double WET = 0.03D;
    /**
     * Fire held in matter is let go past this agitation when nothing crowds it (about 270 °C): it needs room to
     * flicker, and the mass takes room.
     */
    static final double IGNITION = 1.85D;
    /**
     * How much the mass crowds the fire: past {@link #IGNITION}, the agitation to let it go grows with the cube of the
     * mass per room left in the block (the room is a volume). Wood lets its fire go near 300 °C, coal near 600 °C, and
     * what is mostly mass (gold, stone) melts first.
     */
    static final double CROWDING = 1.0D / 3.0D;
    /** The water a particle of fire keeps boiled: what boiling took, given back when it condenses (steam is 9 to 1). */
    static final long VAPOUR = 9L;
    /**
     * The air a particle of fire takes as it is let go: it leaves bound in the smoke. A fire needs much air, as the real
     * one does (eleven times the weight of the coal), and that air is what the flame warms: with fifty to one, the
     * hottest a flame gets is about 2100 K.
     */
    static final long AIR_PER_FIRE = 50L;

    /** The share of what would even out two neighbours that passes between them in one step (at most a sixth). */
    static final double CONDUCT = 1.0D / 6.0D;
    /** The share of what would even out the pressure of two neighbours that flows in one step. */
    static final double FLOW = 1.0D / 6.0D;
    /** How fast what is lighter rises through what is heavier above it (a block swaps at most a quarter in a step). */
    static final double RISE = 1.0D;
    /** The share of the excess agitation that boils water in one step, and of the lack that condenses vapour. */
    static final double BOILING = 0.5D;
    /** The share of the held fire a burning block lets go in one step, once it is twice as agitated as its ignition. */
    static final double BURNING = 0.02D;
    /** The earth that grips each particle of fire in char: three to two, the code of coal. */
    static final double CHAR = 1.5D;
    /** The share of the loose fire in a solid past its ignition that the mass grips into char in one step. */
    static final double CHARRING = 0.05D;
    /** The share of the air or the earth that escapes held matter in one step, once the agitation lets it go. */
    static final double ESCAPE = 0.2D;

    private Drives() {
    }
}
