package com.elderlexicon.mod.magic.lexicon;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * What a source is (book 3): energy showing some of the four aspects. Vis is the four in balance, a fusion is a mixture
 * of its parts ({@code fusus} is half fire, half earth). The element is the aspect whose laws the energy follows in the
 * world; it is written in the lexicon or, if not, the one the essence shows most.
 *
 * @param element the element whose laws it follows
 * @param essence how much of each aspect it holds, summing to 1
 * @param bond    what a {@code ligabis} binds when this source is written as its aspect ({@code firmo}: integrity), or
 *                null when it binds nothing
 * @param traits  how it shows itself in the world
 */
public record SourceSpec(VitaElement element, Map<VitaElement, Double> essence, String bond, Traits traits) {

    public SourceSpec {
        EnumMap<VitaElement, Double> copy = new EnumMap<>(VitaElement.class);
        if (essence != null) {
            copy.putAll(essence);
        }
        essence = Collections.unmodifiableMap(copy);
        traits = traits == null ? Traits.NONE : traits;
        element = element == null ? dominant(essence) : element;
    }

    /** The share of {@code element} in this source (vis holds a quarter of each). */
    public double share(VitaElement aspect) {
        return essence.getOrDefault(aspect, 0.0D);
    }

    /**
     * The element an essence shows most; the four in balance (or nothing at all) is Vis, the centre (book 3.1). Ties go
     * to the order the elements are declared in.
     */
    public static VitaElement dominant(Map<VitaElement, Double> essence) {
        if (essence == null || essence.isEmpty()) {
            return VitaElement.BALANCED;
        }
        VitaElement best = null;
        double bestShare = 0.0D;
        boolean balanced = true;
        Double first = null;
        for (VitaElement aspect : VitaElement.values()) {
            if (aspect == VitaElement.BALANCED) {
                continue;
            }
            double share = essence.getOrDefault(aspect, 0.0D);
            if (first == null) {
                first = share;
            } else if (Math.abs(first - share) > 1.0E-9D) {
                balanced = false;
            }
            if (share > bestShare + 1.0E-9D) {
                best = aspect;
                bestShare = share;
            }
        }
        if (essence.getOrDefault(VitaElement.BALANCED, 0.0D) > bestShare || balanced || best == null) {
            return VitaElement.BALANCED;
        }
        return best;
    }
}
