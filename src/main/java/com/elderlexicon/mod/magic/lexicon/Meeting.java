package com.elderlexicon.mod.magic.lexicon;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the grimoire may say when elements are released in the same instant (docs/interacoes-design.md). It is only
 * words: what really happens where they meet is up to the laws of nature, which know no pairs.
 *
 * @param elements the elements that meet
 * @param name     the name the grimoire gives a page where they meet ("Tempestade"), or null
 * @param note     what the grimoire notes about their meeting
 */
public record Meeting(Set<VitaElement> elements, String name, String note) {

    public Meeting {
        elements = elements == null || elements.isEmpty() ? EnumSet.noneOf(VitaElement.class) : EnumSet.copyOf(elements);
    }

    /** Whether all of this meeting's elements are among {@code present}. */
    public boolean among(Set<VitaElement> present) {
        return !elements.isEmpty() && present.containsAll(elements);
    }
}
