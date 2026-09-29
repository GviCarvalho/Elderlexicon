package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * What matter is like, from what it is made of (docs/plano-materia-emergente.md, section 2). Each aspect gives it a
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

    public static Qualities of(Matter matter) {
        Composition composition = matter.composition();
        return new Qualities(composition.share(VitaElement.FIRMO), composition.share(VitaElement.AQUA),
                composition.share(VitaElement.AURA), composition.share(VitaElement.IGNI));
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
