package com.elderlexicon.mod.spelling.render;

import com.elderlexicon.mod.spelling.client.RuneSgaMapper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Paints spell text onto a vanilla map's saved data so it renders like a filled map.
 */
public final class SpellMapHelper {

    private static final byte BG = MapColor.SAND.getPackedId(MapColor.Brightness.NORMAL);
    private static final byte BORDER = MapColor.COLOR_BROWN.getPackedId(MapColor.Brightness.LOWEST);
    private static final byte INK = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.HIGH);

    private static final Map<Character, byte[]> FONT = buildFont();
    private static final Map<Character, byte[]> SGA_FONT = buildSgaFont();

    private SpellMapHelper() {
    }

    public static void paintSpell(ServerLevel level, ItemStack mapStack, String text) {
        MapItemSavedData data = getSavedData(mapStack, level);
        if (data == null) {
            return;
        }
        fillBackground(data);
        drawText(data, text == null ? "" : text);
        data.setDirty();
    }

    public static Integer getMapId(ItemStack mapStack) {
        return MapItem.getMapId(mapStack);
    }

    public static MapItemSavedData getSavedData(ItemStack mapStack, Level level) {
        Integer mapId = getMapId(mapStack);
        return mapId == null || level == null ? null : MapItem.getSavedData(mapId, level);
    }

    private static void fillBackground(MapItemSavedData data) {
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                byte color = BG;
                if (x < 4 || x > 123 || y < 4 || y > 123) {
                    color = BORDER;
                }
                data.setColor(x, y, color);
            }
        }
    }

    private static void drawText(MapItemSavedData data, String text) {
        if (text.isBlank()) {
            return;
        }
        String[] lines = text.split("\\R");
        int maxLines = Math.min(8, lines.length);
        int startY = 20;
        for (int i = 0; i < maxLines; i++) {
            String line = sanitizeLine(lines[i]);
            drawLine(data, line, startY + i * 12);
        }
    }

    private static void drawLine(MapItemSavedData data, String line, int y) {
        String glyphLine = translateToGlyphs(line);
        int maxChars = Math.min(22, glyphLine.length());
        int cursorX = 6; // left aligned with small margin
        for (int i = 0; i < maxChars; i++) {
            char ch = glyphLine.charAt(i);
            byte[] glyph = SGA_FONT.getOrDefault(ch, FONT.getOrDefault(ch, FONT.get('?')));
            blitChar(data, glyph, cursorX, y);
            cursorX += 6;
        }
    }

    private static void blitChar(MapItemSavedData data, byte[] glyph, int x, int y) {
        if (glyph == null) {
            return;
        }
        for (int row = 0; row < 7; row++) {
            byte rowBits = glyph[row];
            for (int col = 0; col < 5; col++) {
                if ((rowBits & (1 << (4 - col))) != 0) {
                    int px = x + col;
                    int py = y + row;
                    if (px >= 0 && px < 128 && py >= 0 && py < 128) {
                        data.setColor(px, py, INK);
                    }
                }
            }
        }
    }

    private static String sanitizeLine(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String upper = trimmed.toUpperCase();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (FONT.containsKey(c)) {
                builder.append(c);
            } else {
                builder.append('?');
            }
        }
        return builder.toString();
    }

    private static String translateToGlyphs(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        String[] tokens = line.split("\\s+");
        StringJoiner joiner = new StringJoiner(" ");
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String cleaned = token.replaceAll("[^A-Za-z0-9_]", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            var glyph = RuneSgaMapper.glyphForRune(cleaned);
            if (glyph.isPresent()) {
                joiner.add(String.valueOf(glyph.get()));
            } else {
                joiner.add(cleaned.toUpperCase());
            }
        }
        return joiner.toString();
    }

    private static Map<Character, byte[]> buildFont() {
        Map<Character, byte[]> map = new HashMap<>();
        // 5x7 glyphs, bits from MSB to LSB left-to-right.
        map.put('A', new byte[]{0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001});
        map.put('B', new byte[]{0b11110, 0b10001, 0b11110, 0b10001, 0b10001, 0b10001, 0b11110});
        map.put('C', new byte[]{0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110});
        map.put('D', new byte[]{0b11100, 0b10010, 0b10001, 0b10001, 0b10001, 0b10010, 0b11100});
        map.put('E', new byte[]{0b11111, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000, 0b11111});
        map.put('F', new byte[]{0b11111, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000, 0b10000});
        map.put('G', new byte[]{0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01110});
        map.put('H', new byte[]{0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001, 0b10001});
        map.put('I', new byte[]{0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110});
        map.put('J', new byte[]{0b00001, 0b00001, 0b00001, 0b00001, 0b10001, 0b10001, 0b01110});
        map.put('K', new byte[]{0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001});
        map.put('L', new byte[]{0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111});
        map.put('M', new byte[]{0b10001, 0b11011, 0b10101, 0b10001, 0b10001, 0b10001, 0b10001});
        map.put('N', new byte[]{0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001});
        map.put('O', new byte[]{0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110});
        map.put('P', new byte[]{0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000});
        map.put('Q', new byte[]{0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101});
        map.put('R', new byte[]{0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001});
        map.put('S', new byte[]{0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110});
        map.put('T', new byte[]{0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100});
        map.put('U', new byte[]{0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110});
        map.put('V', new byte[]{0b10001, 0b10001, 0b10001, 0b01010, 0b01010, 0b00100, 0b00100});
        map.put('W', new byte[]{0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001});
        map.put('X', new byte[]{0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001});
        map.put('Y', new byte[]{0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100});
        map.put('Z', new byte[]{0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111});
        map.put('0', new byte[]{0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110});
        map.put('1', new byte[]{0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110});
        map.put('2', new byte[]{0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111});
        map.put('3', new byte[]{0b11111, 0b00010, 0b00100, 0b00010, 0b00001, 0b10001, 0b01110});
        map.put('4', new byte[]{0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010});
        map.put('5', new byte[]{0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110});
        map.put('6', new byte[]{0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110});
        map.put('7', new byte[]{0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000});
        map.put('8', new byte[]{0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110});
        map.put('9', new byte[]{0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100});
        map.put('?', new byte[]{0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b00000, 0b00100});
        map.put(' ', new byte[]{0, 0, 0, 0, 0, 0, 0});
        return map;
    }

    private static Map<Character, byte[]> buildSgaFont() {
        Map<Character, byte[]> map = new HashMap<>();
        // 5x7 glyphs derived from minecraft's ascii_sga.png (alt font) to match the in-game runes.
        map.put('A', new byte[]{0b00110, 0b01001, 0b01000, 0b01000, 0b01000, 0b01000, 0b11000});
        map.put('B', new byte[]{0b00100, 0b00100, 0b00100, 0b00100, 0b00010, 0b00001, 0b11111});
        map.put('C', new byte[]{0b11000, 0b00000, 0b11000, 0b11000, 0b11111, 0b00111, 0b00111});
        map.put('D', new byte[]{0b11111, 0b00000, 0b00000, 0b11000, 0b00100, 0b00100, 0b00011});
        map.put('E', new byte[]{0b10001, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111});
        map.put('F', new byte[]{0b11111, 0b11111, 0b00000, 0b00000, 0b00000, 0b10101, 0b10101});
        map.put('G', new byte[]{0b00011, 0b00011, 0b00011, 0b11111, 0b00011, 0b00011, 0b00011});
        map.put('H', new byte[]{0b11111, 0b00000, 0b11111, 0b00100, 0b00100, 0b00100, 0b00100});
        map.put('I', new byte[]{0b11111, 0b11111, 0b11111, 0b00000, 0b11111, 0b11111, 0b11111});
        map.put('J', new byte[]{0b11111, 0b11111, 0b00000, 0b11111, 0b00000, 0b11111, 0b11111});
        map.put('K', new byte[]{0b00100, 0b00100, 0b00100, 0b10101, 0b00100, 0b00100, 0b00100});
        map.put('L', new byte[]{0b11000, 0b11011, 0b11000, 0b11000, 0b11000, 0b11011, 0b11000});
        map.put('M', new byte[]{0b10001, 0b00001, 0b00001, 0b00001, 0b00001, 0b00001, 0b11111});
        map.put('N', new byte[]{0b10001, 0b10001, 0b00001, 0b00110, 0b00110, 0b01000, 0b10000});
        map.put('O', new byte[]{0b11111, 0b00001, 0b00001, 0b00110, 0b00110, 0b01000, 0b10000});
        map.put('P', new byte[]{0b11011, 0b00011, 0b11011, 0b11011, 0b11011, 0b11000, 0b11011});
        map.put('Q', new byte[]{0b00100, 0b00000, 0b11111, 0b00001, 0b00001, 0b00001, 0b11111});
        map.put('R', new byte[]{0b10001, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b10001});
        map.put('S', new byte[]{0b11000, 0b11000, 0b11000, 0b11111, 0b00111, 0b00111, 0b00111});
        map.put('T', new byte[]{0b11111, 0b00001, 0b00001, 0b00001, 0b00001, 0b00000, 0b00001});
        map.put('U', new byte[]{0b01010, 0b01010, 0b00000, 0b00000, 0b00000, 0b11111, 0b11111});
        map.put('V', new byte[]{0b00100, 0b00100, 0b00100, 0b00100, 0b11111, 0b00000, 0b11111});
        map.put('W', new byte[]{0b00100, 0b00100, 0b00000, 0b00000, 0b00000, 0b10001, 0b10001});
        map.put('X', new byte[]{0b10001, 0b00010, 0b00010, 0b00100, 0b01000, 0b01000, 0b10000});
        map.put('Y', new byte[]{0b11011, 0b11011, 0b11011, 0b11011, 0b11011, 0b11011, 0b11011});
        map.put('Z', new byte[]{0b00100, 0b01010, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001});
        map.put('0', new byte[]{0b01110, 0b10001, 0b10111, 0b10101, 0b11101, 0b10001, 0b01110});
        map.put('1', new byte[]{0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b11111});
        map.put('2', new byte[]{0b01110, 0b10001, 0b00011, 0b00110, 0b01100, 0b11000, 0b11111});
        map.put('3', new byte[]{0b11111, 0b00010, 0b00100, 0b00010, 0b00001, 0b10001, 0b01110});
        map.put('4', new byte[]{0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010});
        map.put('5', new byte[]{0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110});
        map.put('6', new byte[]{0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110});
        map.put('7', new byte[]{0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000});
        map.put('8', new byte[]{0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110});
        map.put('9', new byte[]{0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100});
        map.put('?', new byte[]{0b01110, 0b10001, 0b00001, 0b00110, 0b00100, 0b00000, 0b00100});
        map.put(' ', new byte[]{0, 0, 0, 0, 0, 0, 0});
        return map;
    }
}
