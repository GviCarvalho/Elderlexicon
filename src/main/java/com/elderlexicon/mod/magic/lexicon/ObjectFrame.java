package com.elderlexicon.mod.magic.lexicon;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a verb takes right after it, and what that word is to it.
 * <ul>
 *   <li>{@code vertere aqua}: a source or a mark after it is its target (what the subject becomes).</li>
 *   <li>{@code m1 transvocatio m2}: a mark after it is the other side of the exchange.</li>
 *   <li>{@code surgit r2}: a mark after it is its subject ("read r2"), and a quantum after that mark is how much of it
 *       is seen ({@code surgit m1 quantum 0}).</li>
 *   <li>{@code … reframe nome}: the word after it is a name.</li>
 * </ul>
 *
 * @param takes                 the kinds of word it takes
 * @param role                  what the word it takes is to it
 * @param immediate             the word must be the very next one ({@code vertere murus aqua} is refused)
 * @param markNeedsMarkedSubject a mark is taken only when the subject is itself a mark ({@code m1 vertere m2})
 * @param optionalWithSubject   it may be left out when a mark is its subject ({@code m1 transvocatio})
 * @param measure               the name of what a quantum written after its subject object measures
 *                              ({@code visibility} for surgit), or null
 * @param refusal               what the spirit says when a source is written where only a mark is taken, or null
 */
public record ObjectFrame(Set<Kind> takes, Role role, boolean immediate, boolean markNeedsMarkedSubject,
                          boolean optionalWithSubject, String measure, String refusal) {

    /** A kind of word a verb can take after it. */
    public enum Kind {
        SOURCE,
        MARK,
        NAME;

        static Kind parse(String raw) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "source" -> SOURCE;
                case "mark" -> MARK;
                case "name" -> NAME;
                default -> throw new IllegalArgumentException("Unknown object kind: " + raw);
            };
        }
    }

    /** What the word taken is to the verb. */
    public enum Role {
        /** What the subject becomes or meets ({@code vertere aqua}, {@code transvocatio m2}). */
        TARGET,
        /** The subject itself, written after the verb ({@code surgit r2}). */
        SUBJECT,
        /** A name the verb gives ({@code reframe fireball}). */
        NAME;

        static Role parse(String raw) {
            if (raw == null) {
                return TARGET;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "subject" -> SUBJECT;
                case "name" -> NAME;
                default -> TARGET;
            };
        }
    }

    public ObjectFrame {
        takes = takes == null || takes.isEmpty() ? EnumSet.noneOf(Kind.class) : EnumSet.copyOf(takes);
        role = role == null ? Role.TARGET : role;
    }

    public boolean takes(Kind kind) {
        return takes.contains(kind);
    }

    /** A target the verb waits for (the old "requires target"): a source or a mark it acts on. */
    public boolean awaitsTarget() {
        return role == Role.TARGET && (takes(Kind.SOURCE) || takes(Kind.MARK));
    }
}
