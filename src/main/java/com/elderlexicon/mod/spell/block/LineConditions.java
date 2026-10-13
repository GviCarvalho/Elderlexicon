package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.magic.lexicon.ConditionSpec;
import com.elderlexicon.mod.magic.lexicon.FilterSpec;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Parameter;
import com.elderlexicon.mod.magic.lexicon.WordClass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The conditions of a line, read as the spirit reads them (docs/fluxo-design.md). Only the words that belong to the
 * conditions count, in the order they are written:
 * <ul>
 *   <li>conditions side by side hold together ("and"): {@code ferit latet};</li>
 *   <li>{@code aut} ("or") splits them into groups, and the line holds if any group does;</li>
 *   <li>{@code non} ("not") denies the condition right after it;</li>
 *   <li>a <b>measure</b>, a quantity written before a source ({@code quantum 5 firmo}), is a condition when a word of
 *       the conditions or a {@code chronos} with no number stands right after it, or {@code non}/{@code aut} right
 *       before it: the caster's body holds exactly that much of the source, in whole UMU (vis: the mana);</li>
 *   <li>a <b>place</b> written right before a condition ({@code 3 ubis cadit}) belongs to it: falling, with the ground
 *       that many blocks away or nearer;</li>
 *   <li>a {@code chronos} with no number right after a condition or a measure takes its time from them ("while", book
 *       4.3.2): {@code latet chronos aura impediunt}.</li>
 * </ul>
 * A line with no condition always holds. The words that belong to the conditions are no part of the spell itself
 * ({@link #positions}): the grammar leaves them out.
 */
public final class LineConditions {

    /** How long one window of a "while" line lasts, in seconds; it is cast again while its conditions hold. */
    public static final int WINDOW_SECONDS = 1;
    /** The trigger of a measure: it holds on the caster's body, not on a happening. */
    public static final String MEASURE = "measure";
    /** What a landing with a place becomes: falling, the ground near (docs/fluxo-design.md). */
    public static final String FALLING = "falling";
    private static final String LAND = "land";

    private LineConditions() {
    }

    /** What the conditions of a line are measured against: the happenings and the body of the one who casts. */
    public interface Scene {
        /** Whether a happening or a state holds now. */
        boolean recent(String trigger);

        /** How much of a source the caster's body holds, in UMU (vis: the mana). */
        double amount(String source);

        /** How far below the caster the ground is while falling, in blocks; negative when not falling. */
        double groundBelow();
    }

    /** One condition: a happening or state, a measure, or a fall near the ground. */
    public record Term(String trigger, String source, int amount, int distance) {

        static Term trigger(String trigger) {
            return new Term(trigger, null, 0, -1);
        }

        boolean holds(Scene scene) {
            if (MEASURE.equals(trigger)) {
                return Math.round(scene.amount(source)) == amount;
            }
            if (FALLING.equals(trigger)) {
                double below = scene.groundBelow();
                return below >= 0.0D && below <= distance;
            }
            return scene.recent(trigger);
        }
    }

    /** What stands in a line's conditions, in order: a term, "or", or "not". */
    public record Item(ConditionSpec.Logic logic, Term term, int at) {
    }

    /** A line's conditions read: the items in order, and where the words that belong to them stand. */
    public record Parsed(List<Item> items, Set<Integer> positions) {

        public boolean conditional() {
            return items.stream().anyMatch(item -> item.term() != null);
        }
    }

    public static Parsed parse(Lexicon lexicon, List<String> words) {
        List<Item> items = new ArrayList<>();
        Set<Integer> positions = new LinkedHashSet<>();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            Optional<ConditionSpec.Logic> logic = lexicon.logicOf(word);
            if (logic.isPresent()) {
                positions.add(i);
                if (logic.get() != ConditionSpec.Logic.TRIGGER) {
                    items.add(new Item(logic.get(), null, i));
                    continue;
                }
                String trigger = lexicon.triggerOf(word).orElseThrow();
                int distance = placeBefore(lexicon, words, i, positions);
                items.add(new Item(ConditionSpec.Logic.TRIGGER, distance >= 0 && LAND.equals(trigger)
                        ? new Term(FALLING, null, 0, distance) : Term.trigger(trigger), i));
                continue;
            }
            if (isMeasureAt(lexicon, words, i)) {
                positions.add(i);
                positions.add(i + 1);
                positions.add(i + 2);
                int amount = (int) Long.parseLong(words.get(i + 1));
                items.add(new Item(ConditionSpec.Logic.TRIGGER, new Term(MEASURE, words.get(i + 2), amount, -1), i + 2));
                i += 2;
            }
        }
        return new Parsed(items, positions);
    }

    /** Whether the line has any condition. */
    public static boolean conditional(Lexicon lexicon, List<String> words) {
        return parse(lexicon, words).conditional();
    }

    /** Where the words that belong to the conditions stand: no part of the spell itself. */
    public static Set<Integer> positions(Lexicon lexicon, List<String> words) {
        return parse(lexicon, words).positions();
    }

    /** Whether the conditions among {@code words} hold in {@code scene}. */
    public static boolean holds(Lexicon lexicon, List<String> words, Scene scene) {
        List<List<Boolean>> groups = new ArrayList<>();
        List<Boolean> group = new ArrayList<>();
        boolean negate = false;
        for (Item item : parse(lexicon, words).items()) {
            switch (item.logic()) {
                case OR -> {
                    groups.add(group);
                    group = new ArrayList<>();
                    negate = false;
                }
                case NOT -> negate = !negate;
                case TRIGGER -> {
                    group.add(item.term().holds(scene) != negate);
                    negate = false;
                }
            }
        }
        groups.add(group);
        List<List<Boolean>> written = groups.stream().filter(g -> !g.isEmpty()).toList();
        return written.isEmpty() || written.stream().anyMatch(g -> g.stream().allMatch(Boolean::booleanValue));
    }

    /** {@link #holds} where only happenings count (no body, never falling). */
    public static boolean holds(Lexicon lexicon, List<String> words, Predicate<String> now) {
        return holds(lexicon, words, new Scene() {
            @Override
            public boolean recent(String trigger) {
                return now.test(trigger);
            }

            @Override
            public double amount(String source) {
                return 0.0D;
            }

            @Override
            public double groundBelow() {
                return -1.0D;
            }
        });
    }

    /** The happenings the line's conditions speak of (a fall near the ground as {@link #FALLING}). */
    public static Set<String> triggers(Lexicon lexicon, List<String> words) {
        Set<String> triggers = new LinkedHashSet<>();
        for (Item item : parse(lexicon, words).items()) {
            if (item.term() != null && !MEASURE.equals(item.term().trigger())) {
                triggers.add(item.term().trigger());
            }
        }
        return triggers;
    }

    /**
     * Whether the line speaks of {@code trigger} as something that must happen (not denied by {@code non}). A body
     * looked at ({@link #MEASURE}) wakes every line that measures it, denied or not: the measure itself says whether it
     * holds.
     */
    public static boolean wakesFor(Lexicon lexicon, List<String> words, String trigger) {
        if (MEASURE.equals(trigger)) {
            return parse(lexicon, words).items().stream()
                    .anyMatch(item -> item.term() != null && MEASURE.equals(item.term().trigger()));
        }
        boolean negate = false;
        for (Item item : parse(lexicon, words).items()) {
            switch (item.logic()) {
                case OR -> negate = false;
                case NOT -> negate = !negate;
                case TRIGGER -> {
                    if (!negate && trigger.equals(item.term().trigger())) {
                        return true;
                    }
                    negate = false;
                }
            }
        }
        return false;
    }

    /** Where the {@code chronos} that lasts while the conditions hold stands, or -1 when the line has none. */
    public static int whileAt(Lexicon lexicon, List<String> words) {
        Set<Integer> positions = positions(lexicon, words);
        for (int i = 1; i < words.size(); i++) {
            boolean time = lexicon.filter(words.get(i)).map(f -> f.parameter() == Parameter.TIME).orElse(false);
            boolean bare = i + 1 >= words.size() || !isNumber(words.get(i + 1));
            if (time && bare && positions.contains(i - 1)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The words one window of a "while" line is cast with: its {@code chronos} given {@link #WINDOW_SECONDS}, and the
     * measures and places of its conditions left out (with the number given, they would read as part of the spell).
     * The condition words themselves stay, for what the scene words read from them.
     */
    public static List<String> window(Lexicon lexicon, List<String> words) {
        int at = whileAt(lexicon, words);
        if (at < 0) {
            return words;
        }
        Set<Integer> positions = positions(lexicon, words);
        List<String> cast = new ArrayList<>(words.size() + 1);
        for (int i = 0; i < words.size(); i++) {
            if (positions.contains(i) && !lexicon.isCondition(words.get(i))) {
                continue;
            }
            cast.add(words.get(i));
            if (i == at) {
                cast.add(String.valueOf(WINDOW_SECONDS));
            }
        }
        return cast;
    }

    /**
     * Whether {@code quantum N source} at {@code i} is a measure: a condition word or a bare {@code chronos} right after
     * it, or {@code non}/{@code aut} right before it.
     */
    private static boolean isMeasureAt(Lexicon lexicon, List<String> words, int i) {
        if (i + 2 >= words.size()
                || !lexicon.filter(words.get(i)).map(f -> f.parameter() == Parameter.QUANTITY).orElse(false)
                || !isNumber(words.get(i + 1)) || words.get(i + 1).startsWith("-")
                || lexicon.ofClass(words.get(i + 2), WordClass.SOURCE).isEmpty()) {
            return false;
        }
        if (i > 0 && lexicon.logicOf(words.get(i - 1))
                .filter(logic -> logic != ConditionSpec.Logic.TRIGGER).isPresent()) {
            return true;
        }
        if (i + 3 >= words.size()) {
            return false;
        }
        String after = words.get(i + 3);
        boolean bareTime = lexicon.filter(after).map(f -> f.parameter() == Parameter.TIME).orElse(false)
                && (i + 4 >= words.size() || !isNumber(words.get(i + 4)));
        return bareTime || lexicon.isCondition(after);
    }

    /** The distance of a place written right before the condition at {@code i} ({@code 3 ubis cadit}), or -1. */
    private static int placeBefore(Lexicon lexicon, List<String> words, int i, Set<Integer> positions) {
        if (i < 2 || !isNumber(words.get(i - 2)) || words.get(i - 2).startsWith("-")) {
            return -1;
        }
        boolean place = lexicon.filter(words.get(i - 1))
                .map(f -> f.parameter() == Parameter.PLACE && f.argument() == FilterSpec.Argument.OPERANDS)
                .orElse(false);
        if (!place) {
            return -1;
        }
        positions.add(i - 2);
        positions.add(i - 1);
        return (int) Long.parseLong(words.get(i - 2));
    }

    private static boolean isNumber(String word) {
        return word != null && word.matches("-?\\d+");
    }
}
