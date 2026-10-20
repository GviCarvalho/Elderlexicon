package com.elderlexicon.mod.university;

import java.util.Locale;
import java.util.Optional;

/**
 * The nine courses of the university (docs/universidade-cursos-design.md), each a specialist of the group the house
 * trains: a student takes one at a time, after the first year. What each one teaches is data ({@link Curriculum}).
 */
public enum Course {
    EVOCACAO,
    GUARDA,
    CONVERSAO,
    VISAO,
    VINCULACAO,
    RITUAL,
    CONTRAMAGIA,
    ONEIROLOGIA,
    ARTIFICIARIA;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Course> byId(String id) {
        for (Course course : values()) {
            if (course.id().equals(id)) {
                return Optional.of(course);
            }
        }
        return Optional.empty();
    }
}
