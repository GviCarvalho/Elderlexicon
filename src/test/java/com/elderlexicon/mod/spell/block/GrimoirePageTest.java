package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.parser.ParserDictionary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrimoirePageTest {

    @Test
    void aPageAlwaysHasTenRowsAndKeepsItsWords() {
        List<List<String>> rows = GrimoirePage.read("E N UQ H A O Q I\n\nC . . J");
        assertEquals(GrimoirePage.ROWS, rows.size());
        assertEquals(List.of("E", "N", "UQ", "H", "A", "O", "Q", "I"), rows.get(0));
        assertTrue(rows.get(1).isEmpty());
        assertEquals(List.of("C", "", "", "J"), rows.get(2), "an empty cell is a pause");
    }

    @Test
    void writingAPageGivesBackTheTextTheSpiritReads() {
        String text = "E N UQ H A O Q I\n\nC . . J";
        assertEquals(text, GrimoirePage.write(GrimoirePage.read(text)));
    }

    @Test
    void emptyEndsAreDropped() {
        List<List<String>> rows = GrimoirePage.read("");
        rows.get(0).addAll(List.of("C", "J", "", ""));
        assertEquals("C J", GrimoirePage.write(rows), "trailing pauses and empty rows add nothing");
    }

    @Test
    void anEmptyCellStillTakesItsColumnWhenCast() {
        SpellBlock block = SpellBlock.parse(GrimoirePage.write(GrimoirePage.read(". . C J")), RuneTokens::normalize);
        assertEquals(3, block.lines().get(0).releasePosition(), "two pauses, then the spell: released two steps later");
    }

    @Test
    void wordsAreReadAsTheSpiritReadsThem() {
        assertEquals("igni", RuneTokens.normalize("C"));
        assertEquals("igni", RuneTokens.normalize("igni"));
        assertEquals("40", RuneTokens.normalize("UQ"));
        assertEquals("pg2", RuneTokens.normalize("pg2"));
        assertEquals("", RuneTokens.normalize("."));
        assertEquals("C", RuneTokens.written("igni"));
        assertEquals("UQ", RuneTokens.written("40"));
        assertEquals("pg2", RuneTokens.written("pg2"));
    }

    @Test
    void eachRuneSaysWhatItDoesInItsSpell() {
        SpellReading reading = new SpellReading(ParserDictionary.load());
        List<String> ids = new ArrayList<>();
        for (String word : "E N UQ H A O Q I".split(" ")) {
            ids.add(RuneTokens.normalize(word));
        }
        List<SpellReading.Word> words = reading.read(ids);
        assertTrue(words.get(0).role().contains("mana do corpo"), words.get(0).role());
        assertTrue(words.get(1).role().contains("40 unidades"), words.get(1).role());
        assertTrue(words.get(3).role().contains("vis em água"), words.get(3).role());
        assertTrue(words.get(5).role().contains("único instante"), words.get(5).role());
        assertTrue(words.get(7).role().contains("Lança a água") && words.get(7).role().contains("esfera"),
                words.get(7).role());
    }

    @Test
    void captureAndPlacesAreExplained() {
        SpellReading reading = new SpellReading(ParserDictionary.load());
        List<SpellReading.Word> capture = reading.read(List.of("igni", "exsugat", "quantum", "iactare"));
        assertTrue(capture.get(1).role().contains("Captura o fogo") && capture.get(1).role().contains("iactare"),
                capture.get(1).role());
        assertTrue(capture.get(2).role().contains("do mundo"), capture.get(2).role());
        List<SpellReading.Word> placed = reading.read(List.of("pg2", "ubis", "igni", "vocant"));
        assertTrue(placed.get(0).role().contains("lugar do ubis"), placed.get(0).role());
        assertTrue(placed.get(3).role().contains("Faz o fogo surgir"), placed.get(3).role());
        List<SpellReading.Word> paused = reading.read(List.of("", "igni", "vocant"));
        assertTrue(paused.get(0).role().contains("passo de tempo"));
    }
}
