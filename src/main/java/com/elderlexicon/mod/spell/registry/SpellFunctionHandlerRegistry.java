package com.elderlexicon.mod.spell.registry;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
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
 * The operations the world runs for verbs, by operation id. A verb of the lexicon names the operation it runs
 * ({@code "operation": "project"} for iactare), so any number of runes may share one, a new rune may use an operation
 * already here, and a mod may register, override or remove operations at runtime. This is the one place that says
 * which code carries out which deed; no rune is named here.
 * <p>
 * A handler may also be registered under a rune id directly, for a verb the lexicon does not know.
 */
public final class SpellFunctionHandlerRegistry {

    private static final Map<String, SpellFunctionHandler> HANDLERS = new ConcurrentHashMap<>();

    static {
        // What the spirit can do in the world, by the deed and not by any word for it.
        register("project", new IactareFunctionHandler());
        register("manifest", new VocantFunctionHandler());
        register("draw", new ExsugatFunctionHandler());
        register("repel", new ImpediuntFunctionHandler());
        register("name", new ReframeFunctionHandler());
        register("bind", new LigabisFunctionHandler());
        register("exchange", new TransvocatioFunctionHandler());
        register("perceive", new SurgitFunctionHandler());
        // Only a conversion of a marked thing reaches its operation; the flow converts energy itself.
        register("convert", new MarkVertereFunctionHandler());
    }

    private SpellFunctionHandlerRegistry() {
    }

    /** Registers what runs for an operation (or, for a verb the lexicon does not know, for a rune id). */
    public static void register(String operation, SpellFunctionHandler handler) {
        String key = sanitize(operation);
        Objects.requireNonNull(handler, "handler");
        if (key == null) {
            throw new IllegalArgumentException("Operation id cannot be null or blank");
        }
        HANDLERS.put(key, handler);
    }

    public static void unregister(String operation) {
        String key = sanitize(operation);
        if (key == null) {
            return;
        }
        HANDLERS.remove(key);
    }

    /** What runs for a verb: the operation the lexicon names for it, or what was registered under its own id. */
    public static Optional<SpellFunctionHandler> find(String runeId) {
        String key = sanitize(runeId);
        if (key == null) {
            return Optional.empty();
        }
        Optional<String> operation = Lexicons.get().operationOf(key).map(SpellFunctionHandlerRegistry::sanitize);
        if (operation.isPresent() && HANDLERS.containsKey(operation.get())) {
            return Optional.of(HANDLERS.get(operation.get()));
        }
        return Optional.ofNullable(HANDLERS.get(key));
    }

    /** What runs for an operation. */
    public static Optional<SpellFunctionHandler> forOperation(String operation) {
        String key = sanitize(operation);
        return key == null ? Optional.empty() : Optional.ofNullable(HANDLERS.get(key));
    }

    public static Map<String, SpellFunctionHandler> snapshot() {
        return Collections.unmodifiableMap(HANDLERS);
    }

    private static String sanitize(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.toLowerCase(Locale.ROOT).trim();
    }
}
