package com.elderlexicon.mod.spell.block;

import java.util.ArrayList;
import java.util.List;

/**
 * A grimoire page as a grid: {@link #ROWS} lines, each an independent spell as long as its words go, one word per
 * cell; a cell is a column, and a column is a step of time (the column clock of {@link SpellBlock}).
 * <p>
 * It is kept as the same text the spirit reads: one line per row, words split by spaces, an empty cell written as
 * {@link #EMPTY_CELL} (a word the spirit does not read, so it only takes its column: a pause).
 */
public final class GrimoirePage {

    public static final int ROWS = 10;
    public static final String EMPTY_CELL = ".";

    private GrimoirePage() {
    }

    /** The rows of a page's text: always {@link #ROWS}, each the words of its line ({@code ""} for an empty cell). */
    public static List<List<String>> read(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            for (String line : text.split("\\r?\\n", -1)) {
                if (rows.size() >= ROWS) {
                    break;
                }
                List<String> cells = new ArrayList<>();
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    for (String word : trimmed.split("\\s+")) {
                        cells.add(isEmptyCell(word) ? "" : word);
                    }
                }
                rows.add(cells);
            }
        }
        while (rows.size() < ROWS) {
            rows.add(new ArrayList<>());
        }
        return rows;
    }

    /** The text of a page's rows; empty cells at the end of a row and empty rows at the end of the page are dropped. */
    public static String write(List<List<String>> rows) {
        List<String> lines = new ArrayList<>();
        for (List<String> row : rows) {
            int end = row.size();
            while (end > 0 && row.get(end - 1).isEmpty()) {
                end--;
            }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < end; i++) {
                if (i > 0) {
                    line.append(' ');
                }
                line.append(row.get(i).isEmpty() ? EMPTY_CELL : row.get(i));
            }
            lines.add(line.toString());
        }
        int last = lines.size();
        while (last > 0 && lines.get(last - 1).isEmpty()) {
            last--;
        }
        return String.join("\n", lines.subList(0, last));
    }

    private static boolean isEmptyCell(String word) {
        return EMPTY_CELL.equals(word) || "·".equals(word);
    }
}
