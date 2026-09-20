package com.elderlexicon.mod.spell.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Something a spell puts into the world for a short time: a beam segment (Iactare) or a point
 * (Vocant). It carries energy in UMU and the {@link ElementProperties} of its element.
 */
public final class Emission {

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

    private final int spellId;
    private final Kind kind;
    private final String elementRuneId;
    private final ElementProperties properties;
    private final double energy;
    private final double radius;
    private final long startTick;
    private final int lifetimeTicks;
    private final List<Point> samples;
    private double remainingEnergy;

    private Emission(int spellId, Kind kind, String elementRuneId, ElementProperties properties, double energy,
                     double radius, long startTick, int lifetimeTicks, List<Point> samples) {
        this.spellId = spellId;
        this.kind = kind;
        this.elementRuneId = elementRuneId;
        this.properties = Objects.requireNonNull(properties, "properties");
        this.energy = Math.max(0.0D, energy);
        this.remainingEnergy = this.energy;
        this.radius = Math.max(0.0D, radius);
        this.startTick = startTick;
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
        this.samples = List.copyOf(samples);
    }

    /** A straight beam from {@code origin} along the unit direction, {@code length} blocks long. */
    public static Emission beam(int spellId, String elementRuneId, ElementProperties properties, double energy,
                                Point origin, double dirX, double dirY, double dirZ, double length,
                                double radius, long startTick, int lifetimeTicks) {
        double norm = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (norm < 1.0E-9D) {
            return point(spellId, elementRuneId, properties, energy, origin, radius, startTick, lifetimeTicks);
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
        return new Emission(spellId, Kind.BEAM, elementRuneId, properties, energy, radius, startTick, lifetimeTicks, samples);
    }

    public static Emission point(int spellId, String elementRuneId, ElementProperties properties, double energy,
                                 Point center, double radius, long startTick, int lifetimeTicks) {
        return new Emission(spellId, Kind.POINT, elementRuneId, properties, energy, radius, startTick,
                lifetimeTicks, List.of(center));
    }

    public boolean activeAt(long tick) {
        return tick >= startTick && tick < startTick + lifetimeTicks;
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

    /**
     * Point halfway between the two closest samples of both emissions, or empty when they are
     * farther apart than their radii allow.
     */
    public Optional<Point> contactPoint(Emission other) {
        double best = Double.MAX_VALUE;
        Point bestA = null;
        Point bestB = null;
        for (Point a : samples) {
            for (Point b : other.samples) {
                double distance = a.distanceTo(b);
                if (distance < best) {
                    best = distance;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        if (bestA == null || best > radius + other.radius) {
            return Optional.empty();
        }
        return Optional.of(new Point(
                (bestA.x() + bestB.x()) / 2.0D, (bestA.y() + bestB.y()) / 2.0D, (bestA.z() + bestB.z()) / 2.0D));
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

    public int spellId() {
        return spellId;
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
}
