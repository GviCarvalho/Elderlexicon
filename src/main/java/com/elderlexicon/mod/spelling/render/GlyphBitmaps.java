package com.elderlexicon.mod.spelling.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Every glyph of the Old Tongue as 5x7 pixels, for whatever draws runes outside the font (the detached page painted on a
 * map) and for the tests that keep the alphabet readable (docs/glifos-design.md). Each row is a byte, its bit 4 the
 * leftmost column.
 * <p>
 * The letters are the game's own (minecraft:textures/font/ascii_sga.png), copied pixel for pixel, since a dedicated
 * server has no client textures. The mod's own glyphs (the digits, the runes past Z) are read from the sheet the font
 * draws them from, which the mod's jar carries: a glyph added with {@code tools/glyphs/build.py} shows here too.
 */
public final class GlyphBitmaps {

    /** The sheet of the mod's own glyphs: 16 by 16 cells of 8 by 8, from U+E000. */
    public static final String SHEET = "/assets/elderlexicon/textures/font/sga_glyphs.png";
    public static final char FIRST = '\uE000';
    public static final int WIDTH = 5;
    public static final int HEIGHT = 7;

    private static final Map<Character, byte[]> LETTERS = new HashMap<>();
    private static volatile Map<Character, byte[]> own;

    static {
        LETTERS.put('A', new byte[]{0b00110, 0b01001, 0b01000, 0b01000, 0b01000, 0b01000, 0b11000});
        LETTERS.put('B', new byte[]{0b00100, 0b00100, 0b00100, 0b00100, 0b00010, 0b00001, 0b11111});
        LETTERS.put('C', new byte[]{0b10000, 0b00000, 0b10000, 0b10000, 0b11000, 0b01000, 0b01000});
        LETTERS.put('D', new byte[]{0b11111, 0b00000, 0b11000, 0b00100, 0b00011, 0b00000, 0b00000});
        LETTERS.put('E', new byte[]{0b10001, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111});
        LETTERS.put('F', new byte[]{0b11111, 0b00000, 0b10101, 0b00000, 0b00000, 0b00000, 0b00000});
        LETTERS.put('G', new byte[]{0b00100, 0b00100, 0b00100, 0b11100, 0b00100, 0b00100, 0b00100});
        LETTERS.put('H', new byte[]{0b11111, 0b00000, 0b11111, 0b00100, 0b00100, 0b00100, 0b00100});
        LETTERS.put('I', new byte[]{0b10000, 0b10000, 0b10000, 0b00000, 0b10000, 0b10000, 0b10000});
        LETTERS.put('J', new byte[]{0b10000, 0b10000, 0b00000, 0b10000, 0b00000, 0b10000, 0b10000});
        LETTERS.put('K', new byte[]{0b00100, 0b00100, 0b00100, 0b10101, 0b00100, 0b00100, 0b00100});
        LETTERS.put('L', new byte[]{0b10000, 0b10100, 0b10000, 0b10000, 0b10000, 0b10100, 0b10000});
        LETTERS.put('M', new byte[]{0b10001, 0b00001, 0b00001, 0b00001, 0b00001, 0b00001, 0b11111});
        LETTERS.put('N', new byte[]{0b10010, 0b10010, 0b00010, 0b00100, 0b00100, 0b01000, 0b10000});
        LETTERS.put('O', new byte[]{0b11110, 0b00010, 0b00010, 0b00100, 0b00100, 0b01000, 0b10000});
        LETTERS.put('P', new byte[]{0b10100, 0b00100, 0b10100, 0b10100, 0b10100, 0b10000, 0b10100});
        LETTERS.put('Q', new byte[]{0b00100, 0b00000, 0b11111, 0b00001, 0b00001, 0b00001, 0b11111});
        LETTERS.put('R', new byte[]{0b10010, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b10010});
        LETTERS.put('S', new byte[]{0b10000, 0b10000, 0b10000, 0b11000, 0b01000, 0b01000, 0b01000});
        LETTERS.put('T', new byte[]{0b11111, 0b00001, 0b00001, 0b00001, 0b00001, 0b00000, 0b00001});
        LETTERS.put('U', new byte[]{0b00000, 0b00000, 0b01010, 0b00000, 0b11111, 0b00000, 0b00000});
        LETTERS.put('V', new byte[]{0b00100, 0b00100, 0b00100, 0b00100, 0b11111, 0b00000, 0b11111});
        LETTERS.put('W', new byte[]{0b00000, 0b00000, 0b00100, 0b00000, 0b00000, 0b10001, 0b00000});
        LETTERS.put('X', new byte[]{0b10001, 0b00010, 0b00010, 0b00100, 0b01000, 0b01000, 0b10000});
        LETTERS.put('Y', new byte[]{0b10100, 0b10100, 0b10100, 0b10100, 0b10100, 0b10100, 0b10100});
        LETTERS.put('Z', new byte[]{0b00100, 0b01010, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001});
    }

    private GlyphBitmaps() {
    }

    /** The pixels of a glyph: a letter A to Z, or one of the mod's own; empty for anything else. */
    public static Optional<byte[]> of(char glyph) {
        byte[] letter = LETTERS.get(glyph);
        return letter != null ? Optional.of(letter) : Optional.ofNullable(own().get(glyph));
    }

    /** The game's letters A to Z. */
    public static Map<Character, byte[]> letters() {
        return Collections.unmodifiableMap(LETTERS);
    }

    /** The mod's own glyphs, as the sheet draws them. */
    public static Map<Character, byte[]> own() {
        Map<Character, byte[]> loaded = own;
        if (loaded == null) {
            loaded = Collections.unmodifiableMap(load());
            own = loaded;
        }
        return loaded;
    }

    private static Map<Character, byte[]> load() {
        Map<Character, byte[]> glyphs = new HashMap<>();
        try (InputStream in = GlyphBitmaps.class.getResourceAsStream(SHEET)) {
            BufferedImage sheet = in == null ? null : ImageIO.read(in);
            if (sheet == null) {
                return glyphs;
            }
            int cell = sheet.getWidth() / 16;
            for (int index = 0; index < 256; index++) {
                byte[] rows = new byte[HEIGHT];
                boolean ink = false;
                for (int y = 0; y < HEIGHT; y++) {
                    for (int x = 0; x < WIDTH; x++) {
                        int alpha = sheet.getRGB((index % 16) * cell + x, (index / 16) * cell + y) >>> 24;
                        if (alpha > 0) {
                            rows[y] |= (byte) (1 << (WIDTH - 1 - x));
                            ink = true;
                        }
                    }
                }
                if (ink) {
                    glyphs.put((char) (FIRST + index), rows);
                }
            }
        } catch (IOException ignored) {
            // No sheet: the page draws a '?' in place of the mod's own glyphs.
        }
        return glyphs;
    }
}
