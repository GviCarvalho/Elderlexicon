package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.magic.lexicon.ConditionSpec;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The conditions of a line, read as the spirit reads them (docs/fluxo-design.md). Only the condition words count, in
 * the order they are written, wherever they stand:
 * <ul>
 *   <li>conditions side by side hold together ("and"): {@code ferit latet};</li>
 *   <li>{@code aut} ("or") splits them into groups, and the line holds if any group does:
 *       {@code ferit latet aut patitur} is (struck and sneaking) or hurt;</li>
 *   <li>{@code non} ("not") turns the condition right after it around: {@code non latet};</li>
 *   <li>a {@code chronos} with no number, right after a condition, takes its time from the conditions ("while", book
 *       4.3.2: the barrier lasts "enquanto a chama escolhida estiver acesa"): {@code latet chronos aura impediunt}.</li>
 * </ul>
 * A line with no condition always holds.
 */
public final class LineConditions {

    /** How long one window of a "while" line lasts, in seconds; it is cast again while its conditions hold. */
    public static final int WINDOW_SECONDS = 1;

    private LineConditions() {
    }

    /** Whether the line has any condition word. */
    public static boolean conditional(Lexicon lexicon, List<String> words) {
        return words.stream().anyMatch(lexicon::isCondition);
    }

    /** Whether the conditions among {@code words} hold, given which triggers hold now. */
    public static boolean holds(Lexicon lexicon, List<String> words, Predicate<String> now) {
        List<List<Boolean>> groups = new ArrayList<>();
        List<Boolean> group = new ArrayList<>();
        boolean negate = false;
        for (String word : words) {
            Optional<ConditionSpec.Logic> logic = lexicon.logicOf(word);
            if (logic.isEmpty()) {
                continue;
            }
            switch (logic.get()) {
                case OR -> {
                    groups.add(group);
                    group = new ArrayList<>();
                    negate = false;
                }
                case NOT -> negate = !negate;
                case TRIGGER -> {
                    group.add(now.test(lexicon.triggerOf(word).orElseThrow()) != negate);
                    negate = false;
                }
            }
        }
        groups.add(group);
        List<List<Boolean>> written = groups.stream().filter(g -> !g.isEmpty()).toList();
        return written.isEmpty() || written.stream().anyMatch(g -> g.stream().allMatch(Boolean::booleanValue));
    }

    /** Whether the line speaks of {@code trigger} as something that must happen (not turned around by {@code non}). */
    public static boolean wakesFor(Lexicon lexicon, List<String> words, String trigger) {
        boolean negate = false;
        for (String word : words) {
            Optional<ConditionSpec.Logic> logic = lexicon.logicOf(word);
            if (logic.isEmpty()) {
                continue;
            }
            switch (logic.get()) {
                case OR -> negate = false;
                case NOT -> negate = !negate;
                case TRIGGER -> {
                    if (!negate && lexicon.triggerOf(word).filter(trigger::equals).isPresent()) {
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
        for (int i = 1; i < words.size(); i++) {
            boolean time = lexicon.filter(words.get(i)).map(f -> f.parameter() == Parameter.TIME).orElse(false);
            boolean bare = i + 1 >= words.size() || !words.get(i + 1).matches("-?\\d+");
            if (time && bare && lexicon.isCondition(words.get(i - 1))) {
                return i;
            }
        }
        return -1;
    }

    /** The words one window of a "while" line is cast with: its {@code chronos} given {@link #WINDOW_SECONDS}. */
    public static List<String> window(Lexicon lexicon, List<String> words) {
        int at = whileAt(lexicon, words);
        if (at < 0) {
            return words;
        }
        List<String> cast = new ArrayList<>(words);
        cast.add(at + 1, String.valueOf(WINDOW_SECONDS));
        return cast;
    }
}
