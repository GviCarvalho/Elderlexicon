package com.elderlexicon.mod.magic;

import com.elderlexicon.mod.magic.grammar.SpellGrammar;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.action.SpellActionType;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every spell the mod has documented or tested, and what the spirit reads in it: the steps, their subject, place and
 * filters, the conversions and any complaint. The catalogue ({@code spell-catalogue.txt}) was written from the reading of
 * the grammar before it became generic and checked equal to it (save for what the rework meant to change, marked there),
 * so every spell that could be cast still means what it meant.
 */
class SpellCatalogueTest {

    private static final String CATALOGUE = "/spell-catalogue.txt";

    @Test
    void everyCataloguedSpellStillMeansWhatItMeant() throws IOException {
        SpellGrammar grammar = new SpellGrammar(Lexicons.builtIn());
        List<String> mismatches = new ArrayList<>();
        int read = 0;
        for (String line : lines()) {
            int arrow = line.indexOf(" => ");
            String spell = line.substring(0, arrow).trim();
            String expected = line.substring(arrow + 4).trim();
            String actual = signature(grammar.read(Arrays.asList(spell.split(" "))));
            read++;
            if (!expected.equals(actual)) {
                mismatches.add(spell + "\n    expected: " + expected + "\n    actual:   " + actual);
            }
        }
        assertFalse(read < 150, "the catalogue should hold every documented spell; read " + read);
        assertEquals(List.of(), mismatches, String.join("\n", mismatches));
    }

    private static List<String> lines() throws IOException {
        InputStream stream = SpellCatalogueTest.class.getResourceAsStream(CATALOGUE);
        assertNotNull(stream, CATALOGUE + " is missing");
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /** What the spirit read in a spell, in one line. */
    static String signature(SpellActionResult result) {
        List<String> parts = new ArrayList<>();
        for (SpellAction action : result.actions()) {
            if (action.type() == SpellActionType.SOURCE) {
                parts.add(action.runeId() + (action.shapes().isEmpty() ? "" : "/" + String.join("/", action.shapes())));
                continue;
            }
            Map<String, String> details = new TreeMap<>();
            details.put("el", String.valueOf(action.metadata().get("elementRuneId")));
            if (action.targetRuneId() != null) {
                details.put("to", action.targetRuneId());
            }
            if (!action.shapes().isEmpty()) {
                details.put("form", String.join("/", action.shapes()));
            }
            action.metadata().forEach((key, value) -> {
                if (!"elementRuneId".equals(key)) {
                    details.put(key, value instanceof Double number ? format(number)
                            : value instanceof SpellPlace place ? place.describe() : String.valueOf(value));
                }
            });
            List<String> written = new ArrayList<>();
            details.forEach((key, value) -> written.add(key + "=" + value));
            parts.add(action.runeId() + "[" + String.join(",", written) + "]");
        }
        for (VertereRequest request : result.vertereRequests()) {
            parts.add("~" + request.source() + ">" + request.target() + ":" + format(request.amount()));
        }
        for (String issue : result.issues()) {
            parts.add("!" + issue);
        }
        return String.join(" | ", parts);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.3f", value);
    }
}
