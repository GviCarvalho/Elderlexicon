package com.elderlexicon.mod.magic.matter;

import java.util.Locale;
import java.util.Optional;

/**
 * How a substance shows in the game in one state: a block (a lava source is stone, liquid), an item (a raw iron), the
 * particles of a gas, or a creature. Blocks and items hold matter and can be read back as it; particles and creatures only
 * show it.
 *
 * @param kind what shows it
 * @param id   its id in the game ({@code minecraft:lava}); for particles, {@code block:<id>} is the particles of a block
 * @param umu  the UMU one of it holds (a block holds its substance's unit); 0 for what only shows it
 */
public record Form(Kind kind, String id, double umu) {

    /** What shows a substance. */
    public enum Kind {
        BLOCK,
        ITEM,
        PARTICLE,
        ENTITY;

        public static Optional<Kind> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            for (Kind kind : values()) {
                if (kind.name().toLowerCase(Locale.ROOT).equals(raw.trim().toLowerCase(Locale.ROOT))) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public Form {
        if (kind == null || id == null || id.isBlank()) {
            throw new IllegalArgumentException("a form needs a kind and an id");
        }
        id = id.trim().toLowerCase(Locale.ROOT);
        umu = Math.max(0.0D, umu);
    }

    /** Whether it holds matter that can be read back as it (a block or an item). */
    public boolean holdsMatter() {
        return kind == Kind.BLOCK || kind == Kind.ITEM;
    }
}
