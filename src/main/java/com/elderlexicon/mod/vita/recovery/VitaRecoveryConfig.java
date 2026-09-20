package com.elderlexicon.mod.vita.recovery;

import com.elderlexicon.mod.ExampleMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Loads the Vita recovery tuning file (config/elderlexicon/vita/recovery.json).
 */
public final class VitaRecoveryConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicBoolean WATCHING = new AtomicBoolean(false);
    private static RecoverySettings SETTINGS = RecoverySettings.defaults();
    private static WatchService watchService;
    private static Thread watcherThread;

    private VitaRecoveryConfig() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        reload();
        startWatcher();
    }

    public static RecoverySettings settings() {
        return SETTINGS;
    }

    public static void reload() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                writeDefault(path);
            }
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                SETTINGS = RecoverySettings.fromJson(root);
            }
            LOGGER.info("[VitaRecovery] Loaded recovery settings from {}", path);
        } catch (IOException e) {
            LOGGER.error("[VitaRecovery] Failed loading recovery config {}", path, e);
            SETTINGS = RecoverySettings.defaults();
        }
    }

    private static void startWatcher() {
        if (WATCHING.getAndSet(true)) {
            return;
        }
        watcherThread = new Thread(VitaRecoveryConfig::watchLoop, "VitaRecoveryConfigWatcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private static void watchLoop() {
        Path directory = getConfigPath().getParent();
        try {
            Files.createDirectories(directory);
            watchService = FileSystems.getDefault().newWatchService();
            directory.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            while (WATCHING.get()) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = (Path) event.context();
                    if (changed != null && changed.getFileName().toString().equals("recovery.json")) {
                        try {
                            Thread.sleep(150L);
                        } catch (InterruptedException ignored) {
                        }
                        LOGGER.info("[VitaRecovery] Detected recovery.json change, reloading...");
                        reload();
                    }
                }
                if (!key.reset()) {
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("[VitaRecovery] Config watcher stopped", e);
        }
    }

    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(ExampleMod.MODID)
                .resolve("vita")
                .resolve("recovery.json");
    }

    private static void writeDefault(Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            GSON.toJson(RecoverySettings.defaults().toJson(), writer);
        }
        LOGGER.info("[VitaRecovery] Wrote default recovery.json to {}", path);
    }

    public record RecoverySettings(
            double aquaLowPercent,
            double aquaLowMultiplier,
            int drinkCooldownTicks,
            double aquaHighPercent,
            double aquaHighMultiplier,
            int sunlightExposureTicks,
            int sunlightCadenceTicks,
            double igniLowPercent,
            double igniLowMultiplier,
            int heatRadius,
            int heatProximityTicks,
            int heatCadenceTicks,
            double igniHighPercent,
            double igniHighMultiplier,
            int waterExposureTicks,
            int waterCadenceTicks,
            double firmoFoodPercent,
            double firmoFoodMultiplier,
            int firmoFoodCooldownTicks,
            double firmoSleepPercentage,
            double auraStillPercent,
            double auraStillMultiplier,
            int stillnessTicksRequired,
            int stillnessCadenceTicks,
            double auraActionPercent,
            double auraActionMultiplier,
            int actionCooldownTicks,
            boolean feedbackMessages
    ) {

        private static RecoverySettings defaults() {
            return new RecoverySettings(
                    0.25D, 1.0D, 60,
                    0.25D, 1.0D, 40, 100,
                    0.25D, 1.0D, 3, 40, 40,
                    0.25D, 1.0D, 100, 100,
                    0.25D, 1.0D, 32,
                    1.0D,
                    0.25D, 1.0D, 40, 40,
                    0.10D, 1.0D, 40,
                    true
            );
        }

        private static RecoverySettings fromJson(JsonObject root) {
            if (root == null) {
                return defaults();
            }
            return new RecoverySettings(
                    getDouble(root, "aquaLowPercent", 0.25D),
                    getDouble(root, "aquaLowMultiplier", 1.0D),
                    getInt(root, "drinkCooldownTicks", 60),
                    getDouble(root, "aquaHighPercent", 0.25D),
                    getDouble(root, "aquaHighMultiplier", 1.0D),
                    getInt(root, "sunlightExposureTicks", 40),
                    getInt(root, "sunlightCadenceTicks", 100),
                    getDouble(root, "igniLowPercent", 0.25D),
                    getDouble(root, "igniLowMultiplier", 1.0D),
                    getInt(root, "heatRadius", 3),
                    getInt(root, "heatProximityTicks", 40),
                    getInt(root, "heatCadenceTicks", 40),
                    getDouble(root, "igniHighPercent", 0.25D),
                    getDouble(root, "igniHighMultiplier", 1.0D),
                    getInt(root, "waterExposureTicks", 100),
                    getInt(root, "waterCadenceTicks", 100),
                    getDouble(root, "firmoFoodPercent", 0.25D),
                    getDouble(root, "firmoFoodMultiplier", 1.0D),
                    getInt(root, "firmoFoodCooldownTicks", 32),
                    getDouble(root, "firmoSleepPercentage", 1.0D),
                    getDouble(root, "auraStillPercent", 0.25D),
                    getDouble(root, "auraStillMultiplier", 1.0D),
                    getInt(root, "stillnessTicksRequired", 40),
                    getInt(root, "stillnessCadenceTicks", 40),
                    getDouble(root, "auraActionPercent", 0.10D),
                    getDouble(root, "auraActionMultiplier", 1.0D),
                    getInt(root, "actionCooldownTicks", 40),
                    root.has("feedbackMessages") ? root.get("feedbackMessages").getAsBoolean() : true
            );
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("aquaLowPercent", aquaLowPercent);
            obj.addProperty("aquaLowMultiplier", aquaLowMultiplier);
            obj.addProperty("drinkCooldownTicks", drinkCooldownTicks);
            obj.addProperty("aquaHighPercent", aquaHighPercent);
            obj.addProperty("aquaHighMultiplier", aquaHighMultiplier);
            obj.addProperty("sunlightExposureTicks", sunlightExposureTicks);
            obj.addProperty("sunlightCadenceTicks", sunlightCadenceTicks);
            obj.addProperty("igniLowPercent", igniLowPercent);
            obj.addProperty("igniLowMultiplier", igniLowMultiplier);
            obj.addProperty("heatRadius", heatRadius);
            obj.addProperty("heatProximityTicks", heatProximityTicks);
            obj.addProperty("heatCadenceTicks", heatCadenceTicks);
            obj.addProperty("igniHighPercent", igniHighPercent);
            obj.addProperty("igniHighMultiplier", igniHighMultiplier);
            obj.addProperty("waterExposureTicks", waterExposureTicks);
            obj.addProperty("waterCadenceTicks", waterCadenceTicks);
            obj.addProperty("firmoFoodPercent", firmoFoodPercent);
            obj.addProperty("firmoFoodMultiplier", firmoFoodMultiplier);
            obj.addProperty("firmoFoodCooldownTicks", firmoFoodCooldownTicks);
            obj.addProperty("firmoSleepPercentage", firmoSleepPercentage);
            obj.addProperty("auraStillPercent", auraStillPercent);
            obj.addProperty("auraStillMultiplier", auraStillMultiplier);
            obj.addProperty("stillnessTicksRequired", stillnessTicksRequired);
            obj.addProperty("stillnessCadenceTicks", stillnessCadenceTicks);
            obj.addProperty("auraActionPercent", auraActionPercent);
            obj.addProperty("auraActionMultiplier", auraActionMultiplier);
            obj.addProperty("actionCooldownTicks", actionCooldownTicks);
            obj.addProperty("feedbackMessages", feedbackMessages);
            return obj;
        }
    }

    private static double getDouble(JsonObject root, String key, double defaultValue) {
        return root.has(key) ? root.get(key).getAsDouble() : defaultValue;
    }

    private static int getInt(JsonObject root, String key, int defaultValue) {
        return root.has(key) ? root.get(key).getAsInt() : defaultValue;
    }
}
