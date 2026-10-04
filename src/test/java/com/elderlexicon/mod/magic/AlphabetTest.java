package com.elderlexicon.mod.magic;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.spell.mark.NumberGlyphs;
import com.elderlexicon.mod.spelling.render.GlyphBitmaps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The alphabet stays readable as it grows (docs/glifos-design.md). */
class AlphabetTest {

    private static final Lexicon LEXICON = Lexicons.builtIn();
    private static final char FIRST_RUNE = '\uE010';
    private static final char LAST_OWN = '\uE7FF';
    private static final int MIN_DISTANCE = 4;

    /** Every glyph the font draws, letters and the mod's own, by what it writes. */
    private static Map<String, byte[]> written() {
        Map<String, byte[]> glyphs = new LinkedHashMap<>();
        GlyphBitmaps.letters().forEach((c, rows) -> glyphs.put("letter " + c, rows));
        GlyphBitmaps.own().forEach((c, rows) -> glyphs.put(String.format("U+%04X", (int) c), rows));
        return glyphs;
    }

    private static Set<Character> fontChars() throws Exception {
        try (InputStream in = AlphabetTest.class.getResourceAsStream("/assets/elderlexicon/font/sga.json")) {
            assertNotNull(in, "the font elderlexicon:sga");
            JsonObject font = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<Character> chars = new HashSet<>();
            for (var provider : font.getAsJsonArray("providers")) {
                JsonObject p = provider.getAsJsonObject();
                if ("bitmap".equals(p.get("type").getAsString())) {
                    JsonArray rows = p.getAsJsonArray("chars");
                    assertEquals(16, rows.size(), "sixteen rows of the sheet");
                    for (var row : rows) {
                        assertEquals(16, row.getAsString().length(), "sixteen cells a row");
                        row.getAsString().chars().filter(c -> c != 0).forEach(c -> chars.add((char) c));
                    }
                }
            }
            return chars;
        }
    }

    private static int distance(byte[] a, byte[] b) {
        int d = 0;
        for (int y = 0; y < GlyphBitmaps.HEIGHT; y++) {
            d += Integer.bitCount((a[y] ^ b[y]) & 0b11111);
        }
        return d;
    }

    private static byte[] mirror(byte[] rows) {
        byte[] m = new byte[rows.length];
        for (int y = 0; y < rows.length; y++) {
            m[y] = (byte) (Integer.reverse(rows[y] & 0b11111) >>> (32 - GlyphBitmaps.WIDTH));
        }
        return m;
    }

    @Test
    void everyGlyphARuneHasIsDrawn() throws Exception {
        Set<Character> font = fontChars();
        for (Rune rune : LEXICON.runes()) {
            rune.glyph().ifPresent(glyph -> {
                assertEquals(1, glyph.length(), rune.id() + " is written with one glyph");
                char c = glyph.charAt(0);
                assertTrue(GlyphBitmaps.of(c).isPresent(), rune.id() + ": its glyph has pixels");
                if (c >= '\uE000') {
                    assertTrue(font.contains(c), rune.id() + ": its glyph is in the font");
                }
            });
        }
        for (int d = 0; d < 10; d++) {
            assertTrue(font.contains(NumberGlyphs.glyph(d)), "the digit " + d);
        }
    }

    @Test
    void theModsGlyphsKeepToTheirPlaces() {
        for (int d = 0; d < 10; d++) {
            assertEquals('\uE000' + d, NumberGlyphs.glyph(d), "the digits are E000 to E009");
        }
        for (Rune rune : LEXICON.runes()) {
            rune.glyph().map(g -> g.charAt(0)).ifPresent(c -> assertTrue(c >= 'A' && c <= 'Z'
                    || c >= FIRST_RUNE && c <= LAST_OWN, rune.id() + ": a letter, or a place for the mod's runes"));
        }
    }

    @Test
    void noTwoGlyphsAreAlike() {
        Map<String, byte[]> glyphs = written();
        for (var a : glyphs.entrySet()) {
            for (var b : glyphs.entrySet()) {
                if (a.getKey().compareTo(b.getKey()) < 0) {
                    assertNotEquals(0, distance(a.getValue(), b.getValue()), a.getKey() + " and " + b.getKey());
                }
            }
        }
    }

    @Test
    void aNewGlyphStandsApartFromEveryOther() {
        Map<String, byte[]> glyphs = written();
        GlyphBitmaps.own().forEach((c, rows) -> {
            if (c < FIRST_RUNE) {
                return;
            }
            String me = String.format("U+%04X", (int) c);
            glyphs.forEach((other, theirs) -> {
                if (!other.equals(me)) {
                    assertTrue(distance(rows, theirs) >= MIN_DISTANCE, me + " is too close to " + other);
                    assertNotEquals(0, distance(mirror(rows), theirs), me + " is the mirror of " + other);
                }
            });
        });
    }

    @Test
    void theSheetIsWhatTheFontSays() throws Exception {
        try (InputStream in = AlphabetTest.class.getResourceAsStream(GlyphBitmaps.SHEET)) {
            assertNotNull(in, "the sheet of the mod's glyphs");
            BufferedImage sheet = ImageIO.read(in);
            assertEquals(128, sheet.getWidth());
            assertEquals(128, sheet.getHeight());
        }
    }
}
