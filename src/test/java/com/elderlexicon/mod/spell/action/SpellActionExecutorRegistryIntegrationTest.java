package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.function.SpellFunctionHandler;
import com.elderlexicon.mod.spell.registry.SpellFunctionHandlerRegistry;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellActionExecutorRegistryIntegrationTest {

    private static final String CUSTOM_RUNE = "registry_test";

    @AfterEach
    void cleanup() {
        SpellFunctionHandlerRegistry.unregister(CUSTOM_RUNE);
    }

    @Test
    void resolvesHandlersRegisteredAtRuntime() {
        AtomicInteger counter = new AtomicInteger();
        SpellFunctionHandler handler = (context, element) -> counter.incrementAndGet();
        SpellFunctionHandlerRegistry.register(CUSTOM_RUNE, handler);

        SpellActionExecutor executor = new SpellActionExecutor();
        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.BALANCED, 0.0D, List.of(), List.of());
        SpellAction customAction = SpellAction.builder(CUSTOM_RUNE, SpellActionType.FUNCTION)
                .element(VitaElement.AQUA)
                .build();

        executor.execute(context, List.of(customAction));

        assertEquals(1, counter.get(), "Executor should resolve handlers via registry");
    }
}
