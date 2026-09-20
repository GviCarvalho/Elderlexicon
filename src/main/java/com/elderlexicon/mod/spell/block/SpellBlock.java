package com.elderlexicon.mod.spell.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * A block of text read as several independent spells (one per non-blank line).
 * <p>
 * Every rune has a position (row + column). The position acts as a shared clock: the spirit
 * finishes reading a spell at the position of its last rune, so lines whose last runes share a
 * position are released together and a line released later starts that many steps after.
 */
public final class SpellBlock {

    public record Rune(String id, int position) { }

    public record Line(int row, List<Rune> runes) {

        public Line {
            runes = List.copyOf(runes);
        }

        public List<String> runeIds() {
            return runes.stream().map(Rune::id).toList();
        }

        /** Position at which the spirit finishes reading this line. */
        public int releasePosition() {
            return runes.get(runes.size() - 1).position();
        }
    }

    private static final SpellBlock EMPTY = new SpellBlock(List.of());

    private final List<Line> lines;

    private SpellBlock(List<Line> lines) {
        this.lines = List.copyOf(lines);
    }

    public static SpellBlock empty() {
        return EMPTY;
    }

    /**
     * @param normalizer maps a raw token to a rune id, or to an empty string when the token is not
     *                   a rune. Discarded tokens still occupy their column.
     */
    public static SpellBlock parse(String text, UnaryOperator<String> normalizer) {
        Objects.requireNonNull(normalizer, "normalizer");
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        String[] rawLines = text.split("\\r?\\n");
        List<Line> lines = new ArrayList<>();
        for (int row = 0; row < rawLines.length; row++) {
            String raw = rawLines[row];
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] tokens = raw.trim().split("\\s+");
            List<Rune> runes = new ArrayList<>(tokens.length);
            for (int col = 0; col < tokens.length; col++) {
                String rune = tokens[col].isBlank() ? "" : normalizer.apply(tokens[col]);
                if (rune != null && !rune.isEmpty()) {
                    runes.add(new Rune(rune, row + col));
                }
            }
            if (!runes.isEmpty()) {
                lines.add(new Line(row, runes));
            }
        }
        return lines.isEmpty() ? EMPTY : new SpellBlock(lines);
    }

    public List<Line> lines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int earliestRelease() {
        return lines.stream().mapToInt(Line::releasePosition).min().orElse(0);
    }

    /** Steps between the first released line and the given one. */
    public int delaySteps(Line line) {
        return line.releasePosition() - earliestRelease();
    }

    public List<String> allRuneIds() {
        return lines.stream().flatMap(line -> line.runeIds().stream()).toList();
    }
}
