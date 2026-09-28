package com.elderlexicon.mod.spelling.server;

import com.elderlexicon.mod.magic.lexicon.Fusions;
import com.elderlexicon.mod.magic.lexicon.Lexicons;

import java.util.Optional;

/**
 * What the lexicon says of fusions, for the server: whether a word is a rune at all, whether it is one of the runes of
 * the language itself (the grimoire accepts only these), and which fusion two runes make.
 */
final class FusionResolver {

    private FusionResolver() {
    }

    static FusionResolver load() {
        return new FusionResolver();
    }

    boolean isKnownRune(String runeId) {
        return runeId != null && !runeId.isBlank() && Lexicons.get().isRune(runeId);
    }

    boolean isOriginalRune(String runeId) {
        return runeId != null && !runeId.isBlank() && Lexicons.get().isPrimordial(runeId);
    }

    Optional<String> fuse(String a, String b) {
        return Fusions.fuse(Lexicons.get(), a, b);
    }
}
