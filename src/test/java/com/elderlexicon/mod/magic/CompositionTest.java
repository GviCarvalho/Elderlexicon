package com.elderlexicon.mod.magic;

import com.elderlexicon.mod.ligabis.Aspect;
import com.elderlexicon.mod.ligabis.LigabisGrammar;
import com.elderlexicon.mod.magic.flow.FlowInterpreter;
import com.elderlexicon.mod.magic.grammar.SpellGrammar;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.LexiconBuilder;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.SourceSpec;
import com.elderlexicon.mod.magic.lexicon.Traits;
import com.elderlexicon.mod.magic.lexicon.VerbSpec;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.ElementPersistence;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.action.SpellActionType;
import com.elderlexicon.mod.spell.action.SpellCostProcessor;
import com.elderlexicon.mod.spell.block.SpellDescription;
import com.elderlexicon.mod.spell.block.SpellReading;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The language is small and spells are compositions: a spell means what its words compose, and a word means what the
 * lexicon says. A new word needs no code in the engine: an addon's source, verb, filter or fusion is read, explained
 * and run by the same rules as the book's own.
 */
class CompositionTest {

    /** An addon's words, in the same shape as the mod's own lexicon. */
    private static final String ADDON = """
            {
              "runes": {
                "glacies": {
                  "class": "source", "origin": "fusion", "components": ["aqua", "firmo"],
                  "translation": "ice", "name": "gelo", "noun": "o gelo",
                  "essence": {"aqua": 0.7, "firmo": 0.3}, "bond": "aqua",
                  "traits": {"persistent": true, "matter": "minecraft:ice"},
                  "texts": {"title.project": "Estilhaço de gelo"}
                },
                "proicere": {
                  "class": "verb", "translation": "hurl", "operation": "project", "cost": 3, "gathering": "hand",
                  "texts": {"role": "Arremessa {what} longe", "describe": "e arremessa longe{where}"}
                },
                "tempus": {"class": "filter", "translation": "while", "parameter": "time", "argument": "value"},
                "ignivocare": {"class": "verb", "origin": "fusion", "components": ["igni", "vocant"],
                               "expands": ["igni", "vocant"]},
                "flammavocare": {"class": "verb", "origin": "fusion", "expands": ["ignivocare"]}
              }
            }
            """;

    @AfterEach
    void forgetTheAddon() {
        Lexicons.reset();
    }

    private static SpellActionResult read(String spell) {
        return new SpellGrammar(Lexicons.get()).read(Arrays.asList(spell.split(" ")));
    }

    private static List<SpellAction> verbs(SpellActionResult result) {
        return result.actions().stream().filter(action -> action.type() == SpellActionType.FUNCTION).toList();
    }

    // ------------------------------------------------------------------ composition of the book's own words

    @Test
    void aFusionIsTheRunesItFuses() {
        assertEquals(read("igni vertere aqua iactare").actions(), read("igni transiectio aqua").actions());
        assertEquals(read("igni vertere aqua impediunt").actions(), read("igni aversio aqua").actions());
    }

    @Test
    void theSubjectStaysUntilAnotherIsNamed() {
        List<SpellAction> verbs = verbs(read("m1 vocant iactare"));
        assertEquals(Optional.of("m1"), verbs.get(0).subjectMark());
        assertEquals(Optional.of("m1"), verbs.get(1).subjectMark(), "brought, and then thrown");

        List<SpellAction> renamed = verbs(read("m1 vertere m2 vocant"));
        assertEquals(Optional.of("m2"), renamed.get(1).subjectMark(), "what bore m1 now bears m2");

        List<SpellAction> converted = verbs(read("m1 vertere aqua iactare"));
        assertTrue(converted.get(1).subjectMark().isEmpty(), "converted into water, it is water that is thrown");
        assertEquals(VitaElement.AQUA, converted.get(1).element());
    }

    // ------------------------------------------------------------------ an addon's words

    @Test
    void anAddonsSourceIsReadExplainedAndRunLikeTheBooks() {
        Lexicons.extend(words -> words.read(new StringReader(ADDON)));
        Lexicon lexicon = Lexicons.get();

        SpellActionResult thrown = read("glacies iactare");
        assertTrue(thrown.issues().isEmpty(), thrown.issues().toString());
        assertEquals(VitaElement.AQUA, verbs(thrown).get(0).element(), "ice follows the laws of what it holds most");
        assertEquals("glacies", verbs(thrown).get(0).metadata().get("elementRuneId"));
        assertEquals(VitaElement.AQUA, VitaElement.fromRuneId("glacies"));
        assertEquals(ElementPersistence.PERMANENT, ElementPersistence.of("glacies"));
        assertEquals("minecraft:ice", lexicon.traitsOf("glacies").matterBlock());

        SpellActionResult converted = read("m1 vertere glacies");
        assertTrue(converted.issues().isEmpty(), converted.issues().toString());
        assertEquals("glacies", verbs(converted).get(0).targetRuneId());

        ParserDictionary dictionary = ParserDictionary.load();
        assertTrue(new SpellReading(dictionary).read(List.of("glacies", "iactare")).get(1).role().contains("o gelo"));
        SpellDescription.Text text = new SpellDescription(dictionary, Map.of()).describe(List.of(List.of("glacies", "iactare")));
        assertEquals("Estilhaço de gelo", text.name());
        assertTrue(text.paragraphs().get(0).contains("UMU de gelo"), text.paragraphs().toString());

        LigabisGrammar.Parsed bond = LigabisGrammar.tryParse(List.of("glacies", "m1", "ligabis"), lexicon::isRune)
                .orElseThrow();
        assertEquals(Aspect.AQUA, bond.aspect(), "and it binds what it declares");
    }

    @Test
    void anAddonsVerbSharesAnOperationAndItsFlow() {
        Lexicons.extend(words -> words.read(new StringReader(ADDON)));
        Lexicon lexicon = Lexicons.get();

        SpellActionResult hurled = read("igni tenet quantum chronos 0 proicere");
        assertTrue(hurled.issues().isEmpty(), hurled.issues().toString());
        assertEquals(Optional.of("project"), lexicon.operationOf("proicere"), "the world runs it as it runs iactare");
        assertEquals(3.0D, new SpellCostProcessor().computeTotalCost(read("igni proicere").actions()) - 1.0D, 1.0E-9);

        // It spends the fire taken from the world, condensed: the flow knows it only by its role.
        FakeFlow.Ledger ledger = new FakeFlow.Ledger(VitaElement.IGNI, 3.0D, hurled.vertereRequests());
        ledger.primarySource = hurled.primarySource();
        FakeFlow.World world = new FakeFlow.World(ledger);
        new FlowInterpreter(lexicon).run(ledger, hurled.actions(), world);
        assertNotNull(world.last("captureAll"));
        assertEquals(List.of("proicere"), world.performed());
        assertTrue(world.last("perform").action().intensity() > 1.0D);
    }

    @Test
    void anAddonsFilterAndShorthandsAreReadByTheSameRules() {
        Lexicons.extend(words -> words.read(new StringReader(ADDON)));

        SpellAction held = verbs(read("igni tempus 5 iactare")).get(0);
        assertEquals(5.0D, held.seconds().orElseThrow(), 1.0E-9);
        assertTrue(read("igni tempus -2 iactare").issues().stream().anyMatch(issue -> issue.contains("outro volume")));

        assertEquals(read("igni vocant").actions(), read("ignivocare").actions());
        assertEquals(read("igni vocant").actions(), read("flammavocare").actions(), "a shorthand of a shorthand");
    }

    @Test
    void anAddonCanGiveAnOldRuneANewMeaning() {
        Lexicons.extend(words -> words.rune(Lexicons.builtIn().rune("transiectio").orElseThrow().toBuilder()
                .expansion(List.of("vertere", "@", "vocant")).build()));
        assertEquals(read("igni vertere aqua vocant").actions(), read("igni transiectio aqua").actions());
    }

    @Test
    void anAddonCanBringASourceInCode() {
        Lexicons.extend(words -> words.rune(Rune.builder("umbra", WordClass.SOURCE)
                .origin(com.elderlexicon.mod.magic.lexicon.Origin.FUSION)
                .name("sombra")
                .source(new SourceSpec(null, Map.of(VitaElement.AURA, 0.6, VitaElement.FIRMO, 0.4), null,
                        Traits.NONE))
                .build()));
        assertEquals(VitaElement.AURA, VitaElement.fromRuneId("umbra"));
        assertTrue(read("umbra m1 ubis vocant").issues().isEmpty());
    }

    @Test
    void aLexiconThatDoesNotHoldTogetherIsRefused() {
        Lexicon before = Lexicons.get();
        assertThrows(IllegalStateException.class, () -> Lexicons.extend(words -> words.rune(
                Rune.builder("lux", WordClass.VERB).glyph("C")
                        .verb(new VerbSpec("shine", null, 0.0D, null, null, false, null, false, null, null, false))
                        .build())),
                "C is igni's glyph");
        assertThrows(IllegalStateException.class, () -> Lexicons.extend(words -> words.rune(
                Rune.builder("nihil", WordClass.VERB).expansion(List.of("nusquam")).build())),
                "a shorthand of a word the language does not have");
        assertThrows(IllegalStateException.class, () -> Lexicons.extend(words -> words.rune(
                Rune.builder("ouroboros", WordClass.VERB).expansion(List.of("ouroboros")).build())),
                "a shorthand of itself");
        assertEquals(before.runes().size(), Lexicons.get().runes().size(), "what was refused left no trace");
    }

    @Test
    void aLexiconCanBeBuiltFromNothingButData() {
        Lexicon tiny = LexiconBuilder.empty().read(new StringReader("""
                {"defaultSource": "lumen", "runes": {
                  "lumen": {"class": "source", "essence": {"igni": 1}},
                  "mittere": {"class": "verb", "operation": "project"}
                }}
                """)).build();
        SpellActionResult result = new SpellGrammar(tiny).read(List.of("mittere"));
        assertTrue(result.issues().isEmpty(), result.issues().toString());
        assertEquals(VitaElement.IGNI, result.actions().get(0).element(), "the default source fills the gap");
    }
}
