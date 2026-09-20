package com.elderlexicon.mod.vita.damage;

import com.elderlexicon.mod.ExampleMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Loads and watches the Vita damage mapping multiplier config so balance tweaks can be data-driven.
 */
public final class DamageMappingConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, Double> DEFAULT_MULTIPLIERS = Map.ofEntries(
            Map.entry("drownAura", 1.0D),
            Map.entry("poisonFirmo", 1.0D),
            Map.entry("witherFirmo", 1.0D),
            Map.entry("witherAura", 1.0D),
            Map.entry("fireAqua", 1.0D),
            Map.entry("fireIgni", 1.0D),
            Map.entry("freezeIgni", 1.0D),
            Map.entry("lightningAura", 1.0D),
            Map.entry("soulHeatIgni", 1.0D)
    );
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicBoolean WATCHING = new AtomicBoolean(false);
    private static final Map<String, Double> CONFIG_MULTIPLIERS = new ConcurrentHashMap<>();
    private static final Map<String, Double> DATAPACK_MULTIPLIERS = new ConcurrentHashMap<>();
    private static final String FILE_NAME = "damage-mapping.json";

    private static WatchService watchService;
    private static Thread watcherThread;

    private DamageMappingConfig() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        reload();
        startWatcher();
    }

    public static int reload() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                writeDefault(path);
            }
            Map<String, Double> fresh = readFile(path);
            CONFIG_MULTIPLIERS.clear();
            CONFIG_MULTIPLIERS.putAll(fresh);
            LOGGER.info("[VitaDamage] Loaded {} multiplier entries from {}", CONFIG_MULTIPLIERS.size(), path);
        } catch (IOException e) {
            LOGGER.error("[VitaDamage] Failed to load config {}", path, e);
        }
        return CONFIG_MULTIPLIERS.size();
    }

    public static double getMultiplier(String key) {
        if (key == null || key.isBlank()) {
            return 1.0D;
        }
        Double datapack = DATAPACK_MULTIPLIERS.get(key);
        if (datapack != null) {
            return datapack;
        }
        return CONFIG_MULTIPLIERS.getOrDefault(key, DEFAULT_MULTIPLIERS.getOrDefault(key, 1.0D));
    }

    public static Map<String, Double> snapshot() {
        Map<String, Double> snapshot = new LinkedHashMap<>(DEFAULT_MULTIPLIERS);
        CONFIG_MULTIPLIERS.forEach(snapshot::put);
        DATAPACK_MULTIPLIERS.forEach(snapshot::put);
        return Collections.unmodifiableMap(snapshot);
    }

    public static int configCount() {
        return CONFIG_MULTIPLIERS.size();
    }

    public static int datapackCount() {
        return DATAPACK_MULTIPLIERS.size();
    }

    public static void replaceDatapackOverrides(Map<String, Double> overrides, ResourceLocation source) {
        DATAPACK_MULTIPLIERS.clear();
        DATAPACK_MULTIPLIERS.putAll(overrides);
        LOGGER.info("[VitaDamage] Datapack overrides applied from {} ({} entries)", source, DATAPACK_MULTIPLIERS.size());
    }

    private static void startWatcher() {
        if (WATCHING.getAndSet(true)) {
            return;
        }
        watcherThread = new Thread(DamageMappingConfig::watchLoop, "VitaDamageConfigWatcher");
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
                    if (changed != null && changed.getFileName().toString().equals(FILE_NAME)) {
                        try {
                            Thread.sleep(150L); // wait for editors to flush the file
                        } catch (InterruptedException ignored) {
                        }
                        LOGGER.info("[VitaDamage] Detected change to {}, reloading...", FILE_NAME);
                        reload();
                    }
                }
                if (!key.reset()) {
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("[VitaDamage] Config watch loop terminated", e);
        }
    }

    private static Path getConfigPath() {
        Path configDir = FMLPaths.CONFIGDIR.get()
                .resolve(ExampleMod.MODID)
                .resolve("vita");
        return configDir.resolve(FILE_NAME);
    }

    private static Map<String, Double> readFile(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("multipliers")) {
                return new LinkedHashMap<>(DEFAULT_MULTIPLIERS);
            }
            JsonObject multipliers = root.getAsJsonObject("multipliers");
            Map<String, Double> values = new LinkedHashMap<>();
            multipliers.entrySet().forEach(entry -> {
                double value = entry.getValue().getAsDouble();
                values.put(entry.getKey(), value);
            });
            return values;
        }
    }

    public static boolean setMultiplier(String key, double value, boolean persist) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim();
        CONFIG_MULTIPLIERS.put(normalized, value);
        LOGGER.info("[VitaDamage] Updated multiplier {} -> {} (persist={})", normalized, value, persist);
        if (persist) {
            persistConfig();
        }
        return true;
    }

    public static void persistConfig() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            writeConfig(path, CONFIG_MULTIPLIERS);
            LOGGER.info("[VitaDamage] Persisted damage-mapping config to {}", path);
        } catch (IOException e) {
            LOGGER.error("[VitaDamage] Failed to persist config {}", path, e);
        }
    }

    private static void writeDefault(Path path) throws IOException {
        writeConfig(path, DEFAULT_MULTIPLIERS);
        LOGGER.info("[VitaDamage] Wrote default damage-mapping config to {}", path);
    }

    private static void writeConfig(Path path, Map<String, Double> values) throws IOException {
        DamageMappingPayload payload = new DamageMappingPayload();
        payload.multipliers.putAll(values);
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            GSON.toJson(payload, writer);
        }
    }

    private static final class DamageMappingPayload {
        private final Map<String, Double> multipliers = new LinkedHashMap<>();
    }
}
