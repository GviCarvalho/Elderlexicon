package com.elderlexicon.mod.spell.function;

import net.minecraft.world.entity.LivingEntity;
import com.elderlexicon.mod.spell.Casters;

import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.MatterLaws;
import com.elderlexicon.mod.magic.matter.State;
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
 * A state the table gives the substance no look in is still that substance in that state: molten earth, molten iron,
 * solid fire are formless matter (stage 5), which can be poured, mixed and changed back. A gas or a plasma with no look
 * of its own disperses.
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
        LivingEntity player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return 0.0D;
        }
        ServerLevel level = Casters.level(player);
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
        if (from == VitaElement.IGNI || from == VitaElement.AURA) {
            // Fire and air are mostly not blocks: the heat in the air (a flame, a hot field) is fire let loose, and air
            // is the air of every empty block. Their particles are what is changed (docs/particulas-design.md).
            changed = inTheAir(context, player, level, BlockPos.containing(around), from, to, amount);
        }
        for (BlockPos pos : Transfer.nearest(level, player, BlockPos.containing(around), source.get())) {
            if (changed >= amount - EPSILON) {
                break;
            }
            if (level.getBlockState(pos).isAir()) {
                continue; // air changed is the air around: nothing to see
            }
            changed += change(context, player, level, pos, target);
        }
        if (changed <= EPSILON) {
            MarkSpells.tell(player, "Nao ha " + from.runeId() + " ao alcance para converter.");
        }
        return changed;
    }

    /** How far around the mage, or the origin written, fire and air are changed where they are. */
    private static final int AIR_REACH = 8;
    private static final int AIR_HEIGHT = 4;

    /**
     * {@code igni tenet vertere aura}: the heat let loose in the blocks around (a flame, a burning field) taken out of them
     * and made the target where it was, nearest first, up to {@code amount} UMU; {@code aura tenet vertere igni}: the
     * air of the blocks around made the target where it was. Made air or water goes into the air of the block (air
     * pressing out as wind, vapour), earth as dust, fire as heat; made vis goes into the mage. The conversion's work
     * (5% a rung, the whole ladder back into vis) is lost from what is made. Returns the UMU changed.
     */
    private static double inTheAir(SpellContext context, LivingEntity player, ServerLevel level, BlockPos center,
                                   VitaElement from, VitaElement to, double amount) {
        long wanted = com.elderlexicon.mod.magic.matter.Particles.ofUmu(amount);
        if (wanted <= 0L || from == to) {
            return 0.0D;
        }
        double work = MatterLaws.workShare(MatterLaws.steps(State.of(from).orElse(null), State.of(to).orElse(null)));
        net.minecraft.world.phys.AABB body = player.getBoundingBox();
        java.util.List<BlockPos> around = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-AIR_REACH, -AIR_HEIGHT, -AIR_REACH),
                center.offset(AIR_REACH, AIR_HEIGHT, AIR_REACH))) {
            if (level.isLoaded(pos) && !body.intersects(new net.minecraft.world.phys.AABB(pos))) {
                around.add(pos.immutable());
            }
        }
        around.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(center)));
        long done = 0L;
        double vis = 0.0D;
        for (BlockPos pos : around) {
            if (done >= wanted) {
                break;
            }
            long got;
            if (from == VitaElement.IGNI) {
                long heat = com.elderlexicon.mod.spell.nature.NatureWorld.freeHeat(level, pos);
                got = Math.min(heat, wanted - done);
                if (got <= 0L) {
                    continue;
                }
                com.elderlexicon.mod.spell.nature.NatureWorld.heat(level, pos, -got);
            } else {
                if (!level.getBlockState(pos).isAir()
                        && !com.elderlexicon.mod.spell.nature.NatureWorld.flameAt(level, pos)) {
                    continue;
                }
                got = com.elderlexicon.mod.spell.nature.NatureWorld.draw(level, pos, VitaElement.AURA, wanted - done);
                if (got <= 0L) {
                    continue;
                }
            }
            done += got;
            long made = Math.round(got * (1.0D - work));
            if (made <= 0L) {
                continue;
            }
            switch (to) {
                case IGNI -> com.elderlexicon.mod.spell.nature.NatureWorld.heat(level, pos, made);
                case BALANCED -> vis += (double) made / com.elderlexicon.mod.magic.matter.Particles.PER_UMU;
                default -> com.elderlexicon.mod.spell.nature.NatureWorld.blow(level, pos,
                        com.elderlexicon.mod.magic.matter.Particles.of(to, made));
            }
        }
        if (vis > EPSILON) {
            Casters.giveVis(player, vis);
        }
        return (double) done / com.elderlexicon.mod.magic.matter.Particles.PER_UMU;
    }

    /**
     * {@code m1 vertere aqua} on a marked block: the block goes to the target's state where it is. False when the block
     * is no matter the table knows (a chest), for the caller to treat it as it does other things.
     */
    static boolean block(SpellContext context, ServerLevel level, BlockPos pos, VitaElement to) {
        if (WorldMatter.read(level, pos).isEmpty()) {
            return false;
        }
        change(context, context.player(), level, pos, State.of(to));
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
    private static double change(SpellContext context, LivingEntity player, ServerLevel level, BlockPos pos,
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

    /** What finds no room, or is unmade, goes into the mage as energy (L3); with a focus the body is left alone. */
    private static void keep(SpellContext context, LivingEntity player, VitaElement aspect, double umu) {
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
