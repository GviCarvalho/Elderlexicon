package com.elderlexicon.mod.spell.sight;

import com.elderlexicon.mod.magic.lexicon.Lexicons;

import java.util.Locale;

/**
 * Revelation ({@code igni surgit}, {@code m1 surgit}, {@code vis surgit}): the spirit looks through the mage's eyes for
 * what was asked, and the mage sees only that ({@code docs/surgit-visao-design.md}, section 1). This part holds the
 * rules that do not touch the world: what can be sought, how far and for how long the gaze reaches, and its cost.
 */
public final class Revelation {

    /** Without a potency (quantum before surgit) the gaze reaches this far, in blocks. */
    public static final double DEFAULT_RADIUS = 16.0D;
    /** Without chronos the gaze lasts the trance window: two seconds (book, chapter V). */
    public static final double DEFAULT_SECONDS = 2.0D;
    /** Blocks are searched only this far, whatever the radius: beyond it only creatures are seen. */
    public static final double MAX_BLOCK_RADIUS = 32.0D;
    /** Creatures are seen at most this far. */
    public static final double MAX_RADIUS = 128.0D;
    /** Sight is light, not matter: each block of radius held for a second costs this much. */
    public static final double UMU_PER_RADIUS_SECOND = 0.1D;

    private Revelation() {
    }

    /** What the spirit looks for. */
    public enum Kind {
        IGNI(0xFF6A2A),
        AQUA(0x3FA9FF),
        FIRMO(0xB08850),
        AURA(0xE8F4FF),
        /** Everything that carries magic: marks, bound scrolls, spells in the scene. */
        VIS(0xC080FF),
        /** Everything that carries one mark. */
        MARK(0xF0D080);

        private final int color;

        Kind(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }

        /**
         * The kind sought by a source rune, as the lexicon says of it ({@code reveals}); anything else (no source, vis,
         * a source that names nothing to reveal) is mana, as the book fills the gap.
         */
        public static Kind ofSource(String runeId) {
            if (runeId == null) {
                return VIS;
            }
            String reveals = Lexicons.get().traitsOf(runeId.toLowerCase(Locale.ROOT)).reveals();
            if (reveals == null) {
                return VIS;
            }
            for (Kind kind : values()) {
                if (kind != MARK && kind.name().equalsIgnoreCase(reveals.trim())) {
                    return kind;
                }
            }
            return VIS;
        }
    }

    /**
     * How far a gaze of {@code potency} UMU held for {@code seconds} reaches: sight is paid per block of reach per
     * second, so the UMU given to surgit buys its reach ({@code firmo quantum 20 surgit}). Without a potency it reaches
     * the default; never past {@link #MAX_RADIUS}.
     */
    public static double radius(Double potency, double seconds) {
        if (potency == null || potency <= 0.0D) {
            return DEFAULT_RADIUS;
        }
        return Math.min(potency / (UMU_PER_RADIUS_SECOND * Math.max(seconds, 1.0E-3D)), MAX_RADIUS);
    }

    /**
     * Whether something holding {@code umu} is what was asked with a quantum before the source
     * ({@code quantum 2 firmo surgit}): the Old Tongue writes only whole numbers, so the spirit compares whole UMU.
     * Nothing asked matches everything; what is beyond measure matches nothing asked.
     */
    public static boolean matches(double umu, Double wanted) {
        if (wanted == null) {
            return true;
        }
        return Double.isFinite(umu) && Math.round(umu) == Math.round(wanted);
    }

    /** How long the gaze lasts, asked by chronos or the default. */
    public static double seconds(Double chronos) {
        return chronos == null || chronos <= 0.0D ? DEFAULT_SECONDS : chronos;
    }

    public static int ticks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0D));
    }

    /** The lightest earth worth telling apart (sand, dirt: about half a UMU) and the densest (obsidian). */
    public static final double LIGHT_EARTH_UMU = 0.3D;
    public static final double DENSE_EARTH_UMU = 50.0D;

    /**
     * How much UMU of earth a block holds (book, chapter III: "uma pedra pesada ... possui um valor exato em UMU
     * expresso em matéria de Terra"): its hardness, how hard it is to break. Dirt holds 0.5, stone 1.5, ores 3 to
     * 4.5, obsidian 50. What cannot be broken (bedrock) is beyond measure.
     */
    public static double earthUmu(float hardness) {
        return hardness < 0.0F ? Double.POSITIVE_INFINITY : hardness;
    }

    /**
     * How strongly the spirit sees a block of {@code umu}, from 0 (the lightest earth) to 1 (the densest and beyond),
     * on a logarithmic scale: dense earth is a hundred times heavier than loose soil.
     */
    public static double density(double umu) {
        if (!(umu > LIGHT_EARTH_UMU)) {
            return 0.0D;
        }
        if (umu >= DENSE_EARTH_UMU) {
            return 1.0D;
        }
        return Math.log(umu / LIGHT_EARTH_UMU) / Math.log(DENSE_EARTH_UMU / LIGHT_EARTH_UMU);
    }

    public static double cost(double radius, double seconds) {
        return UMU_PER_RADIUS_SECOND * Math.max(0.0D, radius) * Math.max(0.0D, seconds);
    }
}
