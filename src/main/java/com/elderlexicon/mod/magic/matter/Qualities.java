package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * What matter, or energy, is like, from what it is made of (docs/plano-materia-emergente.md, section 2, and
 * docs/plano-rosa-dos-elementos.md, section 3). Each aspect gives it a
 * quality in the proportion it is in it: firmo weight, aqua cohesion, aura lightness, igni heat. The laws of the world
 * ask for the qualities, never for what the matter is called, so any mixture behaves by what it holds.
 *
 * @param weight    the share of firmo: it weighs, resists, thickens a liquid
 * @param cohesion  the share of aqua: it wets, puts out fire
 * @param lightness the share of aura: it is light, a gas of it spreads
 * @param heat      the share of igni: it burns, kindles, glows
 */
public record Qualities(double weight, double cohesion, double lightness, double heat) {

    /** A quality acts once it is this much of the matter: a little fire in stone does not make it burn. */
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

    /** It burns what touches it, kindles what burns around it and glows. */
    public boolean hot() {
        return heat >= ACTS;
    }

    /** It puts out fire; heat that outweighs it dries it. */
    public boolean wet() {
        return cohesion >= ACTS && cohesion > heat;
    }

    /** It weighs: a liquid of it is thick, and what wades into it is slowed. */
    public boolean heavy() {
        return weight >= ACTS;
    }

    /** It is light. */
    public boolean light() {
        return lightness >= ACTS;
    }

    /** How brightly it glows, 0 to 15, with its heat. */
    public int glow() {
        return hot() ? (int) Math.min(15L, Math.round(heat * 15.0D)) : 0;
    }

    /**
     * How the spirit tells what a portion is like: its state, its qualities and what it is made of
     * ("líquida, pesada e quente (terra 70%, fogo 30%)").
     */
    public static String describe(Matter matter) {
        Qualities qualities = of(matter);
        List<String> words = new ArrayList<>();
        words.add(switch (matter.state()) {
            case SOLID -> "sólida";
            case LIQUID -> "líquida";
            case GAS -> "gasosa";
            case PLASMA -> "em plasma";
        });
        if (qualities.heavy()) {
            words.add("pesada");
        }
        if (qualities.wet()) {
            words.add("molhada");
        }
        if (qualities.light()) {
            words.add("leve");
        }
        if (qualities.hot()) {
            words.add("quente");
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            text.append(i == 0 ? "" : i == words.size() - 1 ? " e " : ", ").append(words.get(i));
        }
        StringJoiner parts = new StringJoiner(", ", " (", ")");
        matter.composition().shares().entrySet().stream()
                .filter(share -> share.getValue() >= 0.005D)
                .sorted(Comparator.comparing(Map.Entry<VitaElement, Double>::getValue).reversed())
                .forEach(share -> parts.add(nameOf(share.getKey()) + " "
                        + String.format(Locale.ROOT, "%d%%", Math.round(share.getValue() * 100.0D))));
        return text.append(parts).toString();
    }

    private static String nameOf(VitaElement aspect) {
        return switch (aspect) {
            case FIRMO -> "terra";
            case AQUA -> "água";
            case AURA -> "ar";
            case IGNI -> "fogo";
            default -> aspect.runeId();
        };
    }
}
