package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the mind sees in a sentence, and who it is born as (docs/feiticeiro-design.md, 3 and 4). */
class MindTest {

    private static Act act(String operation, VitaElement element, Act.Toward toward, boolean reversed) {
        return new Act(operation, element, toward, reversed, false, false, OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty());
    }

    @Test
    void throwingAtTheAimHurts() {
        assertEquals(Intent.STRIKE, Mind.serves(act("project", VitaElement.IGNI, Act.Toward.AIM, false)));
        assertEquals(Intent.STRIKE, Mind.serves(act("project", VitaElement.FIRMO, Act.Toward.RESULT, false)));
        assertNull(Mind.serves(act("project", VitaElement.IGNI, Act.Toward.AIM, true)), "pulling is no throw");
    }

    @Test
    void drawingInFromTheAimDrawsOutOfABody() {
        assertEquals(Intent.DRAW, Mind.serves(act("manifest", VitaElement.AQUA, Act.Toward.AIM, true)));
        assertNull(Mind.serves(act("manifest", VitaElement.AQUA, Act.Toward.AIM, false)), "water made there hurts no one");
    }

    @Test
    void theCasterUnseenVanishes() {
        Act veil = new Act("perceive", VitaElement.BALANCED, Act.Toward.SELF, false, false, false,
                OptionalDouble.empty(), OptionalDouble.of(6.0D), OptionalDouble.of(0.0D));
        assertEquals(Intent.VEIL, Mind.serves(veil));
        assertFalse(Mind.turnsOnSelf(veil));
    }

    @Test
    void changingItsOwnBodyIsNeverWorthWeighing() {
        Act convertSelf = act("convert", VitaElement.IGNI, Act.Toward.SELF, false);
        assertTrue(Mind.turnsOnSelf(convertSelf));
        Option strikeAndBurnSelf = new Option("x", List.of(), List.of(act("project", VitaElement.IGNI, Act.Toward.AIM,
                false), convertSelf), 4.0D, true);
        Option strike = new Option("y", List.of(), List.of(act("project", VitaElement.IGNI, Act.Toward.AIM, false)), 3.0D,
                true);
        assertEquals(List.of(strike), Mind.worthWeighing(List.of(strikeAndBurnSelf, strike)));
    }

    @Test
    void moreEnergyThrownHurtsMore() {
        Act ten = act("project", VitaElement.IGNI, Act.Toward.AIM, false);
        Act twenty = new Act("project", VitaElement.IGNI, Act.Toward.AIM, false, false, false, OptionalDouble.of(20.0D),
                OptionalDouble.empty(), OptionalDouble.empty());
        assertEquals(2.0D * Mind.potency(ten), Mind.potency(twenty), 1.0E-9D);
    }

    @Test
    void bringingWhatCarriesAMarkCallsTheBand() {
        Option call = Option.of(PhraseCatalog.imagine(List.of("kador", "vocant")).orElseThrow());
        Act act = call.acts().get(0);
        assertEquals("kador", act.mark());
        assertEquals(Intent.CALL, Mind.serves(act));
        Mind.Scene alone = new Mind.Scene(null, null, 10.0D, 1.0F, 100.0D);
        Mind.Scene withBand = new Mind.Scene(null, null, 10.0D, 1.0F, 100.0D, mark -> mark.equals("kador") ? 2.0D : 0.0D);
        assertTrue(Mind.evaluate(alone, Personality.AGGRESSIVE, call, Mind.person(0.0D)).isEmpty(),
                "with no one to bring, calling is worth nothing");
        assertTrue(Mind.evaluate(withBand, Personality.AGGRESSIVE, call, Mind.person(0.0D)).isPresent());
    }

    @Test
    void theDegreeFollowsWhatItKnows() {
        assertEquals(Degree.APPRENTICE, Degree.of(3));
        assertEquals(Degree.MASTER, Degree.of(8));
        assertEquals(Degree.ELDER, Degree.of(14));
        assertTrue(Degree.learnable(Lexicons.builtIn()).contains("aqua"));
        assertFalse(Degree.learnable(Lexicons.builtIn()).contains("ferit"), "a condition is no word of a creature's");
    }

    @Test
    void anApprenticeKnowsASourceAndTheVerbsToFight() {
        Set<String> known = Degree.APPRENTICE.draw(Lexicons.builtIn(), RandomSource.create(7L));
        assertTrue(known.contains("iactare") && known.contains("vocant"), known.toString());
        assertTrue(known.stream().anyMatch(rune -> Lexicons.builtIn().source(rune).isPresent()), known.toString());
        Set<String> elder = Degree.ELDER.draw(Lexicons.builtIn(), RandomSource.create(7L));
        assertTrue(elder.size() > known.size());
        assertFalse(elder.contains("ille"), "the other one of a happening is no word of a creature's");
    }
}
