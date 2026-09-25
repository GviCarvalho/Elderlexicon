package com.elderlexicon.mod.ligabis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A declared link between marks. With one mark it is a <b>mirror</b> among everything carrying that
 * mark; with two it is a <b>hierarchy</b> where the members of {@code first} are the parents and the
 * members of {@code second} are the children.
 */
public record Link(UUID owner, Aspect aspect, String first, String second) implements Comparable<Link> {

    private static final Comparator<Link> ORDER = Comparator
            .comparing((Link link) -> link.owner.toString())
            .thenComparing(link -> link.aspect)
            .thenComparing(link -> link.first)
            .thenComparing(link -> link.second == null ? "" : link.second);

    public Link {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(aspect, "aspect");
        first = normalize(first);
        if (first == null) {
            throw new IllegalArgumentException("A link needs at least one mark");
        }
        second = normalize(second);
    }

    public boolean isMirror() {
        return second == null;
    }

    /**
     * Turns {@code m1 ligabis m2 ligabis m3} into the hierarchies m1 to m2 and m2 to m3, and a single
     * mark into a mirror.
     */
    public static List<Link> chain(UUID owner, Aspect aspect, List<String> marks) {
        if (marks == null || marks.isEmpty()) {
            throw new IllegalArgumentException("A chain needs at least one mark");
        }
        List<Link> links = new ArrayList<>();
        if (marks.size() == 1) {
            links.add(new Link(owner, aspect, marks.get(0), null));
            return links;
        }
        for (int i = 0; i < marks.size() - 1; i++) {
            links.add(new Link(owner, aspect, marks.get(i), marks.get(i + 1)));
        }
        return links;
    }

    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public int compareTo(Link other) {
        return ORDER.compare(this, other);
    }
}
