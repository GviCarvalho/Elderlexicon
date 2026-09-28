package com.elderlexicon.mod.spell.circle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the shape of a circle of scrolls or inscribed blocks laid on one plane (docs/circulos-design.md): which pieces
 * belong together, and which ring each is in. Any shape is a circle: a square, an ellipse, a cross, a crooked loop,
 * with or without a heart. The rings are found by peeling it like an onion: the pieces on the outline that wraps them
 * all (a band stretched round them) are the outermost ring; the same again with what is left is the next ring in; what
 * is left at the end is the heart. The spirit reads them from the heart out.
 */
public final class CircleShape {

    /** A piece's place on the circle's plane, in blocks. */
    public record Spot(int x, int y) {
    }

    /** Pieces this far apart or closer (in blocks, either way) are of the same circle. */
    public static final int GAP = 3;
    /** A piece within this distance of the outline is on it (a circle drawn in blocks is never perfectly round). */
    static final double ON_OUTLINE = 0.5D;

    private CircleShape() {
    }

    /** The circles among {@code spots}: groups of pieces each within {@link #GAP} of another of its group. */
    public static List<List<Integer>> groups(List<Spot> spots) {
        List<List<Integer>> groups = new ArrayList<>();
        boolean[] seen = new boolean[spots.size()];
        for (int start = 0; start < spots.size(); start++) {
            if (seen[start]) {
                continue;
            }
            List<Integer> group = new ArrayList<>();
            List<Integer> queue = new ArrayList<>();
            queue.add(start);
            seen[start] = true;
            while (!queue.isEmpty()) {
                int at = queue.remove(queue.size() - 1);
                group.add(at);
                for (int other = 0; other < spots.size(); other++) {
                    if (!seen[other] && near(spots.get(at), spots.get(other))) {
                        seen[other] = true;
                        queue.add(other);
                    }
                }
            }
            Collections.sort(group);
            groups.add(group);
        }
        return groups;
    }

    private static boolean near(Spot a, Spot b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y())) <= GAP;
    }

    /** The rings of one circle, from the heart out; each ring holds the indices of its pieces in {@code spots}. */
    public static List<List<Integer>> rings(List<Spot> spots) {
        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            remaining.add(i);
        }
        List<List<Integer>> layers = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<Integer> hull = hull(spots, remaining);
            List<Integer> layer = new ArrayList<>();
            if (hull.size() < 3) {
                layer.addAll(remaining); // a line or a point: all of it is its own outline
            } else {
                for (int index : remaining) {
                    if (onOutline(spots, hull, spots.get(index))) {
                        layer.add(index);
                    }
                }
            }
            remaining.removeAll(layer);
            layers.add(layer);
        }
        Collections.reverse(layers);
        return layers;
    }

    /** The corners of the outline wrapping {@code indices} (Andrew's monotone chain), in order round it. */
    private static List<Integer> hull(List<Spot> spots, List<Integer> indices) {
        List<Integer> sorted = new ArrayList<>(indices);
        sorted.sort((a, b) -> {
            Spot p = spots.get(a);
            Spot q = spots.get(b);
            return p.x() != q.x() ? Integer.compare(p.x(), q.x()) : Integer.compare(p.y(), q.y());
        });
        if (sorted.size() < 3) {
            return sorted;
        }
        List<Integer> lower = new ArrayList<>();
        for (int index : sorted) {
            while (lower.size() >= 2 && cross(spots, lower.get(lower.size() - 2), lower.get(lower.size() - 1), index) <= 0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(index);
        }
        List<Integer> upper = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            int index = sorted.get(i);
            while (upper.size() >= 2 && cross(spots, upper.get(upper.size() - 2), upper.get(upper.size() - 1), index) <= 0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(index);
        }
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower;
    }

    private static long cross(List<Spot> spots, int o, int a, int b) {
        Spot so = spots.get(o);
        Spot sa = spots.get(a);
        Spot sb = spots.get(b);
        return (long) (sa.x() - so.x()) * (sb.y() - so.y()) - (long) (sa.y() - so.y()) * (sb.x() - so.x());
    }

    private static boolean onOutline(List<Spot> spots, List<Integer> hull, Spot point) {
        for (int i = 0; i < hull.size(); i++) {
            Spot a = spots.get(hull.get(i));
            Spot b = spots.get(hull.get((i + 1) % hull.size()));
            if (distanceToSegment(point, a, b) <= ON_OUTLINE) {
                return true;
            }
        }
        return false;
    }

    private static double distanceToSegment(Spot p, Spot a, Spot b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = dx * dx + dy * dy;
        double t = length == 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy)
                / length));
        double x = a.x() + t * dx - p.x();
        double y = a.y() + t * dy - p.y();
        return Math.sqrt(x * x + y * y);
    }
}
