package com.elderlexicon.mod.magic.matter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a portion of matter goes into the world, worked out before the world is touched (docs/plano-materia-e-forca.md,
 * stage 3). What it is decides how it shows: whole blocks or items for what holds matter, a show of particles or a
 * creature for what does not. An amalgam settles first (L5), each of its parts placed as what it is. Only whole blocks
 * and items are placed; what does not make one is left over, never lost (L1).
 *
 * @param substance what is placed
 * @param state     the state it is placed in
 * @param form      how it shows there
 * @param units     how many blocks or items; 1 for a show of particles or a creature
 * @param placed    the UMU that goes into the world
 * @param leftover  the UMU that does not make a whole unit, for whoever placed it to keep
 */
public record Placement(Substance substance, State state, Form form, int units, double placed, double leftover) {

    private static final double EPSILON = 1.0E-9D;

    /** How {@code matter} goes into the world: one placement for a substance, one for each part of an amalgam. */
    public static List<Placement> plan(MaterialTable table, Matter matter) {
        List<Placement> placements = new ArrayList<>();
        for (Matter part : MatterLaws.decay(table, matter)) {
            Optional<Substance> substance = part.substance(table);
            if (substance.isEmpty() || part.umu() <= EPSILON) {
                continue;
            }
            Optional<Form> form = table.form(substance.get(), part.state());
            if (form.isEmpty()) {
                // Nothing shows it at all: it stays with whoever placed it.
                placements.add(new Placement(substance.get(), part.state(), null, 0, 0.0D, part.umu()));
                continue;
            }
            placements.add(of(substance.get(), part.state(), form.get(), part.umu()));
        }
        return placements;
    }

    private static Placement of(Substance substance, State state, Form form, double umu) {
        if (!form.holdsMatter() || form.umu() <= EPSILON) {
            // A gas shown as particles, lightning as a bolt: it goes into the world whole, where it disperses.
            return new Placement(substance, state, form, 1, umu, 0.0D);
        }
        int units = (int) Math.floor(umu / form.umu() + EPSILON);
        double placed = units * form.umu();
        return new Placement(substance, state, form, units, placed, Math.max(0.0D, umu - placed));
    }

    /** What all of a plan leaves over. */
    public static double leftover(List<Placement> placements) {
        return placements.stream().mapToDouble(Placement::leftover).sum();
    }
}
