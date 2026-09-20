package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellContextTest {

    @Test
    void primaryElementCanBeUpdated() {
        SpellContext context = new SpellContext(
                null,
                List.of(),
                Optional.empty(),
                VitaElement.IGNI,
                0.0D,
                List.of(),
                List.of());

        assertEquals(VitaElement.IGNI, context.basePrimaryElement());
        assertEquals(VitaElement.IGNI, context.primaryElement());

        context.setPrimaryElement(VitaElement.AQUA);

        assertEquals(VitaElement.IGNI, context.basePrimaryElement(), "Base element must remain immutable");
        assertEquals(VitaElement.AQUA, context.primaryElement(), "Active element should reflect swaps");
    }

    @Test
    void storesVertereRequests() {
        VertereRequest request = new VertereRequest(VitaElement.IGNI, VitaElement.AQUA, 1.0D);
        SpellContext context = new SpellContext(
            null,
            List.of(),
            Optional.empty(),
            VitaElement.BALANCED,
            0.0D,
            List.of(),
            List.of(request));

        assertEquals(1, context.vertereRequests().size());
        assertEquals(request, context.vertereRequests().get(0));
    }
}
