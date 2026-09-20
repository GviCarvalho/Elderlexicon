package com.elderlexicon.mod.spelling.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/**
 * Converts rune identifiers into Standard Galactic Alphabet glyphs using Minecraft's alt font.
 */
@SuppressWarnings("null")
public final class RuneSgaMapper {

    private static final ResourceLocation SGA_FONT = ResourceLocation.fromNamespaceAndPath("minecraft", "alt");
    private static final Style SGA_STYLE = Style.EMPTY.withFont(SGA_FONT);

    private static final Map<String, Character> RUNE_TO_SGA = Map.ofEntries(
            Map.entry("aqua", 'A'),
            Map.entry("aura", 'B'),
            Map.entry("igni", 'C'),
            Map.entry("firmo", 'D'),
            Map.entry("vis", 'E'),
            Map.entry("exsugat", 'F'),
            Map.entry("ligabis", 'G'),
            Map.entry("vertere", 'H'),
            Map.entry("iactare", 'I'),
            Map.entry("vocant", 'J'),
            Map.entry("reframe", 'K'),
            Map.entry("surgit", 'L'),
            Map.entry("impediunt", 'M'),
            Map.entry("quantum", 'N'),
            Map.entry("chronos", 'O'),
            Map.entry("ubis", 'P'),
            Map.entry("0", 'Q'),
            Map.entry("1", 'R'),
            Map.entry("2", 'S'),
            Map.entry("3", 'T'),
            Map.entry("4", 'U'),
            Map.entry("5", 'V'),
            Map.entry("6", 'W'),
            Map.entry("7", 'X'),
            Map.entry("8", 'Y'),
            Map.entry("9", 'Z')
    );
    private static final Map<Character, String> GLYPH_TO_RUNE = RUNE_TO_SGA.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private RuneSgaMapper() {
    }

    public static Optional<Character> glyphForRune(String runeId) {
        if (runeId == null) {
            return Optional.empty();
        }
        String sanitized = runeId.trim().toLowerCase(Locale.ROOT);
        return Optional.ofNullable(RUNE_TO_SGA.get(sanitized));
    }

    public static Component runeComponent(String runeId) {
        if (runeId == null || runeId.isBlank()) {
            return Component.literal("---");
        }
        return glyphForRune(runeId)
                .<Component>map(ch -> Component.literal(String.valueOf(ch)).withStyle(SGA_STYLE))
                .orElseGet(() -> {
                    String fallback = runeId.toUpperCase(Locale.ROOT);
                    return Component.literal(fallback);
                });
    }

    public static Optional<String> runeForGlyph(char glyph) {
        char normalized = Character.toUpperCase(glyph);
        return Optional.ofNullable(GLYPH_TO_RUNE.get(normalized));
    }

    public static Component sequenceComponent(List<String> runes) {
        return sequenceComponent(runes, " ");
    }

    public static Component sequenceComponent(List<String> runes, String separator) {
        if (runes == null || runes.isEmpty()) {
            return Component.empty();
        }
        String safeSeparator = Objects.requireNonNullElse(separator, " ");
        var composite = Component.literal("");
        boolean first = true;
        for (String rune : runes) {
            if (!first) {
                composite.append(Component.literal(safeSeparator));
            }
            composite.append(runeComponent(rune));
            first = false;
        }
        return composite;
    }
}
