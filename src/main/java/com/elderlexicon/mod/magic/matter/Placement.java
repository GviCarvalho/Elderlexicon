package com.elderlexicon.mod.magic.matter;

import java.util.Optional;

/**
 * How a portion of matter goes into the world, worked out before the world is touched (docs/plano-materia-e-forca.md,
 * stage 3, and docs/particulas-design.md, stage 9). It goes as it is: whatever its particles would do there is for the
 * drives to do once it is in the world, and nothing reacts on the way in.
 * <ul>
 *   <li>What floats (a gas, a plasma) goes into the air there whole, its particles let in where it is put; the form
 *       the table gives it, if any, is only how it is seen.</li>
 *   <li>Near the code of a natural thing that has a block or an item in its state: whole blocks or items of it. Only
 *       whole ones are placed, and what does not make one is left over, never lost (L1).</li>
 *   <li>Anything else is formless matter: blocks that hold its particles exactly, one block for every 4096 of them
 *       (at least one). Nothing of it is left over.</li>
 * </ul>
 *
 * @param matter    what is placed
 * @param substance the natural thing it is; null for a mixture with no name
 * @param form      how it shows there; null when it is formless, or floats with no look of its own
 * @param units     how many blocks or items (formless blocks, when formless); 1 for what floats
 * @param placed    the UMU that goes into the world
 * @param leftover  the UMU that does not make a whole unit, for whoever placed it to keep
 * @param formless  it shows as formless matter
 */
public record Placement(Matter matter, Substance substance, Form form, int units, double placed, double leftover,
                        boolean formless) {

    private static final double EPSILON = 1.0E-9D;

    /** The state it is placed in. */
    public State state() {
        return matter.state();
    }

    /** Whether it is a mixture with no name: near the code of no natural thing. */
    public boolean unnamed() {
        return substance == null;
    }

    /** Whether it floats off into the air (a gas, a plasma) rather than taking room in the world. */
    public boolean floats() {
        return MaterialTable.floats(matter.state());
    }

    /** How {@code matter} goes into the world; nothing, for no matter at all. */
    public static Optional<Placement> plan(MaterialTable table, Matter matter) {
        if (matter.umu() <= EPSILON) {
            return Optional.empty();
        }
        Optional<Substance> substance = matter.substance(table);
        Optional<Form> form = substance.flatMap(found -> table.form(found, matter.state()));
        if (MaterialTable.floats(matter.state())) {
            // It goes into the air whole, where the drives take it; what it looks like there is only its look.
            return Optional.of(new Placement(matter, substance.orElse(null), form.orElse(null), 1, matter.umu(), 0.0D,
                    substance.isEmpty()));
        }
        if (form.isPresent() && form.get().holdsMatter() && form.get().particles() > 0L) {
            Form found = form.get();
            long particles = matter.particles().present().total();
            int units = (int) Math.min(Integer.MAX_VALUE, particles / found.particles());
            double placed = units * found.umu();
            return Optional.of(new Placement(matter, substance.get(), found, units, placed,
                    Math.max(0.0D, matter.umu() - placed), false));
        }
        return Optional.of(formless(matter, substance.orElse(null)));
    }

    /**
     * Formless matter: as many blocks as it fills (at least one), each holding its share of the particles exactly, as a
     * block of the world holds its 4096.
     */
    private static Placement formless(Matter matter, Substance substance) {
        long particles = matter.particles().present().total();
        int units = (int) Math.max(1L, Math.min(Integer.MAX_VALUE,
                Math.round((double) particles / Particles.BLOCK)));
        return new Placement(matter, substance, null, units, matter.umu(), 0.0D, true);
    }
}
