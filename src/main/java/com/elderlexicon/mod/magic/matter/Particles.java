package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How many particles of each primordial something holds (docs/particulas-design.md, section 1). A block is 4096 voxels
 * (16³, the grid of its pixels) and each voxel is a particle of firmo, aqua, aura or igni; an item is one layer of them.
 * Every particle is worth the same and none is ever split, so whatever passes between two holders is kept to the last
 * particle (L1), and a count that would overflow is refused rather than wrapped.
 * <p>
 * A count may go below zero (section 3). Water has no igni to give, but it can owe it: the debt is the lack, and a lack
 * acts as the reverse of the particle (owed igni is cold). A debt and the same particles cancel when they meet. What a
 * thing is comes from what it holds ({@link #composition()}); what it owes only says how it is.
 *
 * @param firmo the particles of firmo, or the debt of them when below zero
 * @param aqua  the particles of aqua, or the debt of them
 * @param aura  the particles of aura, or the debt of them
 * @param igni  the particles of igni, or the debt of them
 */
public record Particles(long firmo, long aqua, long aura, long igni) {

    /** A block as nature fills it: 16³ voxels, one particle each. Fewer is rarefied, more is compressed. */
    public static final long BLOCK = 4096L;
    /** An item: one layer of a block, the 16×16 of its texture one pixel thick. Sixteen make a block. */
    public static final long ITEM = 256L;
    /** The particles in one UMU (decided 30/09/2026): an item is one UMU, and a block sixteen. */
    public static final long PER_UMU = 256L;
    /** The UMU a block as nature fills it holds. */
    public static final double BLOCK_UMU = (double) BLOCK / PER_UMU;
    /** Nothing held and nothing owed. */
    public static final Particles NONE = new Particles(0L, 0L, 0L, 0L);
    /** The four primordials, from the densest to the most rarefied, as the ladder of states has them. */
    public static final List<VitaElement> ASPECTS =
            List.of(VitaElement.FIRMO, VitaElement.AQUA, VitaElement.AURA, VitaElement.IGNI);

    /** {@code count} particles of one primordial, or a debt of them when negative. */
    public static Particles of(VitaElement aspect, long count) {
        return switch (aspect) {
            case FIRMO -> new Particles(count, 0L, 0L, 0L);
            case AQUA -> new Particles(0L, count, 0L, 0L);
            case AURA -> new Particles(0L, 0L, count, 0L);
            case IGNI -> new Particles(0L, 0L, 0L, count);
            default -> throw new IllegalArgumentException("vis is energy, no particle of matter");
        };
    }

    /**
     * A composition in whole particles that add up to exactly {@code total}: each primordial gets the whole part of its
     * share, and what is left goes one by one to the largest fractions (the denser primordial on a tie). Stone (terra
     * 80, água 5, ar 5, fogo 10) in a block is 3277 firmo, 205 aqua, 205 aura and 409 igni.
     */
    public static Particles in(Composition composition, long total) {
        if (total < 0L) {
            throw new IllegalArgumentException("a negative number of particles: " + total);
        }
        long[] counts = new long[ASPECTS.size()];
        double[] fractions = new double[ASPECTS.size()];
        long left = total;
        for (int i = 0; i < counts.length; i++) {
            double exact = composition.share(ASPECTS.get(i)) * total;
            counts[i] = (long) Math.floor(exact);
            fractions[i] = exact - counts[i];
            left -= counts[i];
        }
        for (; left > 0L; left--) {
            int largest = 0;
            for (int i = 1; i < counts.length; i++) {
                if (fractions[i] > fractions[largest]) {
                    largest = i;
                }
            }
            counts[largest]++;
            fractions[largest] = -1.0D;
        }
        return of(counts);
    }

    /** The whole particles nearest {@code umu}. */
    public static long ofUmu(double umu) {
        return Math.round(umu * PER_UMU);
    }

    /** How many UMU they are, what is held less what is owed. */
    public double umu() {
        return (double) total() / PER_UMU;
    }

    /** The particles of one primordial it holds, or the debt of them when below zero. */
    public long count(VitaElement aspect) {
        return switch (aspect) {
            case FIRMO -> firmo;
            case AQUA -> aqua;
            case AURA -> aura;
            case IGNI -> igni;
            default -> throw new IllegalArgumentException("vis is energy, no particle of matter");
        };
    }

    public Particles plus(Particles other) {
        return new Particles(Math.addExact(firmo, other.firmo), Math.addExact(aqua, other.aqua),
                Math.addExact(aura, other.aura), Math.addExact(igni, other.igni));
    }

    /** What is left when {@code other} is taken away; taking more than there is leaves a debt. */
    public Particles minus(Particles other) {
        return new Particles(Math.subtractExact(firmo, other.firmo), Math.subtractExact(aqua, other.aqua),
                Math.subtractExact(aura, other.aura), Math.subtractExact(igni, other.igni));
    }

    /** How many it holds, less what it owes. */
    public long total() {
        return Math.addExact(Math.addExact(firmo, aqua), Math.addExact(aura, igni));
    }

    /** Only what it holds, every debt left out. */
    public Particles present() {
        return new Particles(Math.max(0L, firmo), Math.max(0L, aqua), Math.max(0L, aura), Math.max(0L, igni));
    }

    /** Only what it owes, as the particles that would pay it off. */
    public Particles owed() {
        return new Particles(Math.max(0L, Math.negateExact(firmo)), Math.max(0L, Math.negateExact(aqua)),
                Math.max(0L, Math.negateExact(aura)), Math.max(0L, Math.negateExact(igni)));
    }

    /** Whether it owes any primordial. */
    public boolean owes() {
        return firmo < 0L || aqua < 0L || aura < 0L || igni < 0L;
    }

    /**
     * What it is made of: the share of each primordial among the particles it holds (a debt is no part of it). Empty
     * when it holds none.
     */
    public Optional<Composition> composition() {
        Map<VitaElement, Double> held = new EnumMap<>(VitaElement.class);
        for (VitaElement aspect : ASPECTS) {
            long count = count(aspect);
            if (count > 0L) {
                held.put(aspect, (double) count);
            }
        }
        return held.isEmpty() ? Optional.empty() : Optional.of(Composition.of(held));
    }

    /**
     * How full it is against a block as nature fills it: 1 is natural, less is rarefied (0 is a vacuum), more is
     * compressed. What it owes takes room away too, so water owing igni is as rarefied as stone that lost as much of
     * its own: cold contracts.
     */
    public double pressure() {
        return (double) total() / BLOCK;
    }

    /**
     * These particles in {@code parts} holders that add back up to exactly them, as even as whole particles allow: each
     * primordial is shared out, and what does not divide goes one each to the next holders in turn, so each holder has
     * as many of every primordial as another, and as many in all, give or take one. A block of stone is sixteen items
     * of stone.
     */
    public List<Particles> split(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("particles split into no parts");
        }
        long[][] shares = new long[parts][ASPECTS.size()];
        int next = 0;
        for (int a = 0; a < ASPECTS.size(); a++) {
            long count = count(ASPECTS.get(a));
            long base = Math.floorDiv(count, parts);
            long over = Math.floorMod(count, parts);
            for (int p = 0; p < parts; p++) {
                shares[p][a] = base;
            }
            for (long k = 0L; k < over; k++) {
                shares[next][a]++;
                next = (next + 1) % parts;
            }
        }
        List<Particles> split = new ArrayList<>(parts);
        for (long[] share : shares) {
            split.add(of(share));
        }
        return split;
    }

    private static Particles of(long[] counts) {
        return new Particles(counts[0], counts[1], counts[2], counts[3]);
    }
}
