package com.elderlexicon.mod.magic;

import com.elderlexicon.mod.magic.lexicon.FilterSpec;
import com.elderlexicon.mod.magic.lexicon.Flow;
import com.elderlexicon.mod.magic.lexicon.Glyphs;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.LexiconBuilder;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Parameter;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import com.elderlexicon.mod.spell.ElementPersistence;
import com.elderlexicon.mod.spell.sight.Revelation;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The mod's own words: what the lexicon says of each rune is what the book and the older code said of it. */
class LexiconTest {

    private static final Lexicon LEXICON = Lexicons.builtIn();

    @Test
    void theLanguageIsSmall() {
        assertEquals(16, LEXICON.runes().size(), "five sources, seven verbs and four filters: the rest is composition");
    }

    @Test
    void everyRuneIsWrittenWithTheGlyphItAlwaysHad() {
        // Pages already written in the grimoire hold these glyphs: they must keep reading the same.
        Map<String, Character> glyphs = new LinkedHashMap<>();
        glyphs.put("aqua", 'A');
        glyphs.put("aura", 'B');
        glyphs.put("igni", 'C');
        glyphs.put("firmo", 'D');
        glyphs.put("vis", 'E');
        glyphs.put("tenet", 'F'); // exsugat's glyph, given to the origin that took its place
        glyphs.put("ligabis", 'G');
        glyphs.put("vertere", 'H');
        glyphs.put("iactare", 'I');
        glyphs.put("vocant", 'J');
        glyphs.put("reframe", 'K');
        glyphs.put("surgit", 'L');
        glyphs.put("impediunt", 'M');
        glyphs.put("quantum", 'N');
        glyphs.put("chronos", 'O');
        glyphs.put("ubis", 'P');
        glyphs.forEach((rune, glyph) -> {
            assertEquals(glyph, Glyphs.glyphForRune(rune).orElseThrow(), rune);
            assertEquals(rune, Glyphs.runeForGlyph(glyph).orElseThrow());
        });
        assertEquals('Q', Glyphs.glyphForRune("0").orElseThrow());
        assertEquals("9", Glyphs.runeForGlyph('Z').orElseThrow());
        for (Rune rune : LEXICON.runes()) {
            assertTrue(rune.glyph().isPresent(), rune.id() + ": every rune of the language has its glyph");
        }
    }

    @Test
    void theDefaultsAreTheBooks() {
        assertEquals("vis", LEXICON.defaultSource().id(), "book 4.2: the spirit fills the gap with mana");
        assertEquals(List.of("igni", "aqua", "aura", "firmo", "impediunt", "vertere", "vocant", "iactare"),
                LEXICON.repertoire());
    }

    @Test
    void verbsHaveTheirRolesInTheFlow() {
        assertEquals(Flow.CONVERT, LEXICON.flowOf("vertere"));
        for (String verb : List.of("iactare", "vocant", "impediunt", "surgit", "ligabis", "reframe")) {
            assertEquals(Flow.SPEND, LEXICON.flowOf(verb), verb);
        }
        for (String verb : List.of("iactare", "impediunt", "vocant")) {
            assertTrue(LEXICON.verb(verb).orElseThrow().reversible(), verb + " has a sense a negative quantity turns");
        }
        for (String verb : List.of("vertere", "surgit", "ligabis", "reframe")) {
            assertFalse(LEXICON.verb(verb).orElseThrow().reversible(), verb + " has no sense to turn");
        }
        for (Rune rune : LEXICON.runes()) {
            if (rune.is(WordClass.VERB)) {
                assertTrue(rune.verb().isPresent(), rune.id() + " must do something");
            }
        }
    }

    @Test
    void costsAreTheOnesTheSpellsAlwaysPaid() {
        assertEquals(2.0D, LEXICON.costOf("iactare"));
        assertEquals(1.0D, LEXICON.costOf("vocant"));
        assertEquals(1.0D, LEXICON.costOf("vertere"));
        assertEquals(0.1D, LEXICON.costOf("ligabis"));
    }

    @Test
    void theOriginTookThePlaceOfTheCapture() {
        FilterSpec tenet = LEXICON.filter("tenet").orElseThrow();
        assertEquals(Parameter.ORIGIN, tenet.parameter());
        assertEquals(FilterSpec.Argument.OPERANDS, tenet.argument());
        for (String gone : List.of("exsugat", "transvocatio", "exhaustio", "exsuctio", "extractio", "exinanitio",
                "orbis")) {
            assertFalse(LEXICON.isRune(gone), gone + " is no longer a word of the language");
            assertTrue(LEXICON.retired(gone).isPresent(), gone + ": the spirit says what to write instead");
        }
        assertTrue(LEXICON.retired("igni").isEmpty(), "a rune is not retired");
        assertTrue(LEXICON.retired("m1").isEmpty(), "nor is a word that never was one");
    }

    @Test
    void theFusedRunesLeftTheLanguage() {
        // docs/particulas-design.md, stage 3: every fused rune goes, and the spirit says what to write instead.
        for (String gone : List.of("vita", "fusus", "caligo", "lutum", "pulvis", "nebula", "fulmen",
                "transiectio", "aversio", "cohaesio", "deflectio", "vinculatio", "evocatio", "compeditio", "coniuratio",
                "vortex", "turbio", "hasta", "cuspis", "murus", "murum", "catena", "vinculum", "sigillum")) {
            assertFalse(LEXICON.isRune(gone), gone + " is no longer a word of the language");
            assertTrue(LEXICON.retired(gone).isPresent(), gone + ": the spirit says what to write instead");
        }
        assertEquals(VitaElement.BALANCED, VitaElement.fromRuneId("m1"), "what is no source is mana");
    }

    @Test
    void aFusedRuneAnAddonBringsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> LexiconBuilder.from(LEXICON).read(new StringReader("""
                {"runes": {"vapor": {"class": "source", "origin": "fusion", "components": ["aqua", "igni"],
                                     "essence": {"aqua": 0.5, "igni": 0.5}}}}
                """)), "a source fused of others");
        assertThrows(IllegalArgumentException.class, () -> LexiconBuilder.from(LEXICON).read(new StringReader("""
                {"runes": {"lancea": {"class": "form", "form": "spear"}}}
                """)), "a form");
        assertThrows(IllegalArgumentException.class, () -> LexiconBuilder.from(LEXICON).read(new StringReader("""
                {"runes": {"iacto": {"class": "verb", "expands": ["vocant", "@", "iactare"]}}}
                """)), "a verb that stands for others");
    }

    @Test
    void sourcesShowThemselvesAsTheyAlwaysDid() {
        for (String permanent : List.of("aqua", "firmo")) {
            assertEquals(ElementPersistence.PERMANENT, ElementPersistence.of(permanent), permanent);
        }
        for (String ephemeral : List.of("igni", "aura", "vis")) {
            assertEquals(ElementPersistence.EPHEMERAL, ElementPersistence.of(ephemeral), ephemeral);
        }
        assertEquals(Revelation.Kind.IGNI, Revelation.Kind.ofSource("igni"));
        assertEquals(Revelation.Kind.AQUA, Revelation.Kind.ofSource("aqua"));
        assertEquals(Revelation.Kind.FIRMO, Revelation.Kind.ofSource("firmo"));
        assertEquals(Revelation.Kind.AURA, Revelation.Kind.ofSource("aura"));
        assertEquals(Revelation.Kind.VIS, Revelation.Kind.ofSource("vis"));
        assertTrue(LEXICON.traitsOf("aura").wind());
        assertTrue(LEXICON.traitsOf("igni").kindles());
        assertFalse(LEXICON.traitsOf("vis").touches());
        assertEquals("minecraft:fire", LEXICON.traitsOf("igni").imageBlock());
        assertEquals("minecraft:water", LEXICON.traitsOf("aqua").imageBlock());
        assertNull(LEXICON.traitsOf("aura").imageBlock(), "air has no matter to show");
    }
}
