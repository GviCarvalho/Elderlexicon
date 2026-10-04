package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The laws of matter (docs/plano-materia-e-forca.md, section 1), for any substance. Mixing and what mixed matter does
 * are the drives' now (magic/physics, {@code FieldTest}).
 */
class MatterLawsTest {

    private static final MaterialTable TABLE = Materials.builtIn();

    private static Substance substance(String id) {
        return TABLE.substance(id).orElseThrow();
    }

    private static Matter primordial(VitaElement aspect, State state, double umu) {
        return Matter.of(TABLE.primordial(aspect), state, umu);
    }

    // ------------------------------------------------------------------ L2: the ladder

    @Test
    void changingStateClimbsOrDescendsTheLadder() {
        assertEquals(1, MatterLaws.steps(State.SOLID, State.LIQUID));
        assertEquals(3, MatterLaws.steps(State.SOLID, State.PLASMA), "solid to plasma is the whole ladder");
        assertEquals(2, MatterLaws.steps(State.PLASMA, State.LIQUID), "descending costs as climbing");
        assertEquals(0, MatterLaws.steps(null, State.PLASMA), "out of vis costs nothing");
        assertEquals(3, MatterLaws.steps(State.LIQUID, null), "back into vis costs the whole ladder");
        assertEquals(0.05D, MatterLaws.workShare(1), 1.0E-9);
        assertEquals(0.15D, MatterLaws.workShare(3), 1.0E-9);
    }

    @Test
    void aChangeOfStateKeepsWhatTheMatterIsAndHowMuchOfItThereIs() {
        Matter stone = Matter.natural(substance("stone"), 1.5D);
        MatterLaws.Change molten = MatterLaws.changeState(stone, State.LIQUID);
        assertEquals(State.LIQUID, molten.matter().state());
        assertEquals(1.5D, molten.matter().umu(), 1.0E-9, "the matter keeps its UMU");
        assertEquals(Optional.of(substance("stone")), molten.matter().substance(TABLE), "stone melted is still stone");
        assertEquals(1.5D * 0.05D, molten.work(), 1.0E-9, "whoever melts it pays the work");
        Matter ice = MatterLaws.changeState(primordial(VitaElement.AQUA, State.LIQUID, 3.0D), State.SOLID).matter();
        assertEquals("minecraft:ice", TABLE.form(ice.substance(TABLE).orElseThrow(), ice.state()).orElseThrow().id());
    }

    // ------------------------------------------------------------------ L3: the body

    @Test
    void theBodyKeepsEnergyNotMatter() {
        Matter lava = Matter.of(substance("stone"), State.LIQUID, 1.5D);
        MatterLaws.Energy taken = MatterLaws.intoBody(lava);
        assertEquals(VitaElement.AQUA, taken.aspect(), "it enters as the aspect of the state it was in");
        assertEquals(1.5D, taken.umu(), 1.0E-9);

        Matter given = MatterLaws.fromBody(TABLE, State.SOLID, 2.0D);
        assertEquals(Optional.of(TABLE.primordial(VitaElement.FIRMO)), given.substance(TABLE),
                "with no recipe, what leaves the body is the primordial of its state: firmo vocant is earth");
        assertEquals(2.0D, given.umu(), 1.0E-9);

        Map<VitaElement, Double> drawn = MatterLaws.drawnFromBody(substance("mud").recipe(), 4.0D);
        assertEquals(2.0D, drawn.get(VitaElement.FIRMO), 1.0E-9, "a known recipe draws each share from its aspect");
        assertEquals(2.0D, drawn.get(VitaElement.AQUA), 1.0E-9);
    }

    // ------------------------------------------------------------------ L1: conservation

    @Test
    void noLawMakesOrDestroysMatter() {
        double[][] portions = {{3, 0, 0, 1}, {1, 1, 1, 1}, {0.5, 7, 0, 2.5}, {10, 0.1, 0.1, 0}};
        for (double[] amounts : portions) {
            Matter mixed = new Matter(Composition.of(Map.of(VitaElement.FIRMO, amounts[0] + 1.0E-3D,
                    VitaElement.AQUA, amounts[1], VitaElement.AURA, amounts[2], VitaElement.IGNI, amounts[3])),
                    State.LIQUID, amounts[0] + amounts[1] + amounts[2] + amounts[3]);
            for (State state : State.values()) {
                assertEquals(mixed.umu(), MatterLaws.changeState(mixed, state).matter().umu(), 1.0E-9);
            }
        }
    }

    @Test
    void aCompositionIsOfMatterAlone() {
        assertThrows(IllegalArgumentException.class, () -> Composition.of(Map.of(VitaElement.BALANCED, 1.0D)),
                "vis is energy, no part of matter");
        assertThrows(IllegalArgumentException.class, () -> Composition.of(Map.of(VitaElement.IGNI, -1.0D)));
        assertThrows(IllegalArgumentException.class, () -> Composition.of(Map.of()));
        assertEquals(0.5D, Composition.of(Map.of(VitaElement.IGNI, 2.0D, VitaElement.FIRMO, 2.0D))
                .share(VitaElement.IGNI), 1.0E-9, "any proportion is normalized");
    }
}
