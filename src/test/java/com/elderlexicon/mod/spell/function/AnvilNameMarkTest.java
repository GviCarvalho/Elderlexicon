package com.elderlexicon.mod.spell.function;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A name given at the anvil is a mark, written the way a spell can write it. */
class AnvilNameMarkTest {

    @Test
    void aNameBecomesTheMarkASpellCanWrite() {
        assertEquals(Optional.of("varinha_do_gui"), MarkHelper.markFromName("Varinha do Gui"));
        assertEquals(Optional.of("v1"), MarkHelper.markFromName("  V1 "));
        assertEquals(Optional.of("bau_do_norte"), MarkHelper.markFromName("Baú do Norte!".replace("ú", "u")));
        assertEquals(Optional.of("baú"), MarkHelper.markFromName("Baú"), "letters of any language are kept");
    }

    @Test
    void aNameThatCannotBeWrittenAsAMarkMakesNone() {
        assertEquals(Optional.empty(), MarkHelper.markFromName("x"), "one letter is a glyph");
        assertEquals(Optional.empty(), MarkHelper.markFromName("42"), "digits alone are a number");
        assertEquals(Optional.empty(), MarkHelper.markFromName("Igni"), "a word of the language is a rune");
        assertEquals(Optional.empty(), MarkHelper.markFromName("!!"));
        assertEquals(Optional.empty(), MarkHelper.markFromName(null));
    }
}
