package com.elderlexicon.mod.spell;

/**
 * The density of earth, its intensity (docs/condensacao-design.md): how many UMU one block of it holds, which is what
 * the revelation already sees in it (its hardness). Unlike heat, density is the matter itself: condensed earth becomes
 * denser rock, and past obsidian the mass starts to pull what is around it, a small well of gravity that settles
 * back into obsidian as it relaxes. Carbon condensed hard enough becomes diamond.
 */
public final class Density {

    /** Loose soil, the common earth. */
    public static final double SOIL = 0.5D;
    public static final double STONE = 1.5D;
    public static final double DEEPSLATE = 3.0D;
    public static final double OBSIDIAN = 50.0D;
    /** Past this the mass falls into itself: a black hole (twenty times obsidian, a draft value). */
    public static final double BLACK_HOLE = 1000.0D;
    /** How fast a black hole evaporates (small ones go fast, as Hawking's radiation has it), in ticks. */
    public static final double EVAPORATING_TICKS = 60.0D;
    /** How long a supercondensed mass takes to relax toward obsidian, in ticks (its time constant). */
    public static final double RELAXING_TICKS = 200.0D;
    /** Carbon this dense or more becomes diamond. */
    public static final double DIAMOND_PRESSURE = DEEPSLATE;
    /** Coal it takes to make one diamond, as it takes nine to make a block. */
    public static final int COAL_PER_DIAMOND = 9;

    private Density() {
    }

    /** The rock a density makes, from loose soil to obsidian; past obsidian, a mass that pulls. */
    public enum Rock {
        SOIL,
        STONE,
        DEEPSLATE,
        OBSIDIAN,
        /** Denser than any rock: a well of gravity while it lasts, obsidian once it settles. */
        WELL,
        /** So dense it falls into itself: it swallows creatures and the ground around it until it evaporates. */
        BLACK_HOLE
    }

    /**
     * The rock nearest in density, on a logarithmic scale (dense earth is a hundred times heavier than loose soil):
     * the boundaries lie halfway between the densities of the rocks on each side.
     */
    public static Rock rock(double density) {
        if (density >= BLACK_HOLE) {
            return Rock.BLACK_HOLE;
        }
        if (density > OBSIDIAN) {
            return Rock.WELL;
        }
        if (density >= Math.sqrt(DEEPSLATE * OBSIDIAN)) {
            return Rock.OBSIDIAN;
        }
        if (density >= Math.sqrt(STONE * DEEPSLATE)) {
            return Rock.DEEPSLATE;
        }
        if (density >= Math.sqrt(SOIL * STONE)) {
            return Rock.STONE;
        }
        return Rock.SOIL;
    }

    /** How dense earth is released: like heat, all of it at once in one block, or spread keeping its average. */
    public static double of(double captured, int sources, boolean atOnce) {
        if (captured <= 0.0D) {
            return SOIL;
        }
        return atOnce ? captured : captured / Math.max(1, sources);
    }

    /** What is left of a supercondensed mass {@code ticks} after it formed: it relaxes toward obsidian. */
    public static double relaxed(double density, double ticks) {
        if (density <= OBSIDIAN || ticks <= 0.0D) {
            return density;
        }
        return OBSIDIAN + (density - OBSIDIAN) * Math.exp(-ticks / RELAXING_TICKS);
    }

    /** A black hole one tick later: it loses its share to evaporation (what it eats is added by the caller). */
    public static double evaporateStep(double density) {
        return OBSIDIAN + (density - OBSIDIAN) * Math.exp(-1.0D / EVAPORATING_TICKS);
    }

    /** How far from its heart a black hole tears the ground loose: two thirds of how far it pulls. */
    public static double holeBlockReach(double density) {
        return density < BLACK_HOLE ? 0.0D : wellReach(density) * 2.0D / 3.0D;
    }

    /**
     * Whether the pull at {@code distance} tears loose a block of {@code hardness}: the tide of it falls with the square
     * of the distance, and the harder the block the closer it must be (loose soil goes first, obsidian only at the very
     * heart).
     */
    public static boolean tears(double density, double hardness, double distance) {
        return hardness >= 0.0D && hardness * distance * distance <= density * 0.05D;
    }

    /**
     * How hard a black hole pulls at {@code distance}, in blocks per tick gained each tick: faint at the edge of its
     * reach and growing with the inverse square as things fall closer.
     */
    public static double holePull(double density, double distance) {
        double reach = wellReach(density);
        return Math.min(0.6D, 0.02D * (reach / Math.max(0.5D, distance)) * (reach / Math.max(0.5D, distance)));
    }

    /** What is left of a black hole {@code ticks} after it formed: it evaporates, much faster than a well relaxes. */
    public static double evaporated(double density, double ticks) {
        if (density <= OBSIDIAN || ticks <= 0.0D) {
            return density;
        }
        return OBSIDIAN + (density - OBSIDIAN) * Math.exp(-ticks / EVAPORATING_TICKS);
    }

    /** How big a black hole looks, in blocks across: it grows slowly with its mass, from a third of a block. */
    public static float horizon(double density) {
        if (density < BLACK_HOLE) {
            return 0.0F;
        }
        return (float) (0.35D + 0.15D * Math.log10(density / BLACK_HOLE));
    }

    /** How many blocks a black hole tears loose each time it feeds: more the heavier it is. */
    public static int swallows(double density) {
        if (density < BLACK_HOLE) {
            return 0;
        }
        return (int) (2L + Math.round(Math.log10(density / BLACK_HOLE)));
    }

    /** How far a well of gravity pulls, in blocks: the more mass beyond obsidian, the farther, up to twelve. */
    public static double wellReach(double density) {
        double excess = density - OBSIDIAN;
        if (excess <= 0.0D) {
            return 0.0D;
        }
        if (density >= BLACK_HOLE) {
            // A black hole reaches as far as its mass does, growing slowly (ten times heavier, three blocks farther).
            return 2.0D + 3.0D * Math.log10(density / OBSIDIAN);
        }
        return Math.min(12.0D, 2.0D + Math.sqrt(excess));
    }

    /**
     * How hard a well pulls at {@code distance}, in blocks per tick gained each tick: stronger the more mass beyond
     * obsidian and the closer, never more than a strong pull.
     */
    public static double wellPull(double density, double distance) {
        double excess = density - OBSIDIAN;
        if (excess <= 0.0D) {
            return 0.0D;
        }
        return Math.min(0.25D, 0.004D * excess / Math.max(1.0D, distance * distance));
    }

    /** How strong the crater of condensed earth falling like a meteor is: it grows slowly with its mass. */
    public static float meteor(double density) {
        if (density <= 148.0D) {
            return (float) Math.max(1.0D, 1.0D + Math.log(Math.max(1.0D, density)));
        }
        return (float) (6.0D + 2.0D * Math.log10(density / 148.0D));
    }

    /** Diamonds carbon condensed makes: none unless it was pressed hard enough; one for every nine coal. */
    public static int diamonds(int coal, double density) {
        if (coal <= 0 || density < DIAMOND_PRESSURE) {
            return 0;
        }
        return coal / COAL_PER_DIAMOND;
    }
}
