package com.elderlexicon.arena;

import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Option;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the elder's sentences really do to one kind of foe (docs/feiticeiro-design.md, 3.13), as the GameTests
 * {@code whatTheElderDoesTo...} measure it in the world and write it ({@code arena-calibration/damage-<foe>.json}, kept
 * in {@code src/arena/calibration}). A sentence measured does what was measured; one not measured does what the mind
 * foresaw, times what the measured ones of its intent and energy did over what was foreseen of them.
 */
final class Damage {

    /** What nothing was measured of: as the mind foresees it. */
    static final Damage FORESEEN = new Damage(Map.of(), Map.of(), Map.of(), "foreseen (nothing measured)");

    private final Map<String, Double> measured;
    /** Measured over foreseen, by intent and energy, and by intent alone. */
    private final Map<String, Double> byKind;
    private final Map<String, Double> byIntent;
    private final String source;

    private Damage(Map<String, Double> measured, Map<String, Double> byKind, Map<String, Double> byIntent,
                   String source) {
        this.measured = measured;
        this.byKind = byKind;
        this.byIntent = byIntent;
        this.source = source;
    }

    /** What {@code choice} does to the foe. */
    double harm(Mind.Choice choice) {
        Double known = measured.get(choice.option().name());
        if (known != null) {
            return known;
        }
        String intent = choice.intent().name();
        double factor = byKind.getOrDefault(intent + ":" + element(choice.option()),
                byIntent.getOrDefault(intent, 1.0D));
        return choice.harm() * factor;
    }

    int size() {
        return measured.size();
    }

    @Override
    public String toString() {
        return source;
    }

    private static String element(Option option) {
        return option.acts().isEmpty() ? "" : option.acts().get(0).element().name();
    }

    /** What was measured against {@code foe}, from {@code directory}; {@link #FORESEEN} when nothing was. */
    static Damage of(Path directory, String foe) throws IOException {
        Path file = directory.resolve("damage-" + foe + ".json");
        if (!Files.exists(file)) {
            return FORESEEN;
        }
        JsonObject root = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
        Map<String, Double> measured = new HashMap<>();
        Map<String, List<Double>> kinds = new HashMap<>();
        Map<String, List<Double>> intents = new HashMap<>();
        for (JsonElement element : root.getAsJsonArray("readings")) {
            JsonObject reading = element.getAsJsonObject();
            double done = reading.get("measured").getAsDouble();
            measured.put(reading.get("sentence").getAsString(), done);
            double foreseen = reading.get("arena").getAsDouble();
            String intent = reading.get("intent").getAsString();
            if (foreseen > 0.0D && !intent.isEmpty()) {
                kinds.computeIfAbsent(intent + ":" + reading.get("element").getAsString(), k -> new ArrayList<>())
                        .add(done / foreseen);
                intents.computeIfAbsent(intent, k -> new ArrayList<>()).add(done / foreseen);
            }
        }
        Map<String, Double> byKind = new HashMap<>();
        kinds.forEach((key, ratios) -> byKind.put(key, median(ratios)));
        Map<String, Double> byIntent = new HashMap<>();
        intents.forEach((key, ratios) -> byIntent.put(key, median(ratios)));
        return new Damage(measured, byKind, byIntent, file + " (" + measured.size() + " sentences)");
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0D;
    }
}
