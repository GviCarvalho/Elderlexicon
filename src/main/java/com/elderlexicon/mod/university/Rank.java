package com.elderlexicon.mod.university;

import com.elderlexicon.mod.mind.Degree;

import java.util.Locale;
import java.util.Optional;

/**
 * The ranks of a university, in the Old Tongue (docs/mago-design.md, 4.2): three of students, then the master's, and
 * the Rector who presides. A caster of a rank is born of the degree that fits it.
 */
public enum Rank {
    /** The one who listens: admitted. */
    AUDITOR(Degree.APPRENTICE),
    /** The one who writes: passed a master's examination. */
    SCRIBA(Degree.MASTER),
    /** The one who reads: passed several, and accepted by a master. */
    LECTOR(Degree.MASTER),
    /** The master: holds a chair. */
    MAGISTER(Degree.ELDER),
    /** Presides over the council. */
    RECTOR(Degree.ELDER);

    private final Degree degree;

    Rank(Degree degree) {
        this.degree = degree;
    }

    /** The degree a caster of this rank is born of. */
    public Degree degree() {
        return degree;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Rank> byId(String id) {
        for (Rank rank : values()) {
            if (rank.id().equals(id)) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }
}
