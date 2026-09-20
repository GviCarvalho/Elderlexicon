package com.elderlexicon.mod.vita.damage;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple helper that logs the first N Vita damage mapping samples per source for validation.
 */
public final class DamageTelemetry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SAMPLES_PER_SOURCE = 100;
    private static final Map<ResourceLocation, Integer> SAMPLE_COUNTS = new ConcurrentHashMap<>();

    private DamageTelemetry() {
    }

    public static void record(ResourceLocation sourceId, double rawDamage, double vitaDelta) {
        if (sourceId == null) {
            return;
        }
        int count = SAMPLE_COUNTS.merge(sourceId, 1, Integer::sum);
        if (count <= MAX_SAMPLES_PER_SOURCE) {
            LOGGER.info("[VitaDamage] [{}] sample #{} rawDamage={} vitaDelta={}",
                    sourceId,
                    count,
                    rawDamage,
                    vitaDelta);
        }
    }

    public static Map<ResourceLocation, Integer> snapshot() {
        Map<ResourceLocation, Integer> ordered = new LinkedHashMap<>();
        SAMPLE_COUNTS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    public static void reset() {
        SAMPLE_COUNTS.clear();
    }
}
