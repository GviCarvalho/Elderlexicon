package com.elderlexicon.mod.magic.matter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A kind of matter (docs/plano-materia-e-forca.md, section 1): what it is made of, how it is found in nature and how it
 * shows in each state. Stone melted is still stone, shown as lava: a substance keeps what it is whatever state it is
 * in. A block of any of them holds as much as a block of another, 4096 particles or 16 UMU, in any state
 * (docs/particulas-design.md), so a block of stone melts into a block of lava.
 *
 * @param id     how the data names it ({@code stone})
 * @param name   how the grimoire names it ({@code pedra})
 * @param recipe what it is made of; the four primordials are each all of one aspect
 * @param nature the state it is found in
 * @param forms  how it shows in each state; a gas or a plasma with none shows as the nearest state that has some, a
 *               solid or a liquid with none as formless matter
 */
public record Substance(String id, String name, Composition recipe, State nature, Map<State, List<Form>> forms) {

    public Substance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(nature, "nature");
        name = name == null || name.isBlank() ? id : name;
        EnumMap<State, List<Form>> copy = new EnumMap<>(State.class);
        if (forms != null) {
            forms.forEach((state, list) -> copy.put(state, List.copyOf(list)));
        }
        forms = Collections.unmodifiableMap(copy);
    }

    /** Whether it is one of the four the others are made of: terra, água, ar or fogo. */
    public boolean primordial() {
        return recipe.pureAspect().isPresent();
    }

    /** How it shows in a state, exactly as declared (empty when the data gives none for it). */
    public List<Form> declared(State state) {
        return forms.getOrDefault(state, List.of());
    }

    /**
     * Its code in {@code total} whole particles (docs/particulas-design.md, section 1): a block of it is
     * {@code code(Particles.BLOCK)}, an item {@code code(Particles.ITEM)}.
     */
    public Particles code(long total) {
        return Particles.in(recipe, total);
    }
}
