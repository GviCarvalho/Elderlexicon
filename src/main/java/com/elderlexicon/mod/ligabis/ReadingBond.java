package com.elderlexicon.mod.ligabis;

import java.util.Collection;
import java.util.Set;

/**
 * Whether a mage is bound to the scrolls carrying a mark, so the spirit can read them from anywhere in the
 * dimension rather than only within touch: a {@link Aspect#VIS} link between a mark the mage carries and the
 * scrolls' mark, either way round ({@code vis eu ligabis r1}), or a vis mirror on a mark both carry.
 */
public final class ReadingBond {

    private ReadingBond() {
    }

    public static boolean bound(Collection<Link> links, Set<String> casterMarks, String scrollMark) {
        String wanted = Link.normalize(scrollMark);
        if (wanted == null || casterMarks == null || casterMarks.isEmpty()) {
            return false;
        }
        for (Link link : links) {
            if (link.aspect() != Aspect.VIS) {
                continue;
            }
            if (link.isMirror()) {
                if (link.first().equals(wanted) && casterMarks.contains(wanted)) {
                    return true;
                }
                continue;
            }
            boolean casterFirst = casterMarks.contains(link.first()) && link.second().equals(wanted);
            boolean casterSecond = casterMarks.contains(link.second()) && link.first().equals(wanted);
            if (casterFirst || casterSecond) {
                return true;
            }
        }
        return false;
    }
}
