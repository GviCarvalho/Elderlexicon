package com.elderlexicon.mod.magic;

import com.elderlexicon.mod.magic.grammar.SpellGrammar;
import com.elderlexicon.mod.magic.lexicon.Foci;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** What each focus favours is data now: it must be what the items and woods always favoured. */
class FociTest {

    private static final Lexicon LEXICON = Lexicons.builtIn();

    @Test
    void theFocusItemsBearWhatTheyAlwaysBore() {
        assertEquals(Map.of("firmo", 0.20D), Foci.discountsOf("wand_bone"));
        assertEquals(Map.of("firmo", 0.20D), Foci.discountsOf("improvised_wand_bone"));
        assertEquals(Map.of("aura", 0.10D, "aqua", 0.10D), Foci.discountsOf("wand_bamboo"));
        assertEquals(Map.of("aura", 0.10D, "aqua", 0.10D), Foci.discountsOf("improvised_wand_bamboo"));
        assertEquals(Map.of("igni", 0.20D), Foci.discountsOf("wand_blaze"));
        assertEquals(Map.of("igni", 0.20D), Foci.discountsOf("improvised_wand_blaze"));
        assertEquals(Map.of(), Foci.discountsOf("wand"));
        assertEquals(Map.of("firmo", 0.20D), Foci.discountsOf(" WAND_BONE "));
        assertEquals(Map.of(), Foci.discountsOf("no_such_wand"));
        assertEquals(Map.of(), Foci.discountsOf(null));
    }

    @Test
    void everyWoodKeepsItsCapacityAndWhatItFavours() {
        Map<String, Foci.Wood> woods = Foci.woods();
        assertEquals(Set.of("oak", "birch", "mangrove", "acacia", "dark_oak", "cherry", "spruce", "crimson", "warped"),
                woods.keySet());
        assertWood(woods.get("oak"), 30.0D, Map.of(), Set.of());
        assertWood(woods.get("birch"), 22.0D, Map.of("iactare", 0.10D), Set.of());
        assertWood(woods.get("mangrove"), 24.0D, Map.of("vertere", 0.10D), Set.of());
        assertWood(woods.get("acacia"), 26.0D, Map.of("impediunt", 0.10D), Set.of());
        assertWood(woods.get("dark_oak"), 27.0D, Map.of("impediunt", 0.05D), Set.of());
        assertWood(woods.get("cherry"), 25.0D, Map.of("vocant", 0.10D), Set.of());
        assertWood(woods.get("spruce"), 23.0D, Map.of("vocant", 0.10D), Set.of()); // it favoured exsugat, now retired
        assertWood(woods.get("crimson"), 40.0D, Map.of(), Set.of("igni", "aura"));
        assertWood(woods.get("warped"), 40.0D, Map.of(), Set.of("firmo", "aqua"));
        assertEquals("material.elderlexicon.wand.dark_oak", woods.get("dark_oak").translationKey());
    }

    @Test
    void aWoodBearsEveryConversionIntoWhatItFavours() {
        Set<String> crimson = Foci.woods().get("crimson").conversions();
        assertEquals(1.0D, discount(List.of("aqua", "vertere", "igni", "iactare"), crimson));
        assertEquals(2.0D, discount(List.of("firmo", "vertere", "igni", "vertere", "aura", "vocant"), crimson));
        assertEquals(0.0D, discount(List.of("aqua", "vertere", "firmo", "iactare"), crimson),
                "a conversion into what the wood does not favour costs what it costs");
        assertEquals(0.0D, discount(List.of("igni", "iactare", "vertere"), crimson),
                "a conversion with nothing after it converts into nothing");
        assertEquals(0.0D, discount(List.of("igni", "iactare"), Set.of()));
        assertEquals(0.0D, discount(List.of(), crimson));
    }

    @Test
    void aShorthandIsFavouredAsTheRunesItStandsFor() {
        // aqua transiectio igni says aqua vertere igni iactare: a crimson wand bears the conversion into fire.
        List<String> words = new SpellGrammar(LEXICON).expand(List.of("aqua", "transiectio", "igni"), new ArrayList<>());
        assertEquals(List.of("aqua", "vertere", "igni", "iactare"), words);
        assertEquals(1.0D, discount(words, Foci.woods().get("crimson").conversions()));
    }

    private static double discount(List<String> runes, Set<String> targets) {
        return Foci.conversionDiscount(runes, targets, LEXICON::costOf);
    }

    private static void assertWood(Foci.Wood wood, double capacity, Map<String, Double> discounts, Set<String> conversions) {
        assertNotNull(wood);
        assertEquals(capacity, wood.capacityBonus(), wood.id());
        assertEquals(discounts, wood.discounts(), wood.id());
        assertEquals(conversions, wood.conversions(), wood.id());
    }
}
