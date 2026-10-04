package com.elderlexicon.mod.magic;

import com.elderlexicon.mod.magic.grammar.SpellGrammar;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.action.SpellActionType;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rules of the grammar the new vocabulary brought (docs/plano-materia-e-forca.md, section 3): each is a rule of the
 * sentence, for any rune that has the class or the flag it speaks of, never a spell written out in code.
 */
class GrammarRulesTest {

    private static SpellActionResult read(String spell) {
        return new SpellGrammar(Lexicons.builtIn()).read(Arrays.asList(spell.split(" ")));
    }

    private static List<SpellAction> verbs(SpellActionResult result) {
        return result.actions().stream().filter(action -> action.type() == SpellActionType.FUNCTION).toList();
    }

    private static SpellAction onlyVerb(String spell) {
        SpellActionResult result = read(spell);
        assertTrue(result.issues().isEmpty(), spell + ": " + result.issues());
        List<SpellAction> verbs = verbs(result);
        assertEquals(1, verbs.size(), spell);
        return verbs.get(0);
    }

    private static boolean complains(String spell, String about) {
        return read(spell).issues().stream().anyMatch(issue -> issue.contains(about));
    }

    // ------------------------------------------------------------------ conditions (docs/fluxo-design.md)

    @Test
    void aConditionIsNoPartOfWhatTheLineDoes() {
        SpellAction plain = onlyVerb("igni iactare");
        SpellAction woken = onlyVerb("ferit igni iactare");
        assertEquals(plain.runeId(), woken.runeId());
        assertEquals(plain.element(), woken.element());
        assertEquals(plain.element(), onlyVerb("igni patitur iactare").element(), "written anywhere, it is skipped");
        assertEquals(plain.element(), onlyVerb("ferit non latet aut patitur igni iactare").element(),
                "and so are the words that join conditions");
    }

    // ------------------------------------------------------------------ R2: the subject

    @Test
    void aVerbWrittenAfterAnotherActsOnWhatThatOneProduced() {
        List<SpellAction> verbs = verbs(read("igni vocant iactare"));
        assertFalse(verbs.get(0).chained(), "the vocant acts on the fire written for it");
        assertTrue(verbs.get(1).chained(), "the iactare pushes the fire the vocant made appear");
        assertEquals(VitaElement.IGNI, verbs.get(1).element());
    }

    @Test
    void aNewSubjectWrittenBreaksTheChain() {
        assertFalse(verbs(read("igni vocant aqua iactare")).get(1).chained(), "a source written is the new subject");
        List<SpellAction> marked = verbs(read("m1 vocant iactare"));
        assertFalse(marked.get(1).chained(), "a marked thing is still named by its mark");
        assertEquals("m1", marked.get(1).subjectMark().orElseThrow());
    }

    @Test
    void theTargetOfAConversionIsWhatTheSubjectBecame() {
        List<SpellAction> verbs = verbs(read("igni vertere aqua iactare"));
        assertTrue(verbs.get(1).chained(), "the water the fire became is what is thrown");
        assertEquals(VitaElement.AQUA, verbs.get(1).element());
    }

    @Test
    void aViewIsNoVerbOfItsOwnInTheChain() {
        List<SpellAction> verbs = verbs(read("igni surgit vocant iactare"));
        assertTrue(verbs.get(0).image());
        assertFalse(verbs.get(0).chained(), "the view only changes the vocant");
        assertTrue(verbs.get(1).chained());
    }

    // ------------------------------------------------------------------ R1 and R3: a filter is for the verb after it

    @Test
    void aPlaceBelongsToTheVerbRightAfterIt() {
        List<SpellAction> verbs = verbs(read("igni vocant 20 ubis iactare"));
        assertTrue(verbs.get(0).place().isEmpty(), "the vocant appears where the mage aims");
        assertEquals(20.0D, verbs.get(1).place().orElseThrow().distance(), 1.0E-9, "the iactare pushes toward 20");
        assertTrue(verbs.get(1).chained());
    }

    // ------------------------------------------------------------------ R4: the sign

    @Test
    void aNegativeQuantityTurnsAVerbThatHasASense() {
        SpellAction pull = onlyVerb("m1 quantum -20 iactare");
        assertTrue(pull.reversed());
        assertEquals(20.0D, pull.quantity().orElseThrow(), 1.0E-9, "its size is what it costs");
        assertTrue(onlyVerb("igni -10 quantum vocant").reversed(), "written before the filter too");
        assertTrue(onlyVerb("aura quantum -30 chronos 5 impediunt").reversed());
        assertFalse(onlyVerb("igni quantum 10 vocant").reversed());
    }

    @Test
    void aVerbWithNoSenseRefusesANegativeQuantity() {
        SpellActionResult converted = read("igni quantum -5 vertere aqua");
        assertTrue(converted.issues().stream().anyMatch(issue -> issue.contains("negativa não tem sentido para 'vertere'")
                && issue.contains("iactare")), converted.issues().toString());
        assertTrue(verbs(converted).get(0).quantity().isEmpty(), "the quantity is dropped");
        assertFalse(verbs(converted).get(0).reversed());
        assertTrue(complains("quantum -3 firmo iactare", "Uma fonte não tem quantidade negativa"));
        assertTrue(complains("igni quantum 0 iactare", "diferente de zero"));
    }

    // ------------------------------------------------------------------ R5: the origin

    @Test
    void anOriginSaysTheSourceComesFromTheWorld() {
        SpellAction near = onlyVerb("firmo tenet quantum iactare");
        assertTrue(near.fromWorld());
        assertTrue(near.quantityAll(), "all of it in reach");
        assertTrue(near.originPlace().isEmpty(), "within the mage's reach");
        assertFalse(onlyVerb("firmo quantum iactare").fromWorld(), "without it, from the body");
    }

    @Test
    void whatIsWrittenBeforeTheOriginIsWhereTheSourceIsTaken() {
        SpellAction aroundMark = onlyVerb("firmo m1 tenet iactare");
        assertEquals(SpellPlace.Kind.MARK, aroundMark.originPlace().orElseThrow().kind());
        assertTrue(aroundMark.subjectMark().isEmpty(), "m1 says where, it is not what is thrown");
        SpellAction ahead = onlyVerb("firmo 10 tenet 5 ubis vocant");
        assertEquals(10.0D, ahead.originPlace().orElseThrow().distance(), 1.0E-9);
        assertEquals(5.0D, ahead.place().orElseThrow().distance(), 1.0E-9, "where from and where to are apart");
    }

    @Test
    void anOriginHasNothingToSayOfAMarkedThingOrOfWhatWasProduced() {
        SpellActionResult chained = read("igni vocant tenet iactare");
        assertTrue(chained.issues().stream().anyMatch(issue -> issue.contains("o que o verbo anterior produziu")),
                chained.issues().toString());
        assertFalse(verbs(chained).get(1).fromWorld());
        SpellActionResult marked = read("firmo tenet m1 iactare");
        assertTrue(marked.issues().stream().anyMatch(issue -> issue.contains("marca 'm1'")), marked.issues().toString());
        assertFalse(verbs(marked).get(0).fromWorld());
        assertTrue(complains("igni tenet", "Filtro sem função depois dele"));
    }

    // ------------------------------------------------------------------ words the language no longer has

    @Test
    void aRetiredWordSaysWhatToWriteInstead() {
        assertTrue(complains("igni exsugat", "tenet"));
        assertTrue(complains("m1 transvocatio", "transvocatio não existe mais"));
        assertTrue(complains("igni exhaustio aqua", "tenet vertere"));
        assertFalse(complains("igni exsugat", "Lexema desconhecido"), "it is not an unknown word");
    }
}
