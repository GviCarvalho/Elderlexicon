package com.elderlexicon.mod.magic.lexicon;

import java.util.Locale;
import java.util.Optional;

/**
 * What a condition rune is (docs/fluxo-design.md): a happening of the mage's body that must hold for its line to be
 * cast, and that wakes the line in flow ({@code ferit}: {@code attack}); or a word that joins the others: {@code aut}
 * (or) splits them into groups, {@code non} (not) turns the next one around. The engine knows a trigger only by its id;
 * the world says when each one happens.
 *
 * @param logic   what the word does among the conditions of its line
 * @param trigger the happening it waits for, for a {@link Logic#TRIGGER}; null for the others
 */
public record ConditionSpec(Logic logic, String trigger) {

    /** What a condition word does among the others. */
    public enum Logic {
        /** It holds while its happening is recent, or its state lasts. */
        TRIGGER,
        /** "Or": the conditions before it and after it are two groups; the line holds if either does. */
        OR,
        /** "Not": the condition right after it holds when it does not. */
        NOT;

        public static Optional<Logic> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "or" -> Optional.of(OR);
                case "not" -> Optional.of(NOT);
                default -> Optional.empty();
            };
        }
    }

    public ConditionSpec {
        if (logic == null) {
            throw new NullPointerException("logic");
        }
        if (logic == Logic.TRIGGER) {
            if (trigger == null || trigger.isBlank()) {
                throw new IllegalArgumentException("a trigger condition needs its trigger");
            }
            trigger = trigger.trim().toLowerCase(Locale.ROOT);
        } else {
            trigger = null;
        }
    }

    /** A condition that waits for {@code trigger}. */
    public static ConditionSpec of(String trigger) {
        return new ConditionSpec(Logic.TRIGGER, trigger);
    }
}
