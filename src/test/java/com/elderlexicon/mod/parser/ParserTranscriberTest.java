package com.elderlexicon.mod.parser;

import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionType;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ParserTranscriberTest {

    private static Parser parser;

    @BeforeAll
    static void setupParser() {
        parser = new Parser();
    }

    @Test
    void transcribesSummonSequence() {
        SpellAction source = SpellAction.builder("igni", SpellActionType.SOURCE)
                .element(VitaElement.IGNI)
                .shapes(List.of("bolt"))
                .build();
        SpellAction function = SpellAction.builder("vocant", SpellActionType.FUNCTION)
                .element(VitaElement.IGNI)
                .shapes(List.of("bolt"))
                .build();

        SpellTranscript transcript = parser.transcribeActions(
                List.of(source, function),
                List.of("igni", "vocant"),
                Optional.empty());

        assertTrue(transcript.success());
        assertEquals("Summon fire bolt", transcript.message());
        assertEquals(List.of("igni", "vocant"), transcript.lexemes());
    }

    @Test
    void transcribesTargetedConversion() {
        SpellAction source = SpellAction.builder("igni", SpellActionType.SOURCE)
                .element(VitaElement.IGNI)
                .build();
        SpellAction function = SpellAction.builder("vertere", SpellActionType.FUNCTION)
                .element(VitaElement.IGNI)
                .targetRuneId("aqua")
                .build();
        SpellAction target = SpellAction.builder("aqua", SpellActionType.SOURCE)
                .element(VitaElement.AQUA)
                .build();

        SpellTranscript transcript = parser.transcribeActions(
                List.of(source, function, target),
                List.of("igni", "vertere", "aqua"),
                Optional.empty());

        assertTrue(transcript.success());
        assertEquals("Convert fire to water", transcript.message());
    }

    @Test
    void fallsBackToPlainSourcesWhenNoFunctions() {
        SpellAction source = SpellAction.builder("vis", SpellActionType.SOURCE)
                .element(VitaElement.BALANCED)
                .shapes(List.of("sigil"))
                .build();

        SpellTranscript transcript = parser.transcribeActions(
                List.of(source),
                List.of("vis"),
                Optional.empty());

        assertTrue(transcript.success());
        assertEquals("mana sigil", transcript.message());
    }

    @Test
    void transcribesFusionSourceWithOriginalElement() {
        SpellAction source = SpellAction.builder("caligo", SpellActionType.SOURCE)
                .element(VitaElement.AQUA)
                .putMetadata("elementRuneId", "caligo")
                .build();
        SpellAction function = SpellAction.builder("iactare", SpellActionType.FUNCTION)
                .element(VitaElement.AQUA)
                .putMetadata("elementRuneId", "caligo")
                .build();

        SpellTranscript transcript = parser.transcribeActions(
                List.of(source, function),
                List.of("caligo", "iactare"),
                Optional.empty());

        assertTrue(transcript.success());
        assertEquals("Cast steam", transcript.message());
    }

    @Test
    void usesPronounAfterTransformation() {
        SpellAction source = SpellAction.builder("igni", SpellActionType.SOURCE)
                .element(VitaElement.IGNI)
                .build();
        SpellAction convert = SpellAction.builder("vertere", SpellActionType.FUNCTION)
                .element(VitaElement.IGNI)
                .targetRuneId("aqua")
                .build();
        SpellAction target = SpellAction.builder("aqua", SpellActionType.SOURCE)
                .element(VitaElement.AQUA)
                .build();
        SpellAction followUp = SpellAction.builder("vocant", SpellActionType.FUNCTION)
                .element(VitaElement.AQUA)
                .build();

        SpellTranscript transcript = parser.transcribeActions(
                List.of(source, convert, target, followUp),
                List.of("igni", "vertere", "aqua", "vocant"),
                Optional.empty());

                assertTrue(transcript.success());
                assertEquals("Convert fire to water and Summon water", transcript.message());
    }
}
