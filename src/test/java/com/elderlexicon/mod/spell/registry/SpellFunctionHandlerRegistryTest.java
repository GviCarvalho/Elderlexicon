package com.elderlexicon.mod.spell.registry;

import com.elderlexicon.mod.spell.function.SpellFunctionHandler;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SpellFunctionHandlerRegistryTest {

    private static final String CUSTOM_RUNE = "custom_handler";

    @AfterEach
    void cleanUp() {
        SpellFunctionHandlerRegistry.unregister(CUSTOM_RUNE);
    }

    @Test
    void registersAndFindsHandlers() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        SpellFunctionHandler handler = (context, element) -> invoked.set(element == VitaElement.BALANCED);

        SpellFunctionHandlerRegistry.register(CUSTOM_RUNE, handler);

        assertTrue(SpellFunctionHandlerRegistry.find(CUSTOM_RUNE).isPresent());
        SpellFunctionHandlerRegistry.find(CUSTOM_RUNE).ifPresent(found -> found.execute(null, VitaElement.BALANCED));
        assertTrue(invoked.get(), "Custom handler should run when resolved from registry");
    }

    @Test
    void unregisterRemovesHandlers() {
        SpellFunctionHandler handler = (context, element) -> { };
        SpellFunctionHandlerRegistry.register(CUSTOM_RUNE, handler);
        SpellFunctionHandlerRegistry.unregister(CUSTOM_RUNE);

        assertTrue(SpellFunctionHandlerRegistry.find(CUSTOM_RUNE).isEmpty());
    }
}
