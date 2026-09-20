package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellCostProcessorTest {

    @Test
    void sumsSourceAndFunctionCosts() {
        SpellCostProcessor processor = new SpellCostProcessor(Map.of("iactare", 3.0D), 1.0D);
        SpellAction source = SpellAction.builder("igni", SpellActionType.SOURCE)
                .element(VitaElement.IGNI)
                .build();
        SpellAction function = SpellAction.builder("iactare", SpellActionType.FUNCTION)
                .element(VitaElement.IGNI)
                .build();

        double total = processor.computeTotalCost(List.of(source, function));
        assertEquals(4.0D, total, 1.0E-4);
    }

    @Test
    void ignoresUnknownActions() {
        SpellCostProcessor processor = new SpellCostProcessor(Map.of(), 0.5D);
        SpellAction shape = SpellAction.builder("orbis", SpellActionType.SHAPE)
                .addShape("bolt")
                .build();

        double total = processor.computeTotalCost(List.of(shape));
        assertEquals(0.0D, total, 1.0E-4);
    }
}
