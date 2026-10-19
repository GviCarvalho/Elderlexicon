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
 * @param forms   how it shows in each state; a gas or a plasma with none shows as the nearest state that has some, a
 *                solid or a liquid with none as formless matter
 * @param packing how many of its particles fill the room of one: a block of it holds 4096 times this. Earth, water,
 *                air and fire are 1; what is harder is denser, packed tighter (stone 3, obsidian 100: the block's
 *                hardness over earth's, decided with the author on 17/10/2026). The forms' particles count it.
 * @param origin  who makes it: nature by itself, nature inside something else (a vein of ore), or only hands (planks,
 *                bricks, a furnace). Matter merely near the code of what is not nature's alone is not it, only matter
 *                with its very code (user, 17/10/2026)
 */
public record Substance(String id, String name, Composition recipe, State nature, Map<State, List<Form>> forms,
                        double packing, Origin origin) {

    /** A substance as loose as earth, packed 1, that nature makes. */
    public Substance(String id, String name, Composition recipe, State nature, Map<State, List<Form>> forms) {
        this(id, name, recipe, nature, forms, 1.0D, Origin.NATURE);
    }

    /** A substance nature makes, packed {@code packing}. */
    public Substance(String id, String name, Composition recipe, State nature, Map<State, List<Form>> forms,
                     double packing) {
        this(id, name, recipe, nature, forms, packing, Origin.NATURE);
    }

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
        packing = packing > 0.0D && Double.isFinite(packing) ? packing : 1.0D;
        origin = origin == null ? Origin.NATURE : origin;
    }

    /** Who makes a substance. */
    public enum Origin {
        /** Nature, of the matter alone: rock, soil, ice, a plant. */
        NATURE,
        /** Nature, inside something else: a vein of ore in rock, a creature in stone, a thing buried in sand. */
        VEIN,
        /** Only hands: planks, bricks, a furnace, an ingot. */
        HANDS
    }

    /** Whether nature makes it of the matter alone, so that matter near its code is it. */
    public boolean natural() {
        return origin == Origin.NATURE;
    }

    /**
     * The same substance packed {@code by}: as dense as that, every block and item of it holding that many times the
     * particles its data gives it (the data counts them as if it were as loose as earth).
     */
    public Substance packed(double by) {
        if (!(by > 0.0D) || !Double.isFinite(by)) {
            return this;
        }
        EnumMap<State, List<Form>> scaled = new EnumMap<>(State.class);
        forms.forEach((state, list) -> scaled.put(state, list.stream()
                .map(form -> form.holdsMatter()
                        ? new Form(form.kind(), form.id(), Math.max(1L, Math.round(form.particles() * by)))
                        : form)
                .toList()));
        return new Substance(id, name, recipe, nature, scaled, by, origin);
    }

    /** The block that shows it as a solid, if it has one: the first its data names. */
    public java.util.Optional<String> solidBlock() {
        return declared(State.SOLID).stream().filter(form -> form.kind() == Form.Kind.BLOCK).map(Form::id).findFirst();
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
