package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.magic.lexicon.FilterSpec;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Parameter;
import com.elderlexicon.mod.magic.lexicon.Rune;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * How far what a caster reads gets (docs/feiticeiro-design.md, 3.7). A throw goes twenty blocks along the aim, what is
 * made where it aims twelve, a body is drawn out of from sixteen; a distance written before the verb ({@code igni 40
 * ubis iactare}) takes it that far. A caster that knows how to write a distance writes it when its target stands
 * farther than what it reads would get; one that does not walks up to it.
 */
public final class Reach {

    /** How far a throw goes when no distance is written (the iactare's own range). */
    public static final double THROW = 20.0D;
    /** How far from the caster something is made where it aims (the vocant's own range). */
    public static final double MADE = 12.0D;
    /** How far a body can be drawn out of. */
    public static final double DRAWN = 16.0D;
    /** How far a caster aims, at most, when it knows how to write a distance. */
    public static final double SIGHT = 32.0D;
    /** How much farther than its target a caster writes the distance: it moves while the words are read. */
    private static final double MARGIN = 4.0D;
    /** It writes distances in steps of this many blocks, as a mage would ("forty", not "thirty-seven"). */
    private static final double STEP = 8.0D;

    private Reach() {
    }

    /** How far {@code act} gets: what is written, or what its verb gets by itself; unbounded for what is not aimed. */
    public static double of(Act act) {
        Intent intent = Mind.serves(act);
        if (intent == null || intent.keepsWhole()) {
            return Double.POSITIVE_INFINITY; // what it does to itself, or around itself, is not aimed
        }
        if (intent == Intent.DRAW) {
            return DRAWN;
        }
        if (act.reach().isPresent()) {
            return act.reach().getAsDouble();
        }
        return act.operation().equals("project") ? THROW : MADE;
    }

    /** How far all of {@code option} gets: as far as the shortest of what it aims. */
    public static double of(Option option) {
        return option.acts().stream().mapToDouble(Reach::of).min().orElse(Double.POSITIVE_INFINITY);
    }

    /** Whether all of {@code option} gets to a target {@code distance} away. */
    public static boolean gets(Option option, double distance) {
        return distance <= of(option);
    }

    /** The rune that writes where (ubis), if the lexicon has one and the caster knows it. */
    public static Optional<String> placeRune(Set<String> known) {
        return placeRune(Lexicons.get()).filter(known::contains);
    }

    static Optional<String> placeRune(Lexicon lexicon) {
        return lexicon.runes().stream()
                .filter(rune -> rune.filter().map(FilterSpec::parameter).filter(Parameter.PLACE::equals).isPresent())
                .map(Rune::id).findFirst();
    }

    /** The distance a caster writes for a target {@code distance} away. */
    public static double written(double distance) {
        return Math.ceil((distance + MARGIN) / STEP) * STEP;
    }

    /**
     * {@code option} rewritten to get to a target {@code distance} away, if the caster knows how to write a distance
     * ({@code known}) and what falls short of it is a throw or what is made where it aims: the distance is written
     * before the verb of every line that falls short (a body is drawn out of from where it is drawn, never farther).
     * Empty when it gets there already, cannot be made to, or the grammar does not read it so.
     */
    public static Optional<Option> farther(Option option, double distance, Set<String> known) {
        if (gets(option, distance)) {
            return Optional.empty();
        }
        Optional<String> ubis = placeRune(known);
        if (ubis.isEmpty()) {
            return Optional.empty();
        }
        String far = format(written(distance));
        List<Option.Line> lines = new ArrayList<>();
        for (Option.Line line : option.lines()) {
            Optional<Phrase> read = PhraseCatalog.imagine(line.words());
            if (read.isEmpty()) {
                return Optional.empty();
            }
            if (read.get().acts().stream().allMatch(act -> distance <= of(act))) {
                lines.add(line);
                continue;
            }
            Optional<List<String>> written = write(line.words(), far, ubis.get(), distance);
            if (written.isEmpty()) {
                return Optional.empty();
            }
            lines.add(new Option.Line(written.get(), line.delaySteps()));
        }
        String name = option.lines().size() == 1 ? String.join(" ", lines.get(0).words())
                : option.name() + " (" + far + ")";
        return Option.page(name, lines).filter(page -> gets(page, distance))
                .map(page -> new Option(page.name(), page.lines(), page.acts(), page.cost(), option.composed()));
    }

    /**
     * The words of a line with the distance written where the grammar reads it and it gets {@code distance} away: right
     * before a verb, or before the filters that lead it.
     */
    private static Optional<List<String>> write(List<String> words, String far, String ubis, double distance) {
        for (int at = words.size() - 1; at >= 0; at--) {
            List<String> tried = new ArrayList<>(words.subList(0, at));
            tried.add(far);
            tried.add(ubis);
            tried.addAll(words.subList(at, words.size()));
            Optional<Phrase> read = PhraseCatalog.imagine(tried);
            if (read.isPresent() && read.get().acts().stream().allMatch(act -> distance <= of(act))) {
                return Optional.of(tried);
            }
        }
        return Optional.empty();
    }

    private static String format(double value) {
        return Long.toString((long) value);
    }
}
