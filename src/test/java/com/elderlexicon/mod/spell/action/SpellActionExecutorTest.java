package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.function.SpellFunctionHandler;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SpellActionExecutorTest {

    @Test
    void invokesHandlersInOrder() {
        AtomicInteger counter = new AtomicInteger();
        SpellFunctionHandler handler = (context, element) -> counter.incrementAndGet();
        SpellActionExecutor executor = new SpellActionExecutor(Map.of("test", handler));

        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.BALANCED, 0.0D, List.of(), List.of());
        SpellAction source = SpellAction.builder("igni", SpellActionType.SOURCE)
                .element(VitaElement.IGNI).build();
        SpellAction function = SpellAction.builder("test", SpellActionType.FUNCTION)
                .element(VitaElement.IGNI).build();

        executor.execute(context, List.of(source, function));
        assertEquals(1, counter.get());
    }

    @Test
    void processesVertereRequests() {
        RecordingGateway gateway = new RecordingGateway();
        SpellActionExecutor executor = new SpellActionExecutor(Map.of(), gateway);

        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.FIRMO, 0.0D, List.of(),
                List.of(new VertereRequest(VitaElement.FIRMO, VitaElement.IGNI, 1.0D)));

        SpellAction firmo = SpellAction.builder("firmo", SpellActionType.SOURCE)
                .element(VitaElement.FIRMO)
                .build();
        SpellAction vertere = SpellAction.builder("vertere", SpellActionType.FUNCTION)
                .element(VitaElement.FIRMO)
                .build();
        SpellAction igni = SpellAction.builder("igni", SpellActionType.SOURCE)
                .element(VitaElement.IGNI)
                .build();

        executor.execute(context, List.of(firmo, vertere, igni));

        assertEquals(1, gateway.calls.get());
        assertEquals(VitaElement.IGNI, context.primaryElement());
        assertEquals(1.0D, context.totalCost(), 1.0E-4);
    }

    @Test
    void vertereWithAFocusNeverTouchesTheCastersVita() {
        RecordingGateway gateway = new RecordingGateway();
        SpellActionExecutor executor = new SpellActionExecutor(Map.of(), gateway);

        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.FIRMO, 0.0D, List.of(),
                List.of(new VertereRequest(VitaElement.FIRMO, VitaElement.IGNI, 1.0D)));
        context.setFocusActive(true);

        SpellAction firmo = SpellAction.builder("firmo", SpellActionType.SOURCE).element(VitaElement.FIRMO).build();
        SpellAction vertere = SpellAction.builder("vertere", SpellActionType.FUNCTION).element(VitaElement.FIRMO).build();
        SpellAction igni = SpellAction.builder("igni", SpellActionType.SOURCE).element(VitaElement.IGNI).build();

        executor.execute(context, List.of(firmo, vertere, igni));

        assertEquals(0, gateway.calls.get(), "the Vita gateway must not be used with a focus");
        assertEquals(VitaElement.IGNI, context.primaryElement(), "the spell still converts");
        assertEquals(1.0D, context.totalCost(), 1.0E-4, "the conversion is still paid for");
    }

    private static final class RecordingGateway implements SpellActionExecutor.VertereGateway {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public double transfer(SpellContext context, VertereRequest request) {
            calls.incrementAndGet();
            return request.amount();
        }
    }
}
