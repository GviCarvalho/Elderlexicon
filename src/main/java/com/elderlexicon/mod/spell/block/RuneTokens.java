package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.magic.lexicon.Glyphs;
import com.elderlexicon.mod.spell.mark.NumberGlyphs;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a word written on a page is read and shown. The same rules serve the spirit reading the page (the server) and
 * the grimoire showing it (the client), so what the mage sees is what gets cast.
 */
public final class RuneTokens {

    /** Letters, digits and the private-use glyphs the mod and its addons draw (docs/glifos-design.md). */
    private static final Pattern RUNE_TOKEN = Pattern.compile("(-?[A-Za-z0-9_\\x{E000}-\\x{F8FF}]+)");

    private RuneTokens() {
    }

    /**
     * The rune id a written word stands for: a number's digits (its glyphs or {@code 20} is {@code "20"}), a rune for
     * its glyph ({@code C} is {@code igni}) or its name, a mark in lower case; empty for anything the spirit does not
     * read (it still takes its column).
     */
    public static String normalize(String token) {
        if (token == null) {
            return "";
        }
        Matcher matcher = RUNE_TOKEN.matcher(token);
        if (!matcher.find()) {
            return "";
        }
        String cleaned = matcher.group(1);
        if (cleaned == null || cleaned.isBlank()) {
            return "";
        }
        // Numbers are written one glyph per digit; a page may still mix glyphs and digits while one is typed.
        Optional<String> number = NumberGlyphs.read(cleaned);
        if (number.isPresent()) {
            return number.get();
        }
        if (cleaned.length() == 1) {
            return Glyphs.runeForGlyph(cleaned.charAt(0)).orElse("");
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    /**
     * How a typed word is kept on the page: a rune's name becomes its glyph ({@code igni} is {@code C}), a number's
     * digits their glyphs ({@link NumberGlyphs}); marks and anything else stay as typed.
     */
    public static String written(String typed) {
        if (typed == null || typed.isEmpty()) {
            return "";
        }
        return Glyphs.glyphForRune(typed).map(String::valueOf).orElseGet(() -> NumberGlyphs.toGlyphs(typed));
    }
}
