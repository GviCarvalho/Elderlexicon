package com.elderlexicon.mod.vita.damage;

import com.elderlexicon.mod.ExampleMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads optional datapack multipliers from {@code data/<namespace>/vita/damage_mapping/*.json}.
 */
public final class DamageMappingDatapackLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ResourceLocation SOURCE_ID = new ResourceLocation(ExampleMod.MODID, "datapack");

    public DamageMappingDatapackLoader() {
        super(GSON, "vita/damage_mapping");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, Double> overrides = new LinkedHashMap<>();
        object.forEach((id, element) -> {
            JsonObject json = element.getAsJsonObject();
            if (!json.has("multipliers")) {
                return;
            }
            JsonObject multipliers = json.getAsJsonObject("multipliers");
            multipliers.entrySet().forEach(entry -> {
                try {
                    overrides.put(entry.getKey(), entry.getValue().getAsDouble());
                } catch (Exception e) {
                    LOGGER.warn("[VitaDamage] Failed parsing multiplier {} in {}", entry.getKey(), id, e);
                }
            });
        });
        DamageMappingConfig.replaceDatapackOverrides(overrides, SOURCE_ID);
    }
}
