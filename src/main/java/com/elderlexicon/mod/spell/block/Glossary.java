package com.elderlexicon.mod.spell.block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The runes a grimoire's maker has written in it, in the order they first wrote them: the glossary at the front of the
 * book grows as they use each rune for the first time. Numbers, marks and named runes are not runes of the language
 * and are not recorded here (named runes have their own entries).
 */
public final class Glossary {

    private Glossary() {
    }

    /** The glossary {@code known} grown by every rune of the language written on {@code pages}. */
    public static List<String> grow(List<String> known, List<String> pages, Predicate<String> isRune) {
        Set<String> runes = new LinkedHashSet<>(known == null ? List.of() : known);
        if (pages != null) {
            for (String page : pages) {
                if (page == null) {
                    continue;
                }
                for (String word : page.trim().split("\\s+")) {
                    String id = RuneTokens.normalize(word);
                    if (!id.isEmpty() && isRune.test(id)) {
                        runes.add(id);
                    }
                }
            }
        }
        return new ArrayList<>(runes);
    }
}
