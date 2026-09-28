package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.MatterLaws;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spell.matter.WorldMatter;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Optional;

/**
 * Vertere on matter in the world (docs/plano-materia-e-forca.md, L2): it climbs or descends the ladder of states and
 * stays what it is. Stone melted is lava, lava cooled is stone, ice melted is water, water boiled is vapour. The source
 * names the state the matter is in, the target the state it goes to; the work is 5% of it for every rung, paid by the
 * mage. Going back into vis unmakes it: what is left of it goes into the mage as vis.
 * <p>
 * A state the substance is not known in, that would lay a block or an item, is refused: the spirit does not know
 * molten iron until the table says what it looks like. A gas or a plasma needs no look of its own: it disperses.
 */
public final class StateChange {

    private static final double EPSILON = 1.0E-6D;

    private StateChange() {
    }

    /**
     * {@code firmo tenet vertere aqua}: up to {@code amount} UMU of matter in the source's state, nearest the mage or
     * the origin written, goes to the target's state where it is. Returns the UMU changed.
     */
    public static double inPlace(SpellContext context, VitaElement from, VitaElement to, double amount) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return 0.0D;
        }
        ServerLevel level = player.serverLevel();
        Optional<State> source = State.of(from);
        if (source.isEmpty()) {
            MarkSpells.tell(player, "Vis nao tem estado: nao ha materia dela no mundo para converter.");
            return 0.0D;
        }
        Vec3 around = player.position();
        Optional<SpellPlace> origin = context.currentAction().flatMap(SpellAction::place);
        if (origin.isPresent()) {
            Optional<MarkSpells.Destination> found = MarkSpells.destination(context, origin, MarkSpells.SUMMON_RANGE);
            if (found.isEmpty()) {
                return 0.0D;
            }
            around = found.get().point();
        }
        Optional<State> target = State.of(to);
        double changed = 0.0D;
        String refused = null;
        for (BlockPos pos : Transfer.nearest(level, player, BlockPos.containing(around), source.get())) {
            if (changed >= amount - EPSILON) {
                break;
            }
            if (level.getBlockState(pos).isAir()) {
                continue; // air changed is the air around: nothing to see
            }
            Optional<String> unknown = target.isPresent() ? unknownIn(level, pos, target.get()) : Optional.empty();
            if (unknown.isPresent()) {
                refused = unknown.get();
                continue;
            }
            changed += change(context, player, level, pos, target);
        }
        if (changed <= EPSILON) {
            MarkSpells.tell(player, refused != null ? refused
                    : "Nao ha " + from.runeId() + " ao alcance para converter.");
        }
        return changed;
    }

    /**
     * {@code m1 vertere aqua} on a marked block: the block goes to the target's state where it is. False when the block
     * is no matter the table knows (a chest), for the caller to treat it as it does other things.
     */
    static boolean block(SpellContext context, ServerLevel level, BlockPos pos, VitaElement to) {
        if (WorldMatter.read(level, pos).isEmpty()) {
            return false;
        }
        Optional<State> target = State.of(to);
        Optional<String> unknown = target.isPresent() ? unknownIn(level, pos, target.get()) : Optional.empty();
        if (unknown.isPresent()) {
            MarkSpells.tell(context.player(), unknown.get());
            return true;
        }
        change(context, context.player(), level, pos, target);
        return true;
    }

    /** Whether the block at {@code pos} is matter the table knows, to change state rather than be unmade. */
    static boolean knows(ServerLevel level, BlockPos pos) {
        return WorldMatter.read(level, pos).isPresent();
    }

    /** The work of changing the block at {@code pos} to {@code to}'s state, 0 when it is no matter the table knows. */
    static double workOf(ServerLevel level, BlockPos pos, VitaElement to) {
        return WorldMatter.read(level, pos)
                .map(matter -> matter.umu() * MatterLaws.workShare(MatterLaws.steps(matter.state(),
                        State.of(to).orElse(null))))
                .orElse(0.0D);
    }

    /** Changes the block at {@code pos}; an empty target is vis, which unmakes it into the mage. Returns its UMU. */
    private static double change(SpellContext context, ServerPlayer player, ServerLevel level, BlockPos pos,
                                 Optional<State> target) {
        Optional<Matter> taken = WorldMatter.take(level, pos);
        if (taken.isEmpty()) {
            return 0.0D;
        }
        Matter matter = taken.get();
        if (target.isEmpty()) {
            // Back into vis: it is unmade, and what the work leaves of it goes into the mage.
            double kept = matter.umu() * (1.0D - MatterLaws.workShare(MatterLaws.STEPS_INTO_VIS));
            keep(context, player, VitaElement.BALANCED, kept);
            show(level, pos, VitaElement.BALANCED);
            return matter.umu();
        }
        MatterLaws.Change change = MatterLaws.changeState(matter, target.get());
        context.addTotalCost(change.work());
        WorldMatter.Placed placed = WorldMatter.place(level, pos, change.matter());
        if (placed.leftover() > EPSILON) {
            keep(context, player, target.get().element(), placed.leftover());
        }
        show(level, pos, target.get().element());
        return matter.umu();
    }

    /**
     * Why the block at {@code pos} cannot go to {@code state}: the table gives its substance no look there, and it would
     * have to lay a block or an item. Empty when it can.
     */
    private static Optional<String> unknownIn(ServerLevel level, BlockPos pos, State state) {
        Optional<Substance> substance = WorldMatter.read(level, pos).flatMap(matter -> matter.substance(Materials.get()));
        if (substance.isEmpty() || !substance.get().declared(state).isEmpty() || state == State.GAS
                || state == State.PLASMA) {
            return Optional.empty();
        }
        return Optional.of("O espirito nao conhece " + substance.get().name() + " " + stateName(state)
                + ": nada mudou.");
    }

    private static String stateName(State state) {
        return switch (state) {
            case SOLID -> "solido";
            case LIQUID -> "liquido";
            case GAS -> "gasoso";
            case PLASMA -> "em plasma";
        };
    }

    /** What finds no room, or is unmade, goes into the mage as energy (L3); with a focus the body is left alone. */
    private static void keep(SpellContext context, ServerPlayer player, VitaElement aspect, double umu) {
        if (umu > EPSILON && !context.focusActive()) {
            VitaSystem.restoreElementEnergy(player, aspect, umu);
        }
    }

    private static void show(ServerLevel level, BlockPos pos, VitaElement element) {
        SpellEffects.resolveParticle(element, element.runeId().toLowerCase(Locale.ROOT)).ifPresent(particle -> {
            Vec3 at = Vec3.atCenterOf(pos);
            level.sendParticles(particle, at.x, at.y, at.z, 8, 0.4D, 0.4D, 0.4D, 0.02D);
        });
    }
}
