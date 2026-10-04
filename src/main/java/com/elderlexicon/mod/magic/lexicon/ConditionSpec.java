package com.elderlexicon.mod.magic.lexicon;

import java.util.Locale;
import java.util.Objects;

/**
 * What a condition rune waits for (docs/fluxo-design.md): the happening of the mage's body that must have just come
 * for the line it is written in to hold, and that wakes that line while the mage is in flow. The engine knows a trigger only by its id ({@code attack}, {@code hurt},
 * {@code kill}); the world says when each one happens.
 *
 * @param trigger the happening that wakes the line
 */
public record ConditionSpec(String trigger) {

    public ConditionSpec {
        trigger = Objects.requireNonNull(trigger, "trigger").trim().toLowerCase(Locale.ROOT);
    }
}
