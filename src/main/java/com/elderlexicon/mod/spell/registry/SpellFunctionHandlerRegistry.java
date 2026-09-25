package com.elderlexicon.mod.spell.registry;

import com.elderlexicon.mod.spell.function.ExsugatFunctionHandler;
import com.elderlexicon.mod.spell.function.IactareFunctionHandler;
import com.elderlexicon.mod.spell.function.ImpediuntFunctionHandler;
import com.elderlexicon.mod.spell.function.LigabisFunctionHandler;
import com.elderlexicon.mod.spell.function.MarkVertereFunctionHandler;
import com.elderlexicon.mod.spell.function.ReframeFunctionHandler;
import com.elderlexicon.mod.spell.function.SpellFunctionHandler;
import com.elderlexicon.mod.spell.function.SurgitFunctionHandler;
import com.elderlexicon.mod.spell.function.TransvocatioFunctionHandler;
import com.elderlexicon.mod.spell.function.VocantFunctionHandler;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that exposes spell function handlers so mods can add, override or remove runtime behaviors.
 */
public final class SpellFunctionHandlerRegistry {

    private static final Map<String, SpellFunctionHandler> HANDLERS = new ConcurrentHashMap<>();

    static {
        register("iactare", new IactareFunctionHandler());
        register("vocant", new VocantFunctionHandler());
        register("exsugat", new ExsugatFunctionHandler());
        register("impediunt", new ImpediuntFunctionHandler());
        register("reframe", new ReframeFunctionHandler());
        register("ligabis", new LigabisFunctionHandler());
        register("transvocatio", new TransvocatioFunctionHandler());
        register("surgit", new SurgitFunctionHandler());
        // Only vertere on a marked thing reaches a handler; between sources the executor converts the Vita.
        register("vertere", new MarkVertereFunctionHandler());
    }

    private SpellFunctionHandlerRegistry() {
    }

    public static void register(String runeId, SpellFunctionHandler handler) {
        String key = sanitize(runeId);
        Objects.requireNonNull(handler, "handler");
        if (key == null) {
            throw new IllegalArgumentException("Rune id cannot be null or blank");
        }
        HANDLERS.put(key, handler);
    }

    public static void unregister(String runeId) {
        String key = sanitize(runeId);
        if (key == null) {
            return;
        }
        HANDLERS.remove(key);
    }

    public static Optional<SpellFunctionHandler> find(String runeId) {
        String key = sanitize(runeId);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(HANDLERS.get(key));
    }

    public static Map<String, SpellFunctionHandler> snapshot() {
        return Collections.unmodifiableMap(HANDLERS);
    }

    private static String sanitize(String runeId) {
        if (runeId == null || runeId.isBlank()) {
            return null;
        }
        return runeId.toLowerCase(Locale.ROOT).trim();
    }
}
