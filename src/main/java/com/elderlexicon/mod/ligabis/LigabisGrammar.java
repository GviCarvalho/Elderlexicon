package com.elderlexicon.mod.ligabis;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.SourceSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reads what a mage wrote: an aspect, then marks separated by {@code ligabis}.
 * <pre>
 *   firmo marca1 ligabis             mirror on marca1
 *   firmo marca1 ligabis marca2      hierarchy, marca1 is the parent and marca2 the child
 *   firmo m1 ligabis m2 ligabis m3   chain m1 to m2 to m3
 * </pre>
 * Marks are free words; a word the language already uses for a rune is never taken for a mark.
 */
public final class LigabisGrammar {

    public record Parsed(Aspect aspect, List<String> marks) { }

    public record Result(Parsed parsed, String error) {

        public boolean ok() {
            return parsed != null;
        }
    }

    private LigabisGrammar() {
    }

    /** @param isRune says whether a word is a rune of the language (so it cannot be a mark) */
    public static Result parse(List<String> lexemes, Predicate<String> isRune) {
        List<String> words = new ArrayList<>();
        if (lexemes != null) {
            for (String lexeme : lexemes) {
                if (lexeme != null && !lexeme.isBlank()) {
                    words.add(lexeme.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        int firstLigabis = indexOfBond(words, 0);
        if (firstLigabis < 0) {
            return error("Falta a runa ligabis.");
        }

        Aspect aspect = null;
        int aspectIndex = -1;
        for (int i = 0; i < firstLigabis && aspect == null; i++) {
            Aspect found = aspectOf(words.get(i));
            if (found != null) {
                aspect = found;
                aspectIndex = i;
            }
        }
        if (aspect == null) {
            return error("Ligabis precisa de um aspecto antes da marca: firmo, igni, aqua, aura ou vis.");
        }

        List<String> marks = new ArrayList<>();
        // Between the aspect and the first ligabis: at most one mark.
        List<String> beforeWords = words.subList(aspectIndex + 1, firstLigabis);
        if (countMarks(beforeWords, isRune) > 1) {
            return error("Use uma unica marca antes de ligabis.");
        }
        String before = firstMark(beforeWords, isRune);
        if (before != null) {
            marks.add(before);
        }

        // After each ligabis: at most one mark, up to the next ligabis.
        int index = firstLigabis;
        while (index < words.size()) {
            int next = indexOfBond(words, index + 1);
            int end = next < 0 ? words.size() : next;
            List<String> segment = words.subList(index + 1, end);
            if (countMarks(segment, isRune) > 1) {
                return error("Use uma unica marca depois de cada ligabis.");
            }
            String mark = firstMark(segment, isRune);
            if (mark != null) {
                marks.add(mark);
            }
            index = end;
        }

        if (marks.isEmpty()) {
            return error("Ligabis precisa de pelo menos uma marca.");
        }
        return new Result(new Parsed(aspect, List.copyOf(marks)), null);
    }

    private static String firstMark(List<String> segment, Predicate<String> isRune) {
        for (String word : segment) {
            if (!isRune.test(word)) {
                return word;
            }
        }
        return null;
    }

    private static int countMarks(List<String> segment, Predicate<String> isRune) {
        int count = 0;
        for (String word : segment) {
            if (!isRune.test(word)) {
                count++;
            }
        }
        return count;
    }

    /** The first bond verb of the lexicon ({@code ligabis}) at or after {@code from}, or -1. */
    private static int indexOfBond(List<String> words, int from) {
        Lexicon lexicon = Lexicons.get();
        for (int index = from; index < words.size(); index++) {
            if (lexicon.isBinding(words.get(index))) {
                return index;
            }
        }
        return -1;
    }

    /** What a source binds when written as the aspect of a bond, as the lexicon says ({@code firmo}: integrity). */
    private static Aspect aspectOf(String word) {
        String bond = Lexicons.get().source(word).map(SourceSpec::bond).orElse(null);
        if (bond == null) {
            return null;
        }
        try {
            return Aspect.valueOf(bond.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static Result error(String message) {
        return new Result(null, message);
    }

    /** Convenience for callers that only care whether it parsed. */
    public static Optional<Parsed> tryParse(List<String> lexemes, Predicate<String> isRune) {
        return Optional.ofNullable(parse(lexemes, isRune).parsed());
    }
}
