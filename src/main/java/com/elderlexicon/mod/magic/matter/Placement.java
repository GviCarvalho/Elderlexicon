package com.elderlexicon.mod.magic.matter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a portion of matter goes into the world, worked out before the world is touched (docs/plano-materia-e-forca.md,
 * stage 3, and docs/plano-materia-emergente.md). First its opposites react (L5): what separates out goes as a gas of its
 * own. Then what it is decides how it shows:
 * <ul>
 *   <li>near the code of a natural thing that has a look in its state: whole blocks or items of it, or a show of
 *       particles or a creature for what floats; only whole blocks and items are placed, and what does not make one is
 *       left over, never lost (L1);</li>
 *   <li>anything else is formless matter: blocks that hold it exactly as it is, for a solid or a liquid (molten earth,
 *       a mixture with no name), or a show of its own colour for what floats. Nothing of it is left over.</li>
 * </ul>
 *
 * @param matter    what is placed
 * @param substance the natural thing it is; null for a mixture with no name
 * @param form      how it shows there; null when it is formless
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

    /** Whether it floats off into the world (a gas, a plasma) rather than taking room in it. */
    public boolean floats() {
        return MaterialTable.floats(matter.state());
    }

    /** How {@code matter} goes into the world: what its opposites let out, then what stays. */
    public static List<Placement> plan(MaterialTable table, Matter matter) {
        List<Placement> placements = new ArrayList<>();
        if (matter.umu() <= EPSILON) {
            return placements;
        }
        MatterLaws.Reaction reaction = MatterLaws.react(matter);
        reaction.released().ifPresent(gas -> placements.add(placementOf(table, gas)));
        reaction.remains().ifPresent(rest -> placements.add(placementOf(table, rest)));
        return placements;
    }

    private static Placement placementOf(MaterialTable table, Matter matter) {
        Optional<Substance> substance = matter.substance(table);
        Optional<Form> form = substance.flatMap(found -> table.form(found, matter.state()));
        if (form.isPresent()) {
            return of(matter, substance.get(), form.get());
        }
        return formless(matter, substance.orElse(null));
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
     * Formless matter: for a solid or a liquid, as many blocks as it fills (at least one), each block 16 UMU as any block
     * is, and each holding its share exactly; what floats goes into the world whole, a show of its own colour.
     */
    private static Placement formless(Matter matter, Substance substance) {
        if (MaterialTable.floats(matter.state())) {
            return new Placement(matter, substance, null, 1, matter.umu(), 0.0D, true);
        }
        int units = (int) Math.max(1L, Math.round(matter.umu() / Particles.BLOCK_UMU));
        return new Placement(matter, substance, null, units, matter.umu(), 0.0D, true);
    }

    /** What all of a plan leaves over. */
    public static double leftover(List<Placement> placements) {
        return placements.stream().mapToDouble(Placement::leftover).sum();
    }
}
