package com.elderlexicon.mod.mind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Something a caster can read: a sentence of the catalog, or a page written by hand (several lines, each released at
 * its step). With what it does and what its verbs spend.
 *
 * @param name     what it is called: the page's name, or the sentence itself
 * @param lines    its lines, each with the step it is released at
 * @param acts     everything its lines do
 * @param cost     what its verbs spend, all lines together
 * @param composed whether the mind composed it out of the catalog (what it leaves in its grimoire as its own)
 */
public record Option(String name, List<Line> lines, List<Act> acts, double cost, boolean composed) {

    /** A line of it: its words, and how many steps after the first it is released. */
    public record Line(List<String> words, int delaySteps) {
        public Line {
            words = List.copyOf(words);
        }
    }

    public Option {
        lines = List.copyOf(lines);
        acts = List.copyOf(acts);
    }

    /** A sentence of the catalog, composed by the mind. */
    public static Option of(Phrase phrase) {
        return new Option(phrase.text(), List.of(new Line(phrase.words(), 0)), phrase.acts(), phrase.cost(), true);
    }

    /** A page written by hand: each line imagined as the catalog would; empty when a line of it reads with an issue. */
    public static Optional<Option> page(String name, List<Line> lines) {
        List<Act> acts = new ArrayList<>();
        double cost = 0.0D;
        for (Line line : lines) {
            Optional<Phrase> read = PhraseCatalog.imagine(line.words());
            if (read.isEmpty()) {
                return Optional.empty();
            }
            acts.addAll(read.get().acts());
            cost += read.get().cost();
        }
        return Optional.of(new Option(name, lines, acts, cost, false));
    }

    /** Every word of it, in reading order. */
    public List<String> words() {
        return lines.stream().flatMap(line -> line.words().stream()).toList();
    }

    /**
     * Its text as a grimoire page is written (docs/fluxo-design.md, the column clock): a line a row, each led by as many
     * pauses ({@code .}) as it takes for its last word to fall on the column it is released at.
     */
    public String text() {
        int base = 0;
        for (Line line : lines) {
            base = Math.max(base, line.words().size() - 1 - line.delaySteps());
        }
        List<String> rows = new ArrayList<>();
        for (Line line : lines) {
            int pauses = base + line.delaySteps() - (line.words().size() - 1);
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < pauses; i++) {
                cells.add(".");
            }
            cells.addAll(line.words());
            rows.add(String.join(" ", cells));
        }
        return String.join("\n", rows);
    }

    /** How many steps it takes to release its last line. */
    public int lastStep() {
        return lines.stream().mapToInt(Line::delaySteps).max().orElse(0);
    }
}
