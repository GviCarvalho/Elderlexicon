package com.elderlexicon.mod.spell.circle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CircleShapeTest {

    private static List<CircleShape.Spot> grid(int width, int height, boolean hollow) {
        List<CircleShape.Spot> spots = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (!hollow || edge) {
                    spots.add(new CircleShape.Spot(x, y));
                }
            }
        }
        return spots;
    }

    private static List<Integer> sizes(List<List<Integer>> rings) {
        return rings.stream().map(List::size).toList();
    }

    @Test
    void aCrossIsItsHeartThenItsArms() {
        List<CircleShape.Spot> cross = List.of(new CircleShape.Spot(0, 0), new CircleShape.Spot(1, 0),
                new CircleShape.Spot(-1, 0), new CircleShape.Spot(0, 1), new CircleShape.Spot(0, -1));
        List<List<Integer>> rings = CircleShape.rings(cross);
        assertEquals(List.of(1, 4), sizes(rings));
        assertEquals(List.of(0), rings.get(0), "the heart is read first");
    }

    @Test
    void gridsArePeeledFromTheOutside() {
        assertEquals(List.of(1, 8), sizes(CircleShape.rings(grid(3, 3, false))), "3 x 3");
        assertEquals(List.of(2, 10), sizes(CircleShape.rings(grid(4, 3, false))), "4 x 3: the two in the middle");
        assertEquals(List.of(4, 12), sizes(CircleShape.rings(grid(4, 4, false))), "4 x 4: the four in the middle");
    }

    @Test
    void aHollowRingIsOneRingAndAHeartInsideItComesFirst() {
        assertEquals(List.of(12), sizes(CircleShape.rings(grid(4, 4, true))));
        List<CircleShape.Spot> withHeart = new ArrayList<>(grid(5, 5, true));
        withHeart.add(new CircleShape.Spot(2, 2));
        assertEquals(List.of(1, 16), sizes(CircleShape.rings(withHeart)));
    }

    @Test
    void ringsInsideRingsAreReadFromTheHeartOut() {
        List<CircleShape.Spot> nested = new ArrayList<>(grid(7, 7, true));
        for (CircleShape.Spot spot : grid(3, 3, true)) {
            nested.add(new CircleShape.Spot(spot.x() + 2, spot.y() + 2));
        }
        nested.add(new CircleShape.Spot(3, 3));
        assertEquals(List.of(1, 8, 24), sizes(CircleShape.rings(nested)));
    }

    @Test
    void aRoundCircleDrawnInBlocksIsStillOneRing() {
        List<CircleShape.Spot> round = new ArrayList<>();
        for (int a = 0; a < 360; a += 15) {
            int x = (int) Math.round(6 * Math.cos(Math.toRadians(a)));
            int y = (int) Math.round(4 * Math.sin(Math.toRadians(a)));
            CircleShape.Spot spot = new CircleShape.Spot(x, y);
            if (!round.contains(spot)) {
                round.add(spot);
            }
        }
        int ellipse = round.size();
        round.add(new CircleShape.Spot(0, 0));
        assertEquals(List.of(1, ellipse), sizes(CircleShape.rings(round)), "an ellipse with its heart");
    }

    @Test
    void separateCirclesAreToldApart() {
        List<CircleShape.Spot> spots = new ArrayList<>(grid(3, 3, false));
        for (CircleShape.Spot spot : grid(3, 3, false)) {
            spots.add(new CircleShape.Spot(spot.x() + 20, spot.y()));
        }
        assertEquals(2, CircleShape.groups(spots).size());
    }

    @Test
    void theSteamChainIsReadInSequence() {
        // heart: "A N SQ J" (released at column 3); ring: four "C N UQ O Q J" (released at column 5)
        java.util.List<java.util.List<java.util.List<Integer>>> steps = CircleTiming.schedule(List.of(
                List.of(List.of(3)),
                List.of(List.of(5), List.of(5), List.of(5), List.of(5))));
        assertEquals(List.of(3), steps.get(0).get(0), "the water first");
        for (java.util.List<Integer> piece : steps.get(1)) {
            assertEquals(List.of(9), piece, "then the four fires together, once the heart is done");
        }
    }

    @Test
    void aPageOfSeveralLinesKeepsItsOwnTimingInsideItsRing() {
        // heart: one line released at 1; ring: a page with lines released at 0 and 2, and a page released at 4
        java.util.List<java.util.List<java.util.List<Integer>>> steps = CircleTiming.schedule(List.of(
                List.of(List.of(1)),
                List.of(List.of(0, 2), List.of(4))));
        assertEquals(List.of(4, 6), steps.get(1).get(0), "the shorter page begins later and ends with the longer");
        assertEquals(List.of(6), steps.get(1).get(1));
    }

    @Test
    void piecesOfARingAreReleasedTogetherAndRingsFollowEachOther() {
        // heart: a page 3 steps long; ring: pages 2 and 4 steps long
        List<List<Integer>> starts = CircleTiming.starts(List.of(List.of(3), List.of(2, 4)));
        assertEquals(List.of(0), starts.get(0));
        assertEquals(List.of(5, 3), starts.get(1), "the shorter page begins later, so both end at step 7");
        assertEquals(2.0D, CircleTiming.distanceFactor(64.0D), 1.0E-9);
    }
}
