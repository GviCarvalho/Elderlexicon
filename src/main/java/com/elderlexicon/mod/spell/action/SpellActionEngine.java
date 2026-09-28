package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.magic.grammar.SpellGrammar;
import com.elderlexicon.mod.parser.ParserDictionary;

import java.util.List;
import java.util.Objects;

/**
 * Converts rune lexemes into structured {@link SpellAction}s without triggering gameplay logic.
 * <p>
 * The reading itself is the {@link SpellGrammar}: it knows no rune by name and reads every sentence by what the
 * lexicon says each word is (docs/magia-modular-design.md). Besides runes, a spell may hold numbers and marks (see
 * {@code docs/marcas-como-runas-design.md}).
 */
public final class SpellActionEngine {

    private final ParserDictionary dictionary;

    public SpellActionEngine(ParserDictionary dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
    }

    /** Reads with the dictionary's lexicon as it is now, so words an addon adds later are understood. */
    public SpellActionResult generateActions(List<String> rawLexemes) {
        return grammar().read(rawLexemes);
    }

    public SpellGrammar grammar() {
        return new SpellGrammar(dictionary.lexicon());
    }
}
