package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The laws of matter (docs/plano-materia-e-forca.md, section 1), for any substance. */
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

    // ------------------------------------------------------------------ L4: mixing

    @Test
    void fluidsInOnePlaceBecomeWhatTheirProportionMatches() {
        Matter mud = MatterLaws.mix(List.of(primordial(VitaElement.FIRMO, State.LIQUID, 5.0D),
                primordial(VitaElement.AQUA, State.LIQUID, 5.0D))).orElseThrow();
        assertEquals(Optional.of(substance("mud")), mud.substance(TABLE));
        assertEquals(10.0D, mud.umu(), 1.0E-9);
        assertEquals(State.LIQUID, mud.state());
    }

    @Test
    void theRightProportionOfThePrimordialsMakesStone() {
        Matter made = MatterLaws.mix(List.of(
                primordial(VitaElement.FIRMO, State.LIQUID, 16.0D),
                primordial(VitaElement.AQUA, State.LIQUID, 1.0D),
                primordial(VitaElement.AURA, State.GAS, 1.0D),
                primordial(VitaElement.IGNI, State.PLASMA, 2.0D))).orElseThrow();
        assertEquals(Optional.of(substance("stone")), made.substance(TABLE));
        assertEquals(State.LIQUID, made.state(), "most of it was liquid: it is molten stone");
        assertEquals("minecraft:lava", TABLE.form(substance("stone"), made.state()).orElseThrow().id());
        assertEquals(20.0D, made.umu(), 1.0E-9);
    }

    @Test
    void solidsDoNotMix() {
        assertTrue(MatterLaws.mix(List.of(primordial(VitaElement.FIRMO, State.SOLID, 5.0D),
                primordial(VitaElement.AQUA, State.LIQUID, 5.0D))).isEmpty(), "a bond joins solids, not a mixture");
        assertTrue(MatterLaws.mix(List.of()).isEmpty());
    }

    @Test
    void substancesMixByWhatTheyAreMadeOf() {
        // Mud is half earth: with as much water again it is a quarter earth, near no natural thing's code.
        Matter wet = MatterLaws.mix(List.of(Matter.of(substance("mud"), State.LIQUID, 10.0D),
                primordial(VitaElement.AQUA, State.LIQUID, 10.0D))).orElseThrow();
        assertEquals(0.25D, wet.composition().share(VitaElement.FIRMO), 1.0E-9);
        assertTrue(wet.unnamed(TABLE));
    }

    // ------------------------------------------------------------------ L5: opposites react

    @Test
    void fireAndWaterInALiquidBoilAway() {
        // Six of water and three of fire: three of each boil off as a gas, and three of water stay.
        Matter hot = MatterLaws.mix(List.of(primordial(VitaElement.AQUA, State.LIQUID, 6.0D),
                primordial(VitaElement.IGNI, State.PLASMA, 3.0D))).orElseThrow();
        MatterLaws.Reaction reaction = MatterLaws.react(hot);
        assertTrue(reaction.reacted());
        Matter vapour = reaction.released().orElseThrow();
        assertEquals(State.GAS, vapour.state());
        assertEquals(6.0D, vapour.umu(), 1.0E-9);
        assertEquals(0.5D, vapour.composition().share(VitaElement.IGNI), 1.0E-9, "as much of one as of the other");
        Matter left = reaction.remains().orElseThrow();
        assertEquals(3.0D, left.umu(), 1.0E-9);
        assertEquals(1.0D, left.composition().share(VitaElement.AQUA), 1.0E-9, "the side that won stays");
        assertFalse(MatterLaws.react(left).reacted(), "what stays reacts no more");
    }

    @Test
    void earthAndAirScatterAsDust() {
        Matter stirred = new Matter(Composition.of(Map.of(VitaElement.FIRMO, 0.6D, VitaElement.AURA, 0.4D)),
                State.LIQUID, 10.0D);
        MatterLaws.Reaction reaction = MatterLaws.react(stirred);
        assertEquals(8.0D, reaction.released().orElseThrow().umu(), 1.0E-9);
        assertEquals(2.0D, reaction.remains().orElseThrow().umu(), 1.0E-9);
    }

    @Test
    void aLittleOfAnOppositeDoesNotReactNorDoesASolid() {
        Matter lava = Matter.of(substance("stone"), State.LIQUID, 3.0D);
        assertFalse(MatterLaws.react(lava).reacted(), "the water and fire in stone are too little to meet");
        Matter wood = Matter.natural(substance("wood"), 2.0D);
        assertFalse(MatterLaws.react(wood).reacted(), "a solid holds its parts still");
        MatterLaws.Reaction molten = MatterLaws.react(wood.inState(State.LIQUID));
        assertTrue(molten.reacted(), "molten wood boils");
        Matter steam = Matter.natural(substance("steam"), 4.0D);
        assertFalse(MatterLaws.react(steam).reacted(), "a gas is what a reaction lets out");
    }

    // ------------------------------------------------------------------ L1: conservation

    @Test
    void noLawMakesOrDestroysMatter() {
        double[][] portions = {{3, 0, 0, 1}, {1, 1, 1, 1}, {0.5, 7, 0, 2.5}, {10, 0.1, 0.1, 0}};
        for (double[] amounts : portions) {
            List<Matter> fluids = List.of(
                    primordial(VitaElement.FIRMO, State.LIQUID, amounts[0]),
                    primordial(VitaElement.AQUA, State.LIQUID, amounts[1]),
                    primordial(VitaElement.AURA, State.GAS, amounts[2]),
                    primordial(VitaElement.IGNI, State.PLASMA, amounts[3]));
            double total = amounts[0] + amounts[1] + amounts[2] + amounts[3];
            Matter mixed = MatterLaws.mix(fluids).orElseThrow();
            assertEquals(total, mixed.umu(), 1.0E-9);
            MatterLaws.Reaction reaction = MatterLaws.react(mixed);
            assertEquals(total, reaction.remains().map(Matter::umu).orElse(0.0D)
                    + reaction.released().map(Matter::umu).orElse(0.0D), 1.0E-9);
            for (State state : State.values()) {
                assertEquals(total, MatterLaws.changeState(mixed, state).matter().umu(), 1.0E-9);
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
