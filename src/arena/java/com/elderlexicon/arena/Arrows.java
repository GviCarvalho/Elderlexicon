package com.elderlexicon.arena;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * How often a skeleton's arrow strikes a caster (docs/feiticeiro-design.md, 3.15), as the GameTest
 * {@code howOftenAnArrowStrikes} measured it in the world ({@code arena-calibration/arrows-skeleton.json}, kept in
 * {@code src/arena/calibration}): by how the caster moved as it was loosed, and how far off; between the distances
 * measured, in proportion; nearer or farther, as the nearest.
 */
final class Arrows {

    /** How a caster moves as an arrow is loosed at it. */
    enum Motion {
        STILL, AWAY, TOWARD, SIDE;

        /**
         * The motion of one that moved ({@code dx}, {@code dz}) this tick, from where the arrow comes ({@code ax},
         * {@code az}: a unit step from the bow to it).
         */
        static Motion of(boolean walked, double dx, double dz, double ax, double az) {
            double length = Math.hypot(dx, dz);
            if (!walked || length < 1.0E-6D) {
                return STILL;
            }
            double along = (dx * ax + dz * az) / length;
            return along > 0.7D ? AWAY : along < -0.7D ? TOWARD : SIDE;
        }
    }

    private final double[] distances;
    private final Map<Motion, double[]> hit;
    private final String source;

    private Arrows(double[] distances, Map<Motion, double[]> hit, String source) {
        this.distances = distances;
        this.hit = hit;
        this.source = source;
    }

    /** How often an arrow loosed {@code distance} off strikes one moving so. */
    double hit(double distance, Motion motion) {
        double[] shares = hit.get(motion);
        if (distance <= distances[0]) {
            return shares[0];
        }
        for (int i = 1; i < distances.length; i++) {
            if (distance <= distances[i]) {
                double share = (distance - distances[i - 1]) / (distances[i] - distances[i - 1]);
                return shares[i - 1] + (shares[i] - shares[i - 1]) * share;
            }
        }
        return shares[shares.length - 1];
    }

    @Override
    public String toString() {
        return source;
    }

    /** What was measured, from {@code directory}; null when nothing was. */
    static Arrows of(Path directory) throws IOException {
        Path file = directory.resolve("arrows-skeleton.json");
        if (!Files.exists(file)) {
            return null;
        }
        JsonObject root = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
        double[] distances = new Gson().fromJson(root.get("distances"), double[].class);
        Map<Motion, double[]> hit = new EnumMap<>(Motion.class);
        JsonObject motions = root.getAsJsonObject("hit");
        for (Motion motion : Motion.values()) {
            JsonArray shares = motions.getAsJsonArray(motion.name().toLowerCase(java.util.Locale.ROOT));
            double[] values = new double[distances.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = shares.get(i).getAsDouble();
            }
            hit.put(motion, values);
        }
        return new Arrows(distances, hit, file.toString());
    }
}
