package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.Map;

/**
 * What something that flies is like, for the law of impact (docs/plano-rosa-dos-elementos.md, section 3): each aspect
 * gives it a quality in the proportion it is in it, firmo weight, aqua cohesion, aura lightness, igni heat. A blow
 * weighs by them, and the hot burns where it strikes, the wet puts fire out.
 * <p>
 * Matter in the world no longer goes by them (docs/particulas-design.md, stage 9): what a block of it does comes from
 * its particles and how agitated they are, and the fire in its code is fuel, not heat. What flies keeps them until it
 * carries its own agitation.
 *
 * @param weight    the share of firmo: it weighs
 * @param cohesion  the share of aqua: it wets, puts out fire
 * @param lightness the share of aura: it is light
 * @param heat      the share of igni: it burns
 */
public record Qualities(double weight, double cohesion, double lightness, double heat) {

    /** A quality acts once it is this much of what flies: a little fire in a stone thrown does not make it burn. */
    public static final double ACTS = 0.25D;

    /** How much a UMU of each aspect weighs: earth all of it, water half, fire and air almost nothing. */
    private static final double EARTH_DENSITY = 1.0D;
    private static final double WATER_DENSITY = 0.5D;
    private static final double LIGHT_DENSITY = 0.05D;

    public static Qualities of(Matter matter) {
        return of(matter.composition());
    }

    public static Qualities of(Composition composition) {
        return ofShares(composition.shares());
    }

    /**
     * The qualities of a portion of these amounts of each aspect, energy as well as matter: vis is a quarter of each of
     * the four (docs/plano-rosa-dos-elementos.md), so an orb of vis is a little of everything.
     */
    public static Qualities of(Map<VitaElement, Double> amounts) {
        return ofShares(Rose.spread(amounts));
    }

    /** The qualities of one aspect alone. */
    public static Qualities of(VitaElement aspect) {
        return of(Map.of(aspect, 1.0D));
    }

    private static Qualities ofShares(Map<VitaElement, Double> shares) {
        return new Qualities(shares.getOrDefault(VitaElement.FIRMO, 0.0D), shares.getOrDefault(VitaElement.AQUA, 0.0D),
                shares.getOrDefault(VitaElement.AURA, 0.0D), shares.getOrDefault(VitaElement.IGNI, 0.0D));
    }

    /** How much a UMU of it weighs, its mass: what makes a blow of it hit hard ({@link ImpactLaw}). */
    public double density() {
        return weight * EARTH_DENSITY + cohesion * WATER_DENSITY + (lightness + heat) * LIGHT_DENSITY;
    }

    /** It burns what it strikes. */
    public boolean hot() {
        return heat >= ACTS;
    }

    /** It puts out fire where it strikes; heat that outweighs it dries it. */
    public boolean wet() {
        return cohesion >= ACTS && cohesion > heat;
    }

    /** It weighs: its blow is heard deep. */
    public boolean heavy() {
        return weight >= ACTS;
    }

    /** It is light. */
    public boolean light() {
        return lightness >= ACTS;
    }
}
