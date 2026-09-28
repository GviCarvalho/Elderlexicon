package com.elderlexicon.mod.spell.function;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Matter moved from where it is to where a verb puts it (docs/plano-materia-e-forca.md, stage 4): {@code firmo tenet
 * vocant} takes the solid matter nearest the mage, of whatever substance, and puts it where the mage aims. Nothing is
 * made or unmade (L1): the stone taken is the stone put down, and what finds no room goes back to the mage as energy of
 * its state (L3). The source names the state taken (firmo solid, aqua liquid, aura gas, igni plasma); the origin, where
 * it is taken from; the quantity, how much.
 */
final class Transfer {

    /** How far around the mage, or the origin written, matter is looked for. */
    private static final int REACH = 8;
    private static final int VERTICAL_REACH = 4;
    /** What is moved when no quantity says otherwise, in UMU. */
    private static final double DEFAULT_UMU = 10.0D;
    /** The break effect of a block (its particles and sound). */
    private static final int BREAK_EVENT = 2001;

    private Transfer() {
    }

    /** Moves matter of the source's state from the world to where the verb puts it. */
    static void fromWorld(SpellContext context, VitaElement element, SpellAction action) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        Optional<State> state = State.of(element);
        if (state.isEmpty()) {
            MarkSpells.tell(player, "Vis nao tem estado: nao ha materia dela no mundo para mover.");
            return;
        }
        Vec3 around = player.position();
        if (action.originPlace().isPresent()) {
            Optional<MarkSpells.Destination> origin = MarkSpells.destination(context, action.originPlace(),
                    MarkSpells.SUMMON_RANGE);
            if (origin.isEmpty()) {
                return;
            }
            around = origin.get().point();
        }
        Optional<SpellPlace> place = action.place();
        Optional<MarkSpells.Destination> written = place.isPresent()
                ? MarkSpells.destination(context, place, MarkSpells.SUMMON_RANGE)
                : Optional.empty();
        if (place.isPresent() && written.isEmpty()) {
            return;
        }
        List<Matter> taken = take(level, player, BlockPos.containing(around), state.get(),
                action.quantity().orElse(DEFAULT_UMU));
        double moved = taken.stream().mapToDouble(Matter::umu).sum();
        if (moved <= 0.0D) {
            MarkSpells.tell(player, "Nao ha " + element.runeId() + " ao alcance para trazer.");
            return;
        }
        // The matter is moved, not made: the world gives it, and the mage pays only the spirit's work of moving it.
        context.addAmbientEnergy(element, Math.min(moved, context.payableCost()));
        String rune = context.elementRuneId();
        context.handOn(null); // what an earlier verb left is not handed on past this one
        if (action.handsOn()) {
            // The verb after acts on it (firmo tenet vocant iactare): it is brought to the ubis place or the hand, for
            // that verb to carry, and put down where it lands.
            Product product = new Product(element, moved);
            context.handOn(product);
            SpellEffects.schedule(level, 1, () -> {
                if (!SpellEffects.isPlayerValid(player)) {
                    return;
                }
                if (product.taken()) {
                    Vec3 from = written.map(MarkSpells.Destination::point)
                            .orElseGet(() -> ExsugatFunctionHandler.handOf(player));
                    product.ready(level, from, landed -> put(context, player, element, rune, landed, taken));
                } else {
                    put(context, player, element, rune, destination(player, written), taken);
                }
            });
            return;
        }
        put(context, player, element, rune, destination(player, written), taken);
    }

    /** Where the vocant puts things: the place written, or where the mage aims. */
    private static SpellEffects.SpellImpact destination(ServerPlayer player, Optional<MarkSpells.Destination> written) {
        return written.map(MarkSpells.Destination::impact)
                .orElseGet(() -> SpellEffects.findImpact(player, MarkSpells.SUMMON_RANGE));
    }

    /**
     * Takes up to {@code wanted} UMU of matter in {@code state} from around {@code center}, nearest first, whole blocks
     * at a time: never what the mage stands in or on.
     */
    private static List<Matter> take(ServerLevel level, ServerPlayer player, BlockPos center, State state,
                                     double wanted) {
        List<BlockPos> candidates = nearest(level, player, center, state);
        List<Matter> taken = new ArrayList<>();
        double total = 0.0D;
        for (BlockPos pos : candidates) {
            if (total >= wanted) {
                break;
            }
            BlockState was = level.getBlockState(pos);
            Optional<Matter> matter = WorldMatter.take(level, pos);
            if (matter.isPresent()) {
                if (!was.isAir()) {
                    level.levelEvent(BREAK_EVENT, pos, Block.getId(was));
                }
                taken.add(matter.get());
                total += matter.get().umu();
            }
        }
        return merged(taken);
    }

    /**
     * The blocks of matter in {@code state} around {@code center}, nearest first: never what the mage stands in or on.
     * Air is found too, though the air around fills its place at once.
     */
    static List<BlockPos> nearest(ServerLevel level, ServerPlayer player, BlockPos center, State state) {
        AABB body = player.getBoundingBox().inflate(0.0D, 1.0D, 0.0D).move(0.0D, -0.5D, 0.0D);
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-REACH, -VERTICAL_REACH, -REACH),
                center.offset(REACH, VERTICAL_REACH, REACH))) {
            if (!level.isLoaded(pos) || body.intersects(new AABB(pos))) {
                continue;
            }
            Optional<Matter> matter = WorldMatter.read(level, pos);
            if (matter.isPresent() && matter.get().state() == state) {
                candidates.add(pos.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return candidates;
    }

    /** Portions of the same matter in the same state, as one. */
    private static List<Matter> merged(List<Matter> portions) {
        Map<String, Matter> byKind = new LinkedHashMap<>();
        for (Matter portion : portions) {
            byKind.merge(portion.composition() + "|" + portion.state(), portion,
                    (left, right) -> left.withUmu(left.umu() + right.umu()));
        }
        return List.copyOf(byKind.values());
    }

    /** Puts what was taken down at {@code impact}; what finds no room goes back to the mage (L1, L3). */
    private static void put(SpellContext context, ServerPlayer player, VitaElement element, String rune,
                            SpellEffects.SpellImpact impact, List<Matter> taken) {
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos spot = spotOf(impact);
        SpellEffects.spawnSummonEffect(player, element, rune, impact);
        double leftover = 0.0D;
        List<Matter> fluids = new ArrayList<>();
        for (Matter matter : taken) {
            if (matter.state().fluid()) {
                fluids.add(matter);
            } else {
                leftover += WorldMatter.place(level, spot, matter).leftover(); // solids do not mix: a bond joins them
            }
        }
        // Brought to one place, the fluids mix (L4); landing in fluid matter, they are poured into it.
        Optional<Matter> together = MatterLaws.mix(fluids);
        if (together.isPresent()) {
            if (fluids.size() > 1) {
                Pouring.tell(player, together.get());
            }
            Optional<BlockPos> into = Pouring.into(level, impact);
            WorldMatter.Placed placed = into.isPresent() ? WorldMatter.pour(level, into.get(), together.get())
                    : WorldMatter.place(level, spot, together.get());
            leftover += placed.leftover();
            Pouring.tell(player, together.get(), placed);
            if (into.isEmpty() && together.get().state() == State.GAS) {
                // Air let out there joins the air around it: it is felt as a gust.
                Invocation.invoke(player, element, rune, Invocation.Where.fixed(impact),
                        together.get().umu() / EmissionRecorder.DEFAULT_QUANTITY_UMU, 0);
            }
        }
        if (leftover > 0.0D && !context.focusActive()) {
            VitaSystem.restoreElementEnergy(player, element, leftover);
        }
    }

    /** The block where what lands at {@code impact} is put: before the face it struck, or where it stopped. */
    static BlockPos spotOf(SpellEffects.SpellImpact impact) {
        if (impact.entity() != null) {
            return impact.entity().blockPosition();
        }
        BlockPos before = SpellEffects.firePlacementPos(impact);
        return before != null ? before : BlockPos.containing(impact.location());
    }
}
