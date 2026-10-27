package com.elderlexicon.mod.mind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What an old hand knows of fighting one kind of foe (docs/feiticeiro-design.md, 3.10): for each situation, what is
 * worth doing, a stance, learned over many fights in the arena (src/arena) and kept as a file
 * ({@code data/elderlexicon/doctrines/<foe>.json}). The mind still chooses the sentence: the doctrine says what for.
 * <p>
 * A situation is seen in coarse steps (how far, how soon the blow, how whole, how much Vis, how whole the foe), the
 * same in the arena and in the world: the steps are kept in the file with what was learned.
 */
public final class Doctrine {

    /** What it can do at a moment it is free to choose. */
    public enum Stance {
        STRIKE(Intent.STRIKE), DRAW(Intent.DRAW), WARD(Intent.WARD), FLEE(Intent.FLEE), HEAL(Intent.HEAL),
        VEIL(Intent.VEIL), BACK_OFF(null), CLOSE_IN(null), WAIT(null),
        /** It walks around the foe, aside of it: an arrow aimed where it stood seldom finds it (3.16). */
        CIRCLE(null);

        private final Intent intent;

        Stance(Intent intent) {
            this.intent = intent;
        }

        /** The intent it reads for; null when it moves or waits instead. */
        public Intent intent() {
            return intent;
        }
    }

    /**
     * What it sees at a moment: how far the foe stands, in how many trances its blow comes (infinite: it does not come),
     * its own share of life and of Vis, the foe's share of life, whether it is rested enough to read, and how much room
     * it has to give ground ({@link Room}, blocks).
     */
    public record Situation(double distance, double trancesToBlow, double health, double vis, double foeHealth,
                            boolean ready, double room) {
    }

    /**
     * The steps a situation is seen in: the edges between them, for each thing seen. A doctrine learned before room
     * was seen has no edges for it, and does not see it.
     */
    public record Steps(double[] distance, double[] trancesToBlow, double[] health, double[] vis, double[] foeHealth,
                        double[] room) {

        /** The steps doctrines are learned with: room is cornered, tight or open (3.12). */
        public static Steps standard() {
            return new Steps(new double[]{2.5D, 4.5D, 7.0D, 10.0D, 14.0D, 20.0D}, new double[]{0.5D, 1.0D, 1.5D},
                    new double[]{0.4D, 0.7D}, new double[]{0.15D, 0.4D}, new double[]{0.35D, 0.7D},
                    new double[]{Room.CORNERED + 1.5D, 8.0D});
        }

        /** The situation, in steps: its key. */
        public String key(Situation seen) {
            return step(distance, seen.distance()) + "." + step(trancesToBlow, seen.trancesToBlow()) + "."
                    + step(health, seen.health()) + "." + step(vis, seen.vis()) + "." + step(foeHealth, seen.foeHealth())
                    + "." + (seen.ready() ? 1 : 0) + (room.length == 0 ? "" : "." + step(room, seen.room()));
        }

        private static int step(double[] edges, double value) {
            int step = 0;
            while (step < edges.length && value >= edges[step]) {
                step++;
            }
            return step;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Optional<Doctrine>> LOADED = new ConcurrentHashMap<>();

    private final String foe;
    private final Steps steps;
    /** What each stance is worth in each situation it met, by key: in the order of {@link Stance}. */
    private final Map<String, double[]> worth;
    /** How it was learned (fights, how many were won), for the one who reads the file. */
    private final JsonObject trained;

    public Doctrine(String foe, Steps steps, Map<String, double[]> worth, JsonObject trained) {
        this.foe = foe;
        this.steps = steps;
        this.worth = worth;
        this.trained = trained;
    }

    public String foe() {
        return foe;
    }

    public Steps steps() {
        return steps;
    }

    /** What each stance is worth in {@code seen}; null when it never met it. */
    public double[] worth(Situation seen) {
        return worth.get(steps.key(seen));
    }

    /** The stance worth most in {@code seen}, among the ones it can take now; empty when it never met it. */
    public Optional<Stance> choose(Situation seen, Set<Stance> possible) {
        double[] values = worth(seen);
        if (values == null) {
            return Optional.empty();
        }
        Stance best = null;
        for (Stance stance : possible) {
            if (best == null || values[stance.ordinal()] > values[best.ordinal()]) {
                best = stance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Every stance. */
    public static Set<Stance> all() {
        return EnumSet.allOf(Stance.class);
    }

    /** How near a foe is at hand. */
    public static final double AT_HAND = 3.0D;

    /**
     * What is worth weighing of {@code possible}, where the doctrine says it is (a doctrine learned so says
     * {@code "sensible": true}): with a foe at hand and room to give ground, waiting or walking nearer never is. What a
     * fight seldom shows, a table learns badly; this it need not learn (3.13).
     */
    public static Set<Stance> sensible(Situation seen, Set<Stance> possible) {
        if (seen.distance() >= AT_HAND || seen.room() < Room.CORNERED) {
            return possible;
        }
        Set<Stance> left = EnumSet.noneOf(Stance.class);
        left.addAll(possible);
        left.remove(Stance.WAIT);
        left.remove(Stance.CLOSE_IN);
        return left.isEmpty() ? possible : left;
    }

    /** Whether this doctrine was learned weighing only what is {@link #sensible}: then it chooses so too. */
    public boolean learnedSensibly() {
        return trained.has("sensible") && trained.get("sensible").getAsBoolean();
    }

    /** The stance worth most in {@code seen}, among the ones it can take now and as it learned to weigh them. */
    public Optional<Stance> decide(Situation seen, Set<Stance> possible) {
        return choose(seen, learnedSensibly() ? sensible(seen, possible) : possible);
    }

    // ------------------------------------------------------------------ the file

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("foe", foe);
        JsonArray stances = new JsonArray();
        for (Stance stance : Stance.values()) {
            stances.add(stance.name());
        }
        root.add("stances", stances);
        JsonObject edges = new JsonObject();
        edges.add("distance", GSON.toJsonTree(steps.distance()));
        edges.add("trancesToBlow", GSON.toJsonTree(steps.trancesToBlow()));
        edges.add("health", GSON.toJsonTree(steps.health()));
        edges.add("vis", GSON.toJsonTree(steps.vis()));
        edges.add("foeHealth", GSON.toJsonTree(steps.foeHealth()));
        edges.add("room", GSON.toJsonTree(steps.room()));
        root.add("steps", edges);
        root.add("trained", trained);
        JsonObject values = new JsonObject();
        worth.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            JsonArray row = new JsonArray();
            for (double value : entry.getValue()) {
                row.add(Math.round(value * 1000.0D) / 1000.0D);
            }
            values.add(entry.getKey(), row);
        });
        root.add("worth", values);
        return GSON.toJson(root);
    }

    public static Doctrine fromJson(Reader reader) {
        JsonObject root = GSON.fromJson(reader, JsonObject.class);
        JsonObject edges = root.getAsJsonObject("steps");
        Steps steps = new Steps(GSON.fromJson(edges.get("distance"), double[].class),
                GSON.fromJson(edges.get("trancesToBlow"), double[].class),
                GSON.fromJson(edges.get("health"), double[].class), GSON.fromJson(edges.get("vis"), double[].class),
                GSON.fromJson(edges.get("foeHealth"), double[].class),
                edges.has("room") ? GSON.fromJson(edges.get("room"), double[].class) : new double[0]);
        // The stances as the file names them: one it does not know is left out, one it lacks is worth nothing.
        JsonArray names = root.getAsJsonArray("stances");
        int[] order = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            order[i] = -1;
            for (Stance stance : Stance.values()) {
                if (stance.name().equals(names.get(i).getAsString())) {
                    order[i] = stance.ordinal();
                }
            }
        }
        Map<String, double[]> worth = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("worth").entrySet()) {
            JsonArray row = entry.getValue().getAsJsonArray();
            double[] values = new double[Stance.values().length];
            java.util.Arrays.fill(values, -Double.MAX_VALUE);
            for (int i = 0; i < row.size() && i < order.length; i++) {
                if (order[i] >= 0) {
                    values[order[i]] = row.get(i).getAsDouble();
                }
            }
            worth.put(entry.getKey(), values);
        }
        JsonObject trained = root.has("trained") ? root.getAsJsonObject("trained") : new JsonObject();
        return new Doctrine(root.get("foe").getAsString(), steps, worth, trained);
    }

    /** The doctrine against {@code foe} the mod carries, if it carries one ({@code data/elderlexicon/doctrines}). */
    public static Optional<Doctrine> of(String foe) {
        return LOADED.computeIfAbsent(foe, name -> {
            try (InputStream in = Doctrine.class.getResourceAsStream("/data/elderlexicon/doctrines/" + name
                    + ".json")) {
                if (in == null) {
                    return Optional.empty();
                }
                return Optional.of(fromJson(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException exception) {
                return Optional.empty();
            }
        });
    }
}
