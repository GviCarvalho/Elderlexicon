package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Where a portion stands on the rose of the elements (docs/plano-rosa-dos-elementos.md, section 2): the plane the four
 * aspects pull on, each toward its own end, with vis at the centre.
 * <pre>
 *                 aura (lightness)
 *                      |
 *  aqua (cold) ------ vis ------ igni (heat)
 *                      |
 *                 firmo (weight)
 * </pre>
 * <ul>
 *   <li>{@code x} runs from cold to heat: fire less water, -1 to 1.</li>
 *   <li>{@code y} runs from weight to lightness: air less earth, -1 to 1.</li>
 *   <li>{@code thermal} and {@code mechanical} are the latent tension of each axis: how much of the portion is opposites
 *       cancelling out (twice the lesser of fire and water, and of air and earth). Steam holds much thermal tension,
 *       and pure vis, a quarter of each aspect, holds as much as a portion can.</li>
 * </ul>
 * Nothing is lost in the change of coordinates: the character ({@code |x| + |y|}) and the latent tension always add up
 * to the whole portion, so the more a portion leans one way, the less tension it holds.
 */
public record Rose(double x, double y, double thermal, double mechanical) {

    /** Vis is the balance of the four: each of its UMU is a quarter of each aspect. */
    private static final double VIS_SHARE = 0.25D;
    private static final double EPSILON = 1.0E-9D;

    /** The centre: nothing leans anywhere and nothing cancels out. */
    public static final Rose CENTRE = new Rose(0.0D, 0.0D, 0.0D, 0.0D);

    /** Where matter of this composition stands. */
    public static Rose of(Composition composition) {
        Objects.requireNonNull(composition, "composition");
        return of(composition.shares());
    }

    /**
     * Where a portion of these amounts of each aspect stands (any positive measure: UMU, parts). Vis counts as a quarter
     * of each of the four, so energy as well as matter has a place: all of it vis is the centre, at full tension.
     */
    public static Rose of(Map<VitaElement, Double> amounts) {
        Map<VitaElement, Double> shares = spread(amounts);
        if (shares.isEmpty()) {
            return CENTRE;
        }
        double igni = shares.getOrDefault(VitaElement.IGNI, 0.0D);
        double aqua = shares.getOrDefault(VitaElement.AQUA, 0.0D);
        double aura = shares.getOrDefault(VitaElement.AURA, 0.0D);
        double firmo = shares.getOrDefault(VitaElement.FIRMO, 0.0D);
        return new Rose(igni - aqua, aura - firmo, 2.0D * Math.min(igni, aqua), 2.0D * Math.min(aura, firmo));
    }

    /** Where one aspect alone stands: at its own end, or the centre for vis. */
    public static Rose of(VitaElement aspect) {
        return of(Map.of(aspect, 1.0D));
    }

    /** All the latent tension it holds, 0 to 1: how much of it is opposites cancelling out. */
    public double latent() {
        return thermal + mechanical;
    }

    /** How far it leans from the centre, 0 to 1 (the rest of it is latent tension). */
    public double character() {
        return Math.abs(x) + Math.abs(y);
    }

    /** How far it is from the centre, in the plane. */
    public double distance() {
        return Math.hypot(x, y);
    }

    /**
     * The share of each of the four in a portion standing here: the inverse of {@link #of(Map)}, with the tension split
     * evenly between the two opposites of its axis.
     */
    public Map<VitaElement, Double> shares() {
        EnumMap<VitaElement, Double> shares = new EnumMap<>(VitaElement.class);
        put(shares, VitaElement.IGNI, Math.max(0.0D, x) + thermal / 2.0D);
        put(shares, VitaElement.AQUA, Math.max(0.0D, -x) + thermal / 2.0D);
        put(shares, VitaElement.AURA, Math.max(0.0D, y) + mechanical / 2.0D);
        put(shares, VitaElement.FIRMO, Math.max(0.0D, -y) + mechanical / 2.0D);
        return shares;
    }

    /** The shares of the four in these amounts, vis spread over them. */
    static Map<VitaElement, Double> spread(Map<VitaElement, Double> amounts) {
        Objects.requireNonNull(amounts, "amounts");
        EnumMap<VitaElement, Double> spread = new EnumMap<>(VitaElement.class);
        double total = 0.0D;
        for (Map.Entry<VitaElement, Double> entry : amounts.entrySet()) {
            double amount = entry.getValue() == null ? 0.0D : entry.getValue();
            if (amount < 0.0D) {
                throw new IllegalArgumentException("a negative amount of " + entry.getKey().runeId());
            }
            if (amount <= EPSILON) {
                continue;
            }
            total += amount;
            if (entry.getKey() == VitaElement.BALANCED) {
                for (VitaElement aspect : VitaElement.values()) {
                    if (aspect != VitaElement.BALANCED) {
                        spread.merge(aspect, amount * VIS_SHARE, Double::sum);
                    }
                }
            } else {
                spread.merge(entry.getKey(), amount, Double::sum);
            }
        }
        if (total <= EPSILON) {
            return Map.of();
        }
        double sum = total;
        spread.replaceAll((aspect, amount) -> amount / sum);
        return spread;
    }

    private static void put(Map<VitaElement, Double> shares, VitaElement aspect, double share) {
        if (share > EPSILON) {
            shares.put(aspect, share);
        }
    }
}
