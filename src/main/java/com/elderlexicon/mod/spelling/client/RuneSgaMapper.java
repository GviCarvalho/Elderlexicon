package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.magic.lexicon.Glyphs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;

/**
 * Converts rune identifiers into Standard Galactic Alphabet glyphs using Minecraft's alt font.
 */
@SuppressWarnings("null")
public final class RuneSgaMapper {

    private static final ResourceLocation SGA_FONT = ResourceLocation.fromNamespaceAndPath("minecraft", "alt");
    private static final Style SGA_STYLE = Style.EMPTY.withFont(SGA_FONT);

    private RuneSgaMapper() {
    }

    /** The glyph a rune is written with, as the lexicon says (a digit is its own glyph). */
    public static Optional<Character> glyphForRune(String runeId) {
        if (runeId == null) {
            return Optional.empty();
        }
        return Glyphs.glyphForRune(runeId.trim().toLowerCase(Locale.ROOT));
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
        return Glyphs.runeForGlyph(glyph);
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
