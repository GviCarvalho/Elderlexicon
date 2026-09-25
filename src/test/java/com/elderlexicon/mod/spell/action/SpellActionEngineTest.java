package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpellActionEngineTest {

    private static SpellActionEngine engine;

    @BeforeAll
    static void setupEngine() {
        ParserDictionary dictionary = ParserDictionary.load();
        engine = new SpellActionEngine(dictionary);
    }

    @Test
    void generatesSourceAndFunctionActions() {
        SpellActionResult result = engine.generateActions(List.of("igni", "iactare"));

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        assertEquals(2, result.actions().size(), "Should produce source + function actions");

        SpellAction source = result.actions().get(0);
        assertEquals("igni", source.runeId());
        assertEquals(SpellActionType.SOURCE, source.type());
        assertEquals(VitaElement.IGNI, source.element());
        assertTrue(source.shapes().isEmpty());

        SpellAction function = result.actions().get(1);
        assertEquals("iactare", function.runeId());
        assertEquals(SpellActionType.FUNCTION, function.type());
        assertEquals(VitaElement.IGNI, function.element(), "Function should inherit latest element");
        assertNull(function.targetRuneId());
    }

    @Test
    void attachesShapesAndIgnoresImplicitFallback() {
        SpellActionResult result = engine.generateActions(List.of("orbis", "vis", "vocant"));

        assertFalse(result.hasIssues());
        assertEquals(2, result.actions().size(), "Implicit fallback source must not be emitted");

        SpellAction source = result.actions().get(0);
        assertEquals("vis", source.runeId());
        assertEquals(List.of("bolt"), source.shapes());

        SpellAction function = result.actions().get(1);
        assertEquals("vocant", function.runeId());
        assertEquals(List.of("bolt"), function.shapes());
    }

    @Test
    void handlesTargetedFunctions() {
        SpellActionResult result = engine.generateActions(List.of("igni", "vertere", "aqua"));

        assertFalse(result.hasIssues());
        assertEquals(3, result.actions().size());
        assertEquals(1, result.vertereRequests().size());
        VertereRequest request = result.vertereRequests().get(0);
        assertEquals(VitaElement.IGNI, request.source());
        assertEquals(VitaElement.AQUA, request.target());
        assertTrue(request.isViable());

        SpellAction function = result.actions().get(1);
        assertEquals("vertere", function.runeId());
        assertEquals(VitaElement.IGNI, function.element());
        assertEquals("aqua", function.targetRuneId());

        SpellAction targetSource = result.actions().get(2);
        assertEquals("aqua", targetSource.runeId());
        assertEquals(VitaElement.AQUA, targetSource.element());
    }

    @Test
    void vertereWithoutASourceConvertsMana() {
        // Book 4.2: a function with no source is filled with mana, so "vertere aqua vocant" is "vis vertere aqua vocant".
        SpellActionResult result = engine.generateActions(List.of("vertere", "aqua", "vocant"));

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        assertEquals(1, result.vertereRequests().size());
        assertEquals(VitaElement.BALANCED, result.vertereRequests().get(0).source());
        assertEquals(VitaElement.AQUA, result.vertereRequests().get(0).target());
    }

    @Test
    void vertereAfterExsugatConvertsTheCapturedSource() {
        // Book 8.2.1: capture first, then convert.
        SpellActionResult result = engine.generateActions(List.of("igni", "exsugat", "vertere", "aqua"));

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        assertEquals(VitaElement.IGNI, result.vertereRequests().get(0).source());
        assertEquals(VitaElement.AQUA, result.vertereRequests().get(0).target());
    }

    @Test
    void vertereRequiresImmediateTarget() {
        SpellActionResult result = engine.generateActions(List.of("igni", "vertere", "murus", "aqua"));

        assertTrue(result.hasIssues());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("fonte alvo imediatamente após")));
        assertTrue(result.vertereRequests().isEmpty());
        assertEquals(2, result.actions().size(), "Only source runes should produce actions");
    }

    @Test
    void reportsUnknownLexemes() {
        SpellActionResult result = engine.generateActions(List.of("???", "igni"));

        assertTrue(result.hasIssues());
        assertEquals(1, result.issues().size());
        assertTrue(result.issues().get(0).contains("Lexema desconhecido"));
        assertEquals(1, result.actions().size(), "Only valid lexemes should produce actions");
    }
}
