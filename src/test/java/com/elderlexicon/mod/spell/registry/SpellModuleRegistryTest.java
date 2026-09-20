package com.elderlexicon.mod.spell.registry;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellModule;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SpellModuleRegistryTest {

    private final SpellModule dummyModule = context -> { };

    @AfterEach
    void tearDown() {
        SpellModuleRegistry.unregister(dummyModule);
    }

    @Test
    void exposesDefaultModules() {
        List<com.elderlexicon.mod.spell.SpellModule> modules = SpellModuleRegistry.snapshot();
        assertFalse(modules.isEmpty(), "Default spell modules should be registered");
    }

    @Test
    void registersCustomModules() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        SpellModule module = context -> invoked.set(true);

        SpellModuleRegistry.register(module);
        SpellModuleRegistry.snapshot().forEach(registered -> registered.apply(
            new SpellContext(null, List.of(), Optional.empty(), VitaElement.BALANCED, 0.0D, List.of(), List.of())));

        assertTrue(invoked.get(), "Custom module should be invoked when snapshot is applied");
    }

    @Test
    void unregisterRemovesModule() {
        SpellModuleRegistry.register(dummyModule);
        SpellModuleRegistry.unregister(dummyModule);

        assertFalse(SpellModuleRegistry.snapshot().contains(dummyModule));
    }
}
