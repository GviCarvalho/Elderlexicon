package com.elderlexicon.mod.magic.lexicon;

import java.util.List;
import java.util.Optional;

/**
 * Which fusion two runes make, by the parts the lexicon gives each fusion ({@code fusus} is igni and firmo; a form such
 * as {@code hasta} is iactare and any source). The same two runes in either order make the same fusion, and a rune fused
 * with itself is itself.
 */
public final class Fusions {

    private Fusions() {
    }

    public static Optional<String> fuse(Lexicon lexicon, String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return Optional.empty();
        }
        Optional<Rune> left = lexicon.rune(a);
        Optional<Rune> right = lexicon.rune(b);
        if (left.isEmpty() || right.isEmpty()) {
            return Optional.empty();
        }
        if (left.get().id().equals(right.get().id())) {
            return Optional.of(left.get().id());
        }
        for (Rune fusion : lexicon.runes()) {
            List<String> parts = fusion.components();
            if (parts.size() == 2 && (fits(parts.get(0), left.get()) && fits(parts.get(1), right.get())
                    || fits(parts.get(0), right.get()) && fits(parts.get(1), left.get()))) {
                return Optional.of(fusion.id());
            }
        }
        return Optional.empty();
    }

    /** Whether {@code part} of a fusion is {@code rune} ({@code source} is any source). */
    private static boolean fits(String part, Rune rune) {
        if (LexiconBuilder.ANY_SOURCE.equals(part)) {
            return rune.is(WordClass.SOURCE);
        }
        return part.equals(rune.id());
    }
}
