package com.elderlexicon.mod.spell.registry;

import com.elderlexicon.mod.spell.SpellModule;
import com.elderlexicon.mod.spell.module.SpellCostModule;
import com.elderlexicon.mod.spell.module.ConduitMitigationModule;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime registry tracking spell modules applied after execution so mods can extend the pipeline.
 */
public final class SpellModuleRegistry {

    private static final CopyOnWriteArrayList<SpellModule> MODULES = new CopyOnWriteArrayList<>();

    static {
        register(new ConduitMitigationModule());
        register(new SpellCostModule());
    }

    private SpellModuleRegistry() {
    }

    public static void register(SpellModule module) {
        Objects.requireNonNull(module, "module");
        MODULES.addIfAbsent(module);
    }

    public static void unregister(SpellModule module) {
        if (module == null) {
            return;
        }
        MODULES.remove(module);
    }

    public static List<SpellModule> snapshot() {
        return List.copyOf(MODULES);
    }
}
