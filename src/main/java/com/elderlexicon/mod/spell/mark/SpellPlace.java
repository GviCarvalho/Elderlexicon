package com.elderlexicon.mod.spell.mark;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Where a function acts, as written with {@code ubis}: what comes right before {@code ubis} becomes a place
 * (book 4.3.2: "o Ubis captura a posição").
 * <pre>
 *   igni 10 ubis vocant          distance: 10 blocks ahead of the mage
 *   igni 10 5 ubis vocant        distance and height: 10 blocks ahead, 5 above the mage's feet
 *   igni 10 64 -30 ubis vocant   coordinates x=10, y=64, z=-30
 *   igni 10 m1 -30 ubis vocant   coordinates with y taken from where m1 is (the average of its members)
 *   m1 ubis igni vocant          where m1 is (the average of its members), at its feet
 * </pre>
 */
public record SpellPlace(Kind kind, double distance, double height, List<Axis> axes, String mark) {

    public enum Kind {
        DISTANCE,
        DISTANCE_HEIGHT,
        COORDINATES,
        MARK
    }

    /** One coordinate: a number, or the same coordinate of where a mark is. */
    public record Axis(Double value, String mark) {

        public Axis {
            if ((value == null) == (mark == null)) {
                throw new IllegalArgumentException("An axis is a number or a mark");
            }
        }

        public static Axis of(double value) {
            return new Axis(value, null);
        }

        public static Axis mark(String mark) {
            return new Axis(null, normalize(mark));
        }

        public boolean isMark() {
            return mark != null;
        }

        String describe() {
            return isMark() ? "'" + mark + "'" : format(value);
        }
    }

    public SpellPlace {
        Objects.requireNonNull(kind, "kind");
        axes = axes == null ? List.of() : List.copyOf(axes);
        if (kind == Kind.MARK && (mark == null || mark.isBlank())) {
            throw new IllegalArgumentException("A mark place needs a mark");
        }
        if (kind == Kind.COORDINATES && axes.size() != 3) {
            throw new IllegalArgumentException("Coordinates need three axes");
        }
    }

    public static SpellPlace distance(double distance) {
        return new SpellPlace(Kind.DISTANCE, distance, 0.0D, List.of(), null);
    }

    /** {@code distance} ahead along the ground and {@code height} above the mage's feet. */
    public static SpellPlace distanceAndHeight(double distance, double height) {
        return new SpellPlace(Kind.DISTANCE_HEIGHT, distance, height, List.of(), null);
    }

    public static SpellPlace coordinates(double x, double y, double z) {
        return coordinates(Axis.of(x), Axis.of(y), Axis.of(z));
    }

    public static SpellPlace coordinates(Axis x, Axis y, Axis z) {
        return new SpellPlace(Kind.COORDINATES, 0.0D, 0.0D, List.of(x, y, z), null);
    }

    public static SpellPlace mark(String mark) {
        return new SpellPlace(Kind.MARK, 0.0D, 0.0D, List.of(), normalize(mark));
    }

    /** Whether where it is depends on marked things, so it moves with them. */
    public boolean followsMarks() {
        return kind == Kind.MARK || axes.stream().anyMatch(Axis::isMark);
    }

    public String describe() {
        return switch (kind) {
            case DISTANCE -> format(distance) + " blocks ahead";
            case DISTANCE_HEIGHT -> format(distance) + " blocks ahead, " + format(height) + " up";
            case COORDINATES -> axes.get(0).describe() + " " + axes.get(1).describe() + " " + axes.get(2).describe();
            case MARK -> "'" + mark + "'";
        };
    }

    private static String normalize(String mark) {
        return mark.trim().toLowerCase(Locale.ROOT);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
