package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spell.block.RuneTokens;
import com.elderlexicon.mod.spell.block.SpellBlock;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Out of a fight a caster writes (docs/feiticeiro-design.md, 2.1): pages of several lines, the best of each kind. */
class PreparationTest {

    private static PhraseCatalog catalog;

    @BeforeAll
    static void make() {
        catalog = PhraseCatalog.of(Lexicons.builtIn());
    }

    private static List<Option> write(Set<String> known, Personality personality) {
        long start = System.nanoTime();
        Preparation preparation = new Preparation(catalog, known, personality);
        int steps = 0;
        while (!preparation.done()) {
            preparation.step();
            steps++;
        }
        List<Option> pages = preparation.pages();
        System.out.println("[preparation] " + personality + " wrote " + pages.size() + " pages in " + steps
                + " steps, " + (System.nanoTime() - start) / 1_000_000L + " ms:");
        pages.forEach(page -> System.out.println("[preparation]   " + page.name() + ":\n" + page.text()));
        return pages;
    }

    @Test
    void anElderWritesAVolleyAndPagesOfSeveralKinds() {
        Set<String> all = Degree.ELDER.draw(Lexicons.builtIn(), RandomSource.create(3L));
        List<Option> pages = write(all, Personality.AGGRESSIVE);
        assertTrue(pages.size() >= 3, "too few pages: " + pages.size());
        assertTrue(pages.stream().anyMatch(page -> page.lines().size() == 3), "no volley");
    }

    @Test
    void everyPageIsWrittenAsTheSpiritReadsIt() {
        Set<String> all = Degree.ELDER.draw(Lexicons.builtIn(), RandomSource.create(5L));
        for (Option page : write(all, Personality.TRICKSTER)) {
            SpellBlock block = SpellBlock.parse(page.text(), RuneTokens::normalize);
            assertEquals(page.lines().size(), block.lines().size(), page.text());
            for (int i = 0; i < page.lines().size(); i++) {
                assertEquals(page.lines().get(i).words(), block.lines().get(i).runeIds(), page.text());
                assertEquals(page.lines().get(i).delaySteps(), block.delaySteps(block.lines().get(i)), page.text());
            }
        }
    }

    @Test
    void anApprenticeWritesOnlyWhatItCanRead() {
        Set<String> few = Set.of("igni", "iactare", "vocant");
        List<Option> pages = write(few, Personality.CAUTIOUS);
        assertFalse(pages.isEmpty());
        assertTrue(pages.stream().allMatch(page -> page.words().stream()
                .filter(Lexicons.builtIn()::isRune).allMatch(few::contains)));
    }
}
