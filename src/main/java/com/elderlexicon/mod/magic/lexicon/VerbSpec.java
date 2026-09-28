package com.elderlexicon.mod.magic.lexicon;

import java.util.Locale;

/**
 * What a verb does, as data. The {@code operation} names the code that acts in the world (registered by id, so several
 * runes may share one and an addon may bring its own); everything else is how the verb reads in a sentence.
 *
 * @param operation the id of the operation the world runs for it ({@code project}, {@code manifest}, ...)
 * @param flow      what it does to the energy in flow
 * @param cost      its base cost in UMU (book, ReadmeUMU: iactare 2, vocant 1, vertere 1, ligabis 0.1)
 * @param gathering where what it spends is gathered before it acts: before the hand for what is thrown, at the point
 *                  for what is made to appear, in the body otherwise
 * @param object    what it takes after it, or null when it takes nothing
 * @param view      written right before another verb, it only changes what that verb works with: {@code surgit}
 *                  makes it work with the image of its subject ({@code igni surgit vocant})
 * @param sense     what it binds when written as the aspect of a bond ({@code surgit m1 ligabis}: the sight), or null
 * @param binds     it binds: it reads an aspect and marks around it ({@code firmo m1 ligabis m2})
 * @param phrase    the English verb of the transcript ({@code Summon}); null capitalizes the translation
 * @param joiner    the English word between what it converts and its target ({@code to}, {@code with})
 * @param reversible a negative quantity turns it the other way round ({@code m1 quantum -20 iactare} pulls instead of
 *                   pushing); for any other verb the spirit refuses one
 * @param transfers it moves matter rather than spending energy: with a source from the world ({@code firmo tenet
 *                  vocant}) it carries what it takes as it is, instead of the flow capturing it as energy
 */
public record VerbSpec(String operation, Flow flow, double cost, Gathering gathering, ObjectFrame object, boolean view,
                       String sense, boolean binds, String phrase, String joiner, boolean reversible,
                       boolean transfers) {

    /** Where the energy a verb spends is gathered before it acts. */
    public enum Gathering {
        /** Just before the mage's hand, where a shot leaves from. */
        HAND,
        /** Where the verb makes it appear. */
        DESTINATION,
        /** Into the mage. */
        BODY;

        public static Gathering parse(String raw) {
            if (raw == null) {
                return BODY;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "hand" -> HAND;
                case "destination" -> DESTINATION;
                default -> BODY;
            };
        }
    }

    public VerbSpec {
        flow = flow == null ? Flow.SPEND : flow;
        gathering = gathering == null ? Gathering.BODY : gathering;
        cost = Math.max(0.0D, cost);
    }

    /** Whether it takes a target after it (the old "requires target"). */
    public boolean awaitsTarget() {
        return object != null && object.awaitsTarget();
    }

    /** Whether it gives a name to the words after it ({@code reframe}). */
    public boolean names() {
        return object != null && object.takes(ObjectFrame.Kind.NAME);
    }

    /** Whether it takes a mark written after it as its subject ({@code surgit r2}). */
    public boolean takesSubjectAfter() {
        return object != null && object.role() == ObjectFrame.Role.SUBJECT && object.takes(ObjectFrame.Kind.MARK);
    }
}
