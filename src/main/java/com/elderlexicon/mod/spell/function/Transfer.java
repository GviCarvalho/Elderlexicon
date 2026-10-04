package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Qualities;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
 * <p>
 * Natural things lying loose are taken as well as blocks, whole items at a time (docs/particulas-design.md): the flesh
 * on the ground is flesh. What is brought is weighed in the instant with whatever else is released there, so the
 * ingredients of a body brought beside an anchor of Vis bind into a being instead of being put down.
 */
final class Transfer {

    /** How far around the mage, or the origin written, matter is looked for. */
    private static final int REACH = 8;
    private static final int VERTICAL_REACH = 4;
    /** What is moved when no quantity says otherwise, in UMU: one block, as any vocant. */
    private static final double DEFAULT_UMU = VocantFunctionHandler.DEFAULT_UMU;
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
        // Matter taken into a marked wand goes into its gem (docs/varinhas-design.md, 3.1); what the gem does not take
        // is put down where the wand is.
        Optional<SettingCharge.Bearer> gem = SettingCharge.find(level.getServer(), place);
        Optional<MarkSpells.Destination> written = gem.isPresent()
                ? gem.map(SettingCharge.Bearer::destination)
                : place.isPresent()
                ? MarkSpells.destination(context, place, MarkSpells.SUMMON_RANGE)
                : Optional.empty();
        if (place.isPresent() && written.isEmpty()) {
            return;
        }
        if (gem.isPresent() && gem.get().holder().level() != level) {
            MarkSpells.tell(player, "Esse lugar esta em outra dimensao.");
            return;
        }
        List<Matter> all = take(level, player, BlockPos.containing(around), state.get(),
                action.quantity().orElse(DEFAULT_UMU));
        double moved = all.stream().mapToDouble(Matter::umu).sum();
        if (moved <= 0.0D) {
            MarkSpells.tell(player, "Nao ha " + element.runeId() + " ao alcance para trazer.");
            return;
        }
        // The matter is moved, not made: the world gives it, and the mage pays only the spirit's work of moving it.
        context.addAmbientEnergy(element, Math.min(moved, context.payableCost()));
        List<Matter> taken = gem.isPresent() && !action.handsOn()
                ? leftOver(all, moved, SettingCharge.charge(player, gem.get(), element, moved))
                : all;
        if (taken.isEmpty()) {
            return;
        }
        String rune = context.elementRuneId();
        context.handOn(null); // what an earlier verb left is not handed on past this one
        if (action.handsOn()) {
            // The verb after acts on it (firmo tenet vocant iactare): it is brought to the ubis place or the hand, for
            // that verb to carry, and put down where it lands.
            Product product = new Product(element, moved, qualitiesOf(taken));
            context.handOn(product);
            SpellEffects.schedule(level, 1, () -> {
                if (!SpellEffects.isPlayerValid(player)) {
                    return;
                }
                if (product.taken()) {
                    Vec3 from = written.map(MarkSpells.Destination::point)
                            .orElseGet(() -> WorldSources.handOf(player));
                    product.ready(level, from, landed -> put(context, player, element, rune, landed, taken));
                } else {
                    put(context, player, element, rune, destination(player, written), taken);
                }
            });
            return;
        }
        // Brought in this instant: with an anchor released beside it, it is the body of a being (docs/vita-design.md).
        SpellEffects.SpellImpact landing = destination(player, written);
        Quickening.offer(level, player, landing.location(), taken,
                () -> put(context, player, element, rune, landing, taken));
    }

    /** What is left of the matter taken once a gem took its share: every portion, in the same proportion. */
    private static List<Matter> leftOver(List<Matter> taken, double moved, double left) {
        if (left <= 1.0E-4D || moved <= 0.0D) {
            return List.of();
        }
        double share = Math.min(1.0D, left / moved);
        return taken.stream().map(matter -> matter.withUmu(matter.umu() * share)).toList();
    }

    /** What all the matter taken is like together, each portion counting by its UMU. */
    private static Qualities qualitiesOf(List<Matter> taken) {
        Map<VitaElement, Double> amounts = new java.util.EnumMap<>(VitaElement.class);
        taken.forEach(matter -> matter.composition().split(matter.umu())
                .forEach((aspect, umu) -> amounts.merge(aspect, umu, Double::sum)));
        return Qualities.of(amounts);
    }

    /** Where the vocant puts things: the place written, or where the mage aims. */
    private static SpellEffects.SpellImpact destination(ServerPlayer player, Optional<MarkSpells.Destination> written) {
        return written.map(MarkSpells.Destination::impact)
                .orElseGet(() -> SpellEffects.findImpact(player, MarkSpells.SUMMON_RANGE));
    }

    /** A place matter can be taken from: a block, or natural items lying loose. */
    private record Source(BlockPos block, ItemEntity items, double distance) {
    }

    /**
     * Takes up to {@code wanted} UMU of matter in {@code state} from around {@code center}, nearest first: whole blocks
     * and whole items at a time, never what the mage stands in or on.
     */
    private static List<Matter> take(ServerLevel level, ServerPlayer player, BlockPos center, State state,
                                     double wanted) {
        Vec3 middle = Vec3.atCenterOf(center);
        List<Source> sources = new ArrayList<>();
        for (BlockPos pos : nearest(level, player, center, state)) {
            sources.add(new Source(pos, null, Vec3.atCenterOf(pos).distanceToSqr(middle)));
        }
        for (ItemEntity items : loose(level, center, state)) {
            sources.add(new Source(null, items, items.position().distanceToSqr(middle)));
        }
        sources.sort(Comparator.comparingDouble(Source::distance));
        List<Matter> taken = new ArrayList<>();
        double total = 0.0D;
        for (Source source : sources) {
            if (total >= wanted) {
                break;
            }
            Optional<Matter> matter = source.block() != null ? takeBlock(level, source.block())
                    : takeItems(source.items(), wanted - total);
            if (matter.isPresent()) {
                taken.add(matter.get());
                total += matter.get().umu();
            }
        }
        return merged(taken);
    }

    private static Optional<Matter> takeBlock(ServerLevel level, BlockPos pos) {
        BlockState was = level.getBlockState(pos);
        Optional<Matter> matter = WorldMatter.take(level, pos);
        if (matter.isPresent() && !was.isAir()) {
            level.levelEvent(BREAK_EVENT, pos, Block.getId(was));
        }
        return matter;
    }

    /** As many items of a loose stack as it takes to make {@code wanted} UMU, whole ones: the rest stays where it lies. */
    private static Optional<Matter> takeItems(ItemEntity items, double wanted) {
        ItemStack stack = items.getItem();
        ItemStack one = stack.copy();
        one.setCount(1);
        Optional<Matter> each = WorldMatter.read(one);
        if (each.isEmpty() || each.get().umu() <= 0.0D) {
            return Optional.empty();
        }
        int count = (int) Math.max(1L, Math.min(stack.getCount(), (long) Math.ceil(wanted / each.get().umu() - 1.0E-9D)));
        stack.shrink(count);
        if (stack.isEmpty()) {
            items.discard();
        } else {
            items.setItem(stack);
        }
        return Optional.of(each.get().withUmu(each.get().umu() * count));
    }

    /** The natural things lying loose around {@code center} whose matter is in {@code state}. */
    private static List<ItemEntity> loose(ServerLevel level, BlockPos center, State state) {
        AABB around = new AABB(center).inflate(REACH, VERTICAL_REACH, REACH);
        return level.getEntitiesOfClass(ItemEntity.class, around, items -> items.isAlive()
                && WorldMatter.read(items).map(matter -> matter.state() == state).orElse(false));
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
        List<Matter> liquids = new ArrayList<>();
        for (Matter matter : taken) {
            if (matter.state() == State.LIQUID) {
                liquids.add(matter);
                continue;
            }
            // Solids do not mix (a bond joins them); what floats goes into the air there, where the drives take it.
            leftover += WorldMatter.place(level, spot, matter).leftover();
            if (matter.state() == State.GAS) {
                // Air let out there joins the air around it: it is felt as a gust.
                Invocation.invoke(player, element, rune, Invocation.Where.fixed(impact), matter.umu(), 0);
            }
        }
        // Brought to one place, the liquids mix (L4), with the agitation each brought; landing in fluid matter, they
        // are poured into it.
        Optional<WorldMatter.Mixed> together = WorldMatter.mix(liquids);
        if (together.isPresent()) {
            Matter mixture = together.get().matter();
            if (liquids.size() > 1) {
                Pouring.tell(player, mixture);
            }
            // A solid that came of it is laid beside, as any solid; a liquid or what floats goes into what it lands in.
            Optional<BlockPos> into = Pouring.into(level, impact).filter(pos -> mixture.state() != State.SOLID);
            WorldMatter.Placed placed = WorldMatter.place(level, into.orElse(spot), mixture,
                    together.get().temperature());
            leftover += placed.leftover();
            Pouring.tell(player, mixture, placed);
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
