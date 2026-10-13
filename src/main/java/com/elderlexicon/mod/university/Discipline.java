package com.elderlexicon.mod.university;

import com.elderlexicon.mod.library.RuneGuides;

import java.util.Locale;
import java.util.Optional;

/**
 * What is taught at a university, each with its master (docs/mago-design.md, 4.4): the four families of the language,
 * and the arts of galdraria, the Vita and Nahvá; and the two chairs of the house, the Archive's and the Rector's.
 */
public enum Discipline {
    FONTES(RuneGuides.Family.FONTS),
    FUNCOES(RuneGuides.Family.FUNCTIONS),
    FILTROS(RuneGuides.Family.FILTERS),
    FATOS(RuneGuides.Family.FACTS),
    GALDRARIA(null),
    VITA(null),
    NAHVA(null),
    ARQUIVO(null),
    REITOR(null);

    private final RuneGuides.Family family;

    Discipline(RuneGuides.Family family) {
        this.family = family;
    }

    /** The family of the language it teaches, if it is one of the four. */
    public Optional<RuneGuides.Family> family() {
        return Optional.ofNullable(family);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Discipline> byId(String id) {
        for (Discipline discipline : values()) {
            if (discipline.id().equals(id)) {
                return Optional.of(discipline);
            }
        }
        return Optional.empty();
    }
}
