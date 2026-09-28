package com.elderlexicon.mod.spell.circle;

import java.util.ArrayList;
import java.util.List;

/**
 * When each piece of a circle is read (docs/circulos-design.md). The rings are read one after the other, from the
 * heart out: a ring begins when the one inside it has been released. Within a ring the pieces adapt to each other so
 * that all of them are released in the same instant: a shorter page is begun later, so it ends with the longest.
 * Circle magic is for spells in a chain.
 */
public final class CircleTiming {

    private CircleTiming() {
    }

    /**
     * The step at which each piece begins, given how many steps each takes (its last column + 1), ring by ring from the
     * heart out. The result has the same shape as {@code lengths}.
     */
    public static List<List<Integer>> starts(List<List<Integer>> lengths) {
        List<List<Integer>> starts = new ArrayList<>();
        int ringStart = 0;
        for (List<Integer> ring : lengths) {
            int longest = 0;
            for (int length : ring) {
                longest = Math.max(longest, length);
            }
            List<Integer> ringStarts = new ArrayList<>();
            for (int length : ring) {
                ringStarts.add(ringStart + longest - length);
            }
            starts.add(ringStarts);
            ringStart += longest;
        }
        return starts;
    }

    /**
     * The step at which each line of each piece is released, given where each line of each piece is released on its
     * own page (the column of its last rune), ring by ring from the heart out, piece by piece. A piece takes as many
     * steps as its latest line + 1; an empty piece takes none.
     */
    public static List<List<List<Integer>>> schedule(List<List<List<Integer>>> releases) {
        List<List<Integer>> lengths = new ArrayList<>();
        for (List<List<Integer>> ring : releases) {
            List<Integer> ringLengths = new ArrayList<>();
            for (List<Integer> piece : ring) {
                int length = 0;
                for (int release : piece) {
                    length = Math.max(length, release + 1);
                }
                ringLengths.add(length);
            }
            lengths.add(ringLengths);
        }
        List<List<Integer>> starts = starts(lengths);
        List<List<List<Integer>>> steps = new ArrayList<>();
        for (int r = 0; r < releases.size(); r++) {
            List<List<Integer>> ring = new ArrayList<>();
            for (int p = 0; p < releases.get(r).size(); p++) {
                List<Integer> piece = new ArrayList<>();
                for (int release : releases.get(r).get(p)) {
                    piece.add(starts.get(r).get(p) + release);
                }
                ring.add(piece);
            }
            steps.add(ring);
        }
        return steps;
    }

    /** What reading a circle from {@code distance} blocks away by its mark costs, as a share of its own cost. */
    public static double distanceFactor(double distance) {
        return 1.0D + Math.max(0.0D, distance) / 64.0D;
    }
}
