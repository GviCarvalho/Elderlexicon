package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.parser.ParserDictionary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellDescriptionTest {

    private static final ParserDictionary DICTIONARY = ParserDictionary.load();

    private static List<List<String>> page(String... lines) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : lines) {
            List<String> ids = new ArrayList<>();
            for (String word : line.split(" ")) {
                ids.add(RuneTokens.normalize(word));
            }
            rows.add(ids);
        }
        return rows;
    }

    @Test
    void airAndWaterThrownTogetherAreAStorm() {
        SpellDescription.Text text = new SpellDescription(DICTIONARY, Map.of())
                .describe(page("vis vertere aura iactare", "vis vertere aqua iactare"));
        assertEquals("Tempestade", text.name());
        assertTrue(text.paragraphs().get(0).startsWith("Ao mesmo tempo"), text.paragraphs().toString());
        assertTrue(text.paragraphs().get(1).contains("Converte 10 UMU de mana do corpo (a experiência) em ar e lança"),
                text.paragraphs().toString());
        assertTrue(text.notes().stream().anyMatch(note -> note.contains("relâmpago")), text.notes().toString());
    }

    @Test
    void aCondensedCaptureIsDescribedAndNamed() {
        SpellDescription.Text text = new SpellDescription(DICTIONARY, Map.of())
                .describe(page("C F N O Q I"));
        assertEquals("Bola de fogo", text.name());
        assertTrue(text.paragraphs().get(0).startsWith("Captura toda a energia de fogo do mundo ao redor da mão, "
                + "condensa tudo num só ponto e lança na direção mirada."), text.paragraphs().toString());
        assertTrue(text.notes().stream().anyMatch(note -> note.contains("Condensar")));
    }

    @Test
    void linesWithoutASourceAreDescribedToo() {
        SpellDescription.Text text = new SpellDescription(DICTIONARY, Map.of()).describe(page("L", "I", "N UQ"));
        assertTrue(text.paragraphs().contains("• Faz o espírito ler e revelar."), text.paragraphs().toString());
        assertTrue(text.paragraphs().contains("• Lança na direção mirada."), text.paragraphs().toString());
    }

    @Test
    void namedRunesAndPlacesAreRead() {
        SpellDescription.Text text = new SpellDescription(DICTIONARY, Map.of("chama", List.of("igni", "vocant")))
                .describe(page(". . chama", "pg2 ubis aqua vocant"));
        assertTrue(text.paragraphs().stream().anyMatch(p -> p.contains("onde está o que tem a marca “pg2”")),
                text.paragraphs().toString());
        assertTrue(text.paragraphs().stream().anyMatch(p -> p.contains("Solto em 0,50 s")), text.paragraphs().toString());
        assertEquals("Chama", text.name().split(" e ")[0]);
    }
}
