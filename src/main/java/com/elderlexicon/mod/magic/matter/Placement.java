package com.elderlexicon.mod.magic.matter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a portion of matter goes into the world, worked out before the world is touched (docs/plano-materia-e-forca.md,
 * stages 3 and 5). What it is decides how it shows:
 * <ul>
 *   <li>a substance in a state the data gives a look: whole blocks or items of it, or a show of particles or a creature
 *       for what floats; only whole blocks and items are placed, and what does not make one is left over, never lost
 *       (L1);</li>
 *   <li>a solid or a liquid with no look (molten earth, an amalgam): formless matter, blocks that hold it exactly as it
 *       is, so nothing is left over. An amalgam stays so until it falls apart (L5);</li>
 *   <li>an amalgam that floats (a gas, a plasma) goes into the world falling apart as it goes: each of its primordials is
 *       placed as what it is.</li>
 * </ul>
 *
 * @param matter    what is placed
 * @param substance what it is; null for an amalgam
 * @param form      how it shows there; null when it is formless, or when nothing shows it at all
 * @param units     how many blocks or items (formless blocks, when formless); 1 for particles or a creature
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

    /** Whether it is an amalgam, which falls apart with time (L5). */
    public boolean amalgam() {
        return substance == null;
    }

    /** How {@code matter} goes into the world. */
    public static List<Placement> plan(MaterialTable table, Matter matter) {
        List<Placement> placements = new ArrayList<>();
        if (matter.umu() <= EPSILON) {
            return placements;
        }
        Optional<Substance> substance = matter.substance(table);
        if (substance.isEmpty()) {
            if (MaterialTable.floats(matter.state())) {
                // Floating off, an amalgam comes apart as it goes: each part is placed as what it is.
                for (Matter part : MatterLaws.decay(table, matter)) {
                    placements.addAll(plan(table, part));
                }
                return placements;
            }
            placements.add(formless(table, matter, null));
            return placements;
        }
        Optional<Form> form = table.form(substance.get(), matter.state());
        if (form.isPresent()) {
            placements.add(of(matter, substance.get(), form.get()));
        } else if (!MaterialTable.floats(matter.state())) {
            placements.add(formless(table, matter, substance.get()));
        } else {
            // Nothing shows it at all: it stays with whoever placed it.
            placements.add(new Placement(matter, substance.get(), null, 0, 0.0D, matter.umu(), false));
        }
        return placements;
    }

    private static Placement of(Matter matter, Substance substance, Form form) {
        double umu = matter.umu();
        if (!form.holdsMatter() || form.umu() <= EPSILON) {
            // A gas shown as particles, lightning as a bolt: it goes into the world whole, where it disperses.
            return new Placement(matter, substance, form, 1, umu, 0.0D, false);
        }
        int units = (int) Math.floor(umu / form.umu() + EPSILON);
        double placed = units * form.umu();
        return new Placement(matter, substance, form, units, placed, Math.max(0.0D, umu - placed), false);
    }

    /**
     * Formless matter: as many blocks as it fills (at least one), each holding its share of it exactly, so none of it is
     * left over.
     */
    private static Placement formless(MaterialTable table, Matter matter, Substance substance) {
        int units = (int) Math.max(1L, Math.round(matter.umu() / table.unitOf(matter)));
        return new Placement(matter, substance, null, units, matter.umu(), 0.0D, true);
    }

    /** What all of a plan leaves over. */
    public static double leftover(List<Placement> placements) {
        return placements.stream().mapToDouble(Placement::leftover).sum();
    }
}
