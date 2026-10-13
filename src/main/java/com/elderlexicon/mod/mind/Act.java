package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.ReferentSpec;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * One thing a sentence does, as the mind sees it (docs/feiticeiro-design.md, 3.2): what a verb does (its operation, as
 * the lexicon names it, not the rune), with what energy, toward what, and how. A sentence's acts are its signature.
 *
 * @param operation  the operation of the verb ({@code project}, {@code manifest}, ...): the mind knows what verbs do,
 *                   never which rune says it, so a verb an addon brings is understood as the book's own
 * @param element    the energy in flow when it acts, after what was converted before it
 * @param toward     where it acts
 * @param reversed   turned the other way round by a negative quantity (it pulls, draws in)
 * @param fromWorld  its source comes from the world (or the body aimed at), not from the caster's
 * @param atOnce     released all at once, condensed
 * @param quantity   how much, when written
 * @param seconds    for how long, when written
 * @param visibility how much is seen of its subject, when written ({@code surgit ego quantum 0})
 * @param mark       the mark its subject or target is, when one is written ({@code kador vocant}), or null
 * @param reach      how far along the aim it acts, when written ({@code igni 40 ubis iactare})
 */
public record Act(String operation, VitaElement element, Toward toward, boolean reversed, boolean fromWorld,
                  boolean atOnce, OptionalDouble quantity, OptionalDouble seconds, OptionalDouble visibility,
                  String mark, OptionalDouble reach) {

    /** An act with no reach written. */
    public Act(String operation, VitaElement element, Toward toward, boolean reversed, boolean fromWorld,
               boolean atOnce, OptionalDouble quantity, OptionalDouble seconds, OptionalDouble visibility,
               String mark) {
        this(operation, element, toward, reversed, fromWorld, atOnce, quantity, seconds, visibility, mark,
                OptionalDouble.empty());
    }

    /** An act with no mark written. */
    public Act(String operation, VitaElement element, Toward toward, boolean reversed, boolean fromWorld,
               boolean atOnce, OptionalDouble quantity, OptionalDouble seconds, OptionalDouble visibility) {
        this(operation, element, toward, reversed, fromWorld, atOnce, quantity, seconds, visibility, null);
    }

    /** Where an act is aimed. */
    public enum Toward {
        /** Where the caster aims, or what it aims at: the aim is enough. */
        AIM,
        /** The caster itself ({@code ego}). */
        SELF,
        /** What the verb before produced or moved. */
        RESULT,
        /** A place or a mark written in the sentence. */
        WRITTEN
    }

    /** The act of {@code action}, performed with {@code element} in flow. */
    static Act of(Lexicon lexicon, SpellAction action, VitaElement element) {
        String operation = lexicon.operationOf(action.runeId()).orElse(action.runeId());
        String mark = action.subjectMark().or(action::targetMark)
                .filter(written -> lexicon.referentOf(written).isEmpty()).orElse(null);
        OptionalDouble reach = action.place().filter(place -> place.kind() == SpellPlace.Kind.DISTANCE)
                .map(place -> OptionalDouble.of(place.distance())).orElse(OptionalDouble.empty());
        return new Act(operation, element, toward(lexicon, action), action.reversed(), action.fromWorld(),
                action.atOnce(), action.quantity(), action.seconds(), action.visibility(), mark, reach);
    }

    private static Toward toward(Lexicon lexicon, SpellAction action) {
        String mark = action.subjectMark().or(action::targetMark).orElse(null);
        if (mark != null) {
            return lexicon.referentOf(mark).filter(refers -> refers == ReferentSpec.Refers.CASTER).isPresent()
                    ? Toward.SELF : Toward.WRITTEN;
        }
        // A distance along the aim is still the aim, only farther (or nearer).
        if (action.place().filter(place -> place.kind() != SpellPlace.Kind.DISTANCE).isPresent()) {
            return Toward.WRITTEN;
        }
        return action.chained() ? Toward.RESULT : Toward.AIM;
    }

    /** A short key for it: the same key is the same act. */
    public String key() {
        StringBuilder key = new StringBuilder(operation).append(':').append(element.runeId()).append('>')
                .append(toward.name().toLowerCase(Locale.ROOT));
        if (reversed) {
            key.append(" rev");
        }
        if (fromWorld) {
            key.append(" world");
        }
        if (atOnce) {
            key.append(" once");
        }
        quantity.ifPresent(value -> key.append(" q").append(format(value)));
        seconds.ifPresent(value -> key.append(" t").append(format(value)));
        visibility.ifPresent(value -> key.append(" v").append(format(value)));
        reach.ifPresent(value -> key.append(" r").append(format(value)));
        if (mark != null) {
            key.append(" @").append(mark);
        }
        return key.toString();
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
