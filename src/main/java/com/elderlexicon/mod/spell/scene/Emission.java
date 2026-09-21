package com.elderlexicon.mod.spell.scene;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Something a spell puts into the world for a short time: a beam segment (Iactare) or a point
 * (Vocant). It carries energy in UMU, the {@link ElementProperties} of its element and the id of
 * the caster who paid for it, so that whatever it causes can be credited to them.
 */
public final class Emission {

    /** Caster used when an emission does not belong to anybody. */
    public static final UUID NO_CASTER = new UUID(0L, 0L);

    public record Point(double x, double y, double z) {

        public double distanceTo(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    public enum Kind { BEAM, POINT }

    /** Spacing between sampled points used for overlap tests. */
    static final double SAMPLE_SPACING = 0.5D;
    /** Edge of the cells of the spatial grid the scene uses to find neighbours. */
    static final double CELL_SIZE = 8.0D;

    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private final int id;
    private final int spellId;
    private final UUID caster;
    private final Kind kind;
    private final String elementRuneId;
    private final ElementProperties properties;
    private final double energy;
    private final double radius;
    private final long startTick;
    private final int lifetimeTicks;
    private final List<Point> samples;
    private final long[] cells;
    private double remainingEnergy;

    private Emission(int spellId, UUID caster, Kind kind, String elementRuneId, ElementProperties properties,
                     double energy, double radius, long startTick, int lifetimeTicks, List<Point> samples) {
        this.id = NEXT_ID.incrementAndGet();
        this.spellId = spellId;
        this.caster = caster == null ? NO_CASTER : caster;
        this.kind = kind;
        this.elementRuneId = elementRuneId;
        this.properties = Objects.requireNonNull(properties, "properties");
        this.energy = Math.max(0.0D, energy);
        this.remainingEnergy = this.energy;
        this.radius = Math.max(0.0D, radius);
        this.startTick = startTick;
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
        this.samples = List.copyOf(samples);
        this.cells = computeCells(this.samples, this.radius);
    }

    /** A straight beam from {@code origin} along the direction, {@code length} blocks long. */
    public static Emission beam(int spellId, String elementRuneId, ElementProperties properties, double energy,
                                Point origin, double dirX, double dirY, double dirZ, double length,
                                double radius, long startTick, int lifetimeTicks) {
        return beam(spellId, NO_CASTER, elementRuneId, properties, energy, origin, dirX, dirY, dirZ, length,
                radius, startTick, lifetimeTicks);
    }

    public static Emission beam(int spellId, UUID caster, String elementRuneId, ElementProperties properties,
                                double energy, Point origin, double dirX, double dirY, double dirZ, double length,
                                double radius, long startTick, int lifetimeTicks) {
        double norm = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (norm < 1.0E-9D) {
            return point(spellId, caster, elementRuneId, properties, energy, origin, radius, startTick, lifetimeTicks);
        }
        double ux = dirX / norm;
        double uy = dirY / norm;
        double uz = dirZ / norm;
        double safeLength = Math.max(0.0D, length);
        int steps = Math.max(1, (int) Math.ceil(safeLength / SAMPLE_SPACING));
        List<Point> samples = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double d = safeLength * i / steps;
            samples.add(new Point(origin.x() + ux * d, origin.y() + uy * d, origin.z() + uz * d));
        }
        return new Emission(spellId, caster, Kind.BEAM, elementRuneId, properties, energy, radius, startTick,
                lifetimeTicks, samples);
    }

    public static Emission point(int spellId, String elementRuneId, ElementProperties properties, double energy,
                                 Point center, double radius, long startTick, int lifetimeTicks) {
        return point(spellId, NO_CASTER, elementRuneId, properties, energy, center, radius, startTick, lifetimeTicks);
    }

    public static Emission point(int spellId, UUID caster, String elementRuneId, ElementProperties properties,
                                 double energy, Point center, double radius, long startTick, int lifetimeTicks) {
        return new Emission(spellId, caster, Kind.POINT, elementRuneId, properties, energy, radius, startTick,
                lifetimeTicks, List.of(center));
    }

    /**
     * Keys of every grid cell the emission (inflated by its radius) touches. Two emissions that come
     * within reach of each other always share at least one cell.
     */
    private static long[] computeCells(List<Point> samples, double radius) {
        Set<Long> keys = new HashSet<>();
        for (Point p : samples) {
            int x0 = cell(p.x() - radius);
            int x1 = cell(p.x() + radius);
            int y0 = cell(p.y() - radius);
            int y1 = cell(p.y() + radius);
            int z0 = cell(p.z() - radius);
            int z1 = cell(p.z() + radius);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        keys.add(cellKey(x, y, z));
                    }
                }
            }
        }
        long[] result = new long[keys.size()];
        int i = 0;
        for (long key : keys) {
            result[i++] = key;
        }
        return result;
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL) << 42 | ((long) y & 0x1FFFFFL) << 21 | ((long) z & 0x1FFFFFL);
    }

    public boolean activeAt(long tick) {
        return tick >= startTick && tick < startTick + lifetimeTicks;
    }

    /** True once the emission has run its course and can be dropped. */
    public boolean expiredAt(long tick) {
        return tick >= startTick + lifetimeTicks;
    }

    /** True when the two emissions come within the sum of their radii of each other. */
    public boolean overlaps(Emission other) {
        double reach = radius + other.radius;
        for (Point a : samples) {
            for (Point b : other.samples) {
                if (a.distanceTo(b) <= reach) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Stretch of space where two emissions touch; {@code from} is the end nearest to this emission's origin. */
    public record Extent(Point from, Point to) {

        public Point center() {
            return new Point((from.x() + to.x()) / 2.0D, (from.y() + to.y()) / 2.0D, (from.z() + to.z()) / 2.0D);
        }

        public double length() {
            return from.distanceTo(to);
        }
    }

    /**
     * Where this emission and {@code other} touch, walking along this emission from its origin, or
     * empty when they never come within the sum of their radii. Two beams that leave the same
     * point in the same direction touch along their whole length.
     */
    public Optional<Extent> overlapExtent(Emission other) {
        double reach = radius + other.radius;
        Point first = null;
        Point last = null;
        for (Point a : samples) {
            Point nearest = null;
            double best = Double.MAX_VALUE;
            for (Point b : other.samples) {
                double distance = a.distanceTo(b);
                if (distance < best) {
                    best = distance;
                    nearest = b;
                }
            }
            if (nearest == null || best > reach) {
                continue;
            }
            Point mid = new Point((a.x() + nearest.x()) / 2.0D, (a.y() + nearest.y()) / 2.0D, (a.z() + nearest.z()) / 2.0D);
            if (first == null) {
                first = mid;
            }
            last = mid;
        }
        return first == null ? Optional.empty() : Optional.of(new Extent(first, last));
    }

    /** Energy not yet turned into something else by the laws of the scene. */
    public double remainingEnergy() {
        return remainingEnergy;
    }

    /** Takes up to {@code amount} UMU out of this emission; returns what was really taken. */
    public double consume(double amount) {
        double taken = Math.max(0.0D, Math.min(amount, remainingEnergy));
        remainingEnergy -= taken;
        return taken;
    }

    public int id() {
        return id;
    }

    public int spellId() {
        return spellId;
    }

    public UUID caster() {
        return caster;
    }

    public Kind kind() {
        return kind;
    }

    public String elementRuneId() {
        return elementRuneId;
    }

    public ElementProperties properties() {
        return properties;
    }

    public double energy() {
        return energy;
    }

    public double radius() {
        return radius;
    }

    public long startTick() {
        return startTick;
    }

    public int lifetimeTicks() {
        return lifetimeTicks;
    }

    public List<Point> samples() {
        return samples;
    }

    long[] cells() {
        return cells;
    }
}
