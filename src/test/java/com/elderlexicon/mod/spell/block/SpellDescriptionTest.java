package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.mark.NumberGlyphs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellDescriptionTest {

    private static final ParserDictionary DICTIONARY = ParserDictionary.load();

    /** A page written as the older pages wrote numbers (Q to Z), read in the new writing. */
    private static List<List<String>> page(String... lines) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : lines) {
            List<String> ids = new ArrayList<>();
            for (String word : NumberGlyphs.upgrade(line).split(" ")) {
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
    void aConditionSaysWhenTheLineHolds() {
        SpellDescription.Text text = new SpellDescription(DICTIONARY, Map.of())
                .describe(page("ferit patitur igni iactare"));
        assertTrue(text.paragraphs().get(0).startsWith(
                        "Se quem conjura acabou de golpear algo e quem conjura acabou de ser ferido: Usa 10 UMU de fogo"),
                text.paragraphs().toString());
    }

    @Test
    void theSceneIsSpokenOfByName() {
        SpellDescription.Text text = new SpellDescription(DICTIONARY, Map.of())
                .describe(page("ferit vis vertere igni ille ubis vocant"));
        assertTrue(text.paragraphs().get(0).contains("onde está o alvo"), text.paragraphs().toString());
        assertTrue(new SpellReading(DICTIONARY).read(List.of("ego", "ubis", "aqua", "vocant")).get(0).role()
                .contains("Quem conjura"), "the tooltip of ego");
    }

    @Test
    void orNotAndWhileAreWrittenOut() {
        SpellDescription.Text either = new SpellDescription(DICTIONARY, Map.of())
                .describe(page("ferit non latet aut patitur igni iactare"));
        assertTrue(either.paragraphs().get(0).startsWith("Se quem conjura acabou de golpear algo e quem conjura não está "
                + "agachado ou quem conjura acabou de ser ferido: "), either.paragraphs().toString());
        SpellDescription.Text lasting = new SpellDescription(DICTIONARY, Map.of())
                .describe(page("latet chronos aura impediunt"));
        assertTrue(lasting.paragraphs().get(0).startsWith("Enquanto quem conjura está agachado, a cada segundo: "),
                lasting.paragraphs().toString());
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
    void aCoreChangedIsDescribedAndNamed() {
        SpellDescription.Text text = new SpellDescription(DICTIONARY, Map.of())
                .describe(page("aqua quantum 16 vertere m1"));
        assertEquals("Transformação", text.name());
        assertTrue(text.paragraphs().get(0).startsWith("Muda o cerne do que tem a marca “m1”: água, 16 das cem partes."),
                text.paragraphs().toString());
        assertTrue(text.notes().stream().anyMatch(note -> note.startsWith("O cerne são cem partes")),
                text.notes().toString());
    }

    @Test
    void theFourPartsOfACodeInOneColumnAreOneTransformation() {
        SpellDescription.Text text = new SpellDescription(DICTIONARY, Map.of()).describe(page(
                "igni quantum 1 vertere m1", "aqua quantum 2 vertere m1", "aura quantum 5 vertere m1",
                "firmo quantum 7 vertere m1"));
        assertEquals("Transformação", text.name(), "nothing is released, so the elements do not meet");
        assertTrue(text.paragraphs().contains("• Muda o cerne do que tem a marca “m1”: terra, 7 das cem partes."),
                text.paragraphs().toString());
        assertEquals(1, text.notes().size(), text.notes().toString());
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
