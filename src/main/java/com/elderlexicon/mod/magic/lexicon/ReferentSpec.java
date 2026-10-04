package com.elderlexicon.mod.magic.lexicon;

import java.util.Locale;
import java.util.Optional;

/**
 * What a referent rune points at in the scene of the casting (docs/fluxo-design.md): a word that stands where a mark
 * would, for something no one had to mark. {@code ego} is the one who casts; {@code ille} is the other one of the
 * happening the line's conditions speak of (whom the mage struck, who hurt the mage, the block broken…).
 *
 * @param refers what it points at
 */
public record ReferentSpec(Refers refers) {

    /** What a referent points at. */
    public enum Refers {
        /** The one who casts. */
        CASTER,
        /** The other one of the most recent happening the line speaks of. */
        SCENE;

        public static Optional<Refers> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "caster" -> Optional.of(CASTER);
                case "scene" -> Optional.of(SCENE);
                default -> Optional.empty();
            };
        }
    }

    public ReferentSpec {
        if (refers == null) {
            throw new NullPointerException("refers");
        }
    }
}
