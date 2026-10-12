package com.elderlexicon.mod.mind;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A sentence the mind knows (docs/feiticeiro-design.md, 3.2): its words, what its verbs spend, and what it does.
 *
 * @param words the words, as written
 * @param runes the runes among them (no numbers): what one must know to read it
 * @param cost  what its verbs spend, in UMU (what it takes from the world or a body is not counted)
 * @param acts  what it does, in order: its signature
 */
public record Phrase(List<String> words, Set<String> runes, double cost, List<Act> acts) {

    public Phrase {
        words = List.copyOf(words);
        runes = Set.copyOf(runes);
        acts = List.copyOf(acts);
    }

    /** What it does, as a key: two sentences with the same signature do the same thing. */
    public String signature() {
        return acts.stream().map(Act::key).collect(Collectors.joining(" | "));
    }

    /** Whether one who knows {@code known} can read it whole. */
    public boolean readableBy(Set<String> known) {
        return known.containsAll(runes);
    }

    /** The sentence, as written. */
    public String text() {
        return String.join(" ", words);
    }
}
