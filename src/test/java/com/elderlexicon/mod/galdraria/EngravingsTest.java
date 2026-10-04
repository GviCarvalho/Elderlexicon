package com.elderlexicon.mod.galdraria;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An engraving is one line of up to ten runes, kept as the grimoire writes it and read as a page is. */
class EngravingsTest {

    @Test
    void onlyTheFirstLineIsEngraved() {
        assertEquals(List.of("igni", "vocant", "iactare"), Engravings.words("  igni  vocant iactare \n aqua vocant"));
        assertEquals(List.of(), Engravings.words("   "));
        assertEquals(List.of(), Engravings.words(null));
    }

    @Test
    void whatIsCarvedIsReadBackAsTheSameSpell() {
        List<String> typed = List.of("igni", "quantum", "20", "v1", "ubis", "vocant");
        String written = Engravings.written(typed);
        assertEquals(typed, Engravings.read(written));
    }

    @Test
    void aWordTheSpiritCannotReadIsLeftBlank() {
        assertEquals(List.of("igni", ""), Engravings.read(Engravings.written(List.of("igni", "!!"))));
    }
}
