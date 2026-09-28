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

    @Test
    void vertereOfASourceFromTheWorldConvertsItNotTheVita() {
        RecordingGateway gateway = new RecordingGateway();
        AtomicInteger thrown = new AtomicInteger();
        VitaElement[] thrownAs = new VitaElement[1];
        SpellActionExecutor executor = new SpellActionExecutor(Map.of("iactare", (context, element) -> {
            thrown.incrementAndGet();
            thrownAs[0] = element;
        }), gateway);

        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.IGNI, 3.0D, List.of(),
                List.of(new VertereRequest(VitaElement.IGNI, VitaElement.AQUA, 1.0D)));
        SpellAction igni = SpellAction.builder("igni", SpellActionType.SOURCE).element(VitaElement.IGNI).build();
        // igni tenet vertere aqua iactare: the fire is taken from the world, converted, and the iactare throws it.
        SpellAction vertere = SpellAction.builder("vertere", SpellActionType.FUNCTION).element(VitaElement.IGNI)
                .putMetadata(SpellAction.FROM_WORLD, Boolean.TRUE).build();
        SpellAction aqua = SpellAction.builder("aqua", SpellActionType.SOURCE).element(VitaElement.AQUA).build();
        SpellAction iactare = SpellAction.builder("iactare", SpellActionType.FUNCTION).element(VitaElement.AQUA)
                .putMetadata(SpellAction.CHAINED, Boolean.TRUE).build();

        executor.execute(context, List.of(igni, vertere, aqua, iactare));

        assertEquals(0, gateway.calls.get(), "the fire taken from the world is converted, not the body's");
        assertEquals(1, thrown.get());
        assertEquals(VitaElement.AQUA, thrownAs[0], "what is thrown is the converted water");
        assertEquals(VitaElement.AQUA, context.primaryElement());
        assertEquals(3.0D, context.totalCost(), 1.0E-4, "converting what is taken adds nothing taken from the Vita");
    }

    @Test
    void vertereWithAQuantumConvertsThatMuch() {
        RecordingGateway gateway = new RecordingGateway();
        SpellActionExecutor executor = new SpellActionExecutor(Map.of(), gateway);

        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.FIRMO, 0.0D, List.of(),
                List.of(new VertereRequest(VitaElement.FIRMO, VitaElement.IGNI, 1.0D)));
        SpellAction firmo = SpellAction.builder("firmo", SpellActionType.SOURCE).element(VitaElement.FIRMO).build();
        SpellAction vertere = SpellAction.builder("vertere", SpellActionType.FUNCTION).element(VitaElement.FIRMO)
                .putMetadata(SpellAction.QUANTITY, 5.0D).build();
        SpellAction igni = SpellAction.builder("igni", SpellActionType.SOURCE).element(VitaElement.IGNI).build();

        executor.execute(context, List.of(firmo, vertere, igni));

        assertEquals(1, gateway.calls.get());
        assertEquals(5.0D, gateway.lastAmount, 1.0E-4);
    }

    private static final class RecordingGateway implements SpellActionExecutor.VertereGateway {
        private final AtomicInteger calls = new AtomicInteger();
        private double lastAmount;

        @Override
        public double transfer(SpellContext context, VertereRequest request) {
            calls.incrementAndGet();
            lastAmount = request.amount();
            return request.amount();
        }
    }
}
