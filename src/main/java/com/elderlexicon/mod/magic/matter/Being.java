package com.elderlexicon.mod.magic.matter;

import java.util.Locale;
import java.util.Objects;

/**
 * A kind of being in the table (docs/particulas-design.md, section 6): the share of its body in each primordial, which
 * is its code as a thing's is, and the creature that shows it. Matter held by an anchor of vis is a being, the one
 * whose proportion is nearest ({@link MaterialTable#identify(Particles, boolean)}).
 *
 * @param id     how the data names it ({@code cow})
 * @param name   how the grimoire names it ({@code vaca})
 * @param recipe the share of its body in each primordial
 * @param entity the creature that shows it in the game ({@code minecraft:cow})
 */
public record Being(String id, String name, Composition recipe, String entity) {

    public Being {
        Objects.requireNonNull(recipe, "recipe");
        if (id == null || id.isBlank() || entity == null || entity.isBlank()) {
            throw new IllegalArgumentException("a being needs an id and a creature to show it");
        }
        id = id.trim().toLowerCase(Locale.ROOT);
        entity = entity.trim().toLowerCase(Locale.ROOT);
        name = name == null || name.isBlank() ? id : name;
    }

    /** A body of it in {@code total} whole particles, in its proportion. */
    public Particles code(long total) {
        return Particles.in(recipe, total);
    }
}
