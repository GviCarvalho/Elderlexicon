package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.VerbSpec;
import com.elderlexicon.mod.spell.AirPressure;
import com.elderlexicon.mod.spell.Capture;
import com.elderlexicon.mod.spell.Charge;
import com.elderlexicon.mod.spell.Conversion;
import com.elderlexicon.mod.spell.Density;
import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.Pressure;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spell.sight.Revelation;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.command.SpellCostCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Exsugat, absorbing (book 8.2): it pulls a source from outside the body, "o fogo de uma fogueira próxima, a água de
 * um lago próximo". Written before other functions it captures what they spend, so the mage's own Vita is spared
 * ({@code igni exsugat iactare}); written last, what it pulls goes into the body ({@code igni exsugat}, when cold).
 * The sources are taken whole, the nearest to where the mage aims first (or to the mage, aiming at nothing).
 */
public final class ExsugatFunctionHandler implements SpellFunctionHandler {

    /** How far from the aim, or the mage, a source is pulled from. */
    private static final int RANGE = 5;
    /** How far the spirit reaches out, at most, to find an amount it was asked for. */
    private static final int SEARCH_REACH = 16;
    /** How far up and down it looks: sources are pulled from around the mage, not from deep under it. */
    private static final int VERTICAL_REACH = 8;
    /** Without a quantum, a bare exsugat pulls what a function spends by default (book 4.3.2: ten UMU). */
    public static final double DEFAULT_ABSORBED_UMU = EmissionRecorder.DEFAULT_QUANTITY_UMU;
    private static final double EPSILON = 1.0E-4D;
    private static Field furnaceData;

    @Override
    public void execute(SpellContext context, VitaElement element) {
        Optional<SpellAction> action = context.currentAction();
        if (action.isPresent() && action.get().image() && SpellEffects.isPlayerValid(context.player())) {
            ImageSpells.absorb(context, element, action.get());
            return;
        }
        if (action.flatMap(SpellAction::subjectMark).isPresent() && SpellEffects.isPlayerValid(context.player())) {
            // m1 exsugat: the marked thing is pulled toward the mage, like a magnet.
            MarkSpells.push(context, action.get().subjectMark().get(), action.get().place(), MarkSpells.Push.TOWARD_CASTER,
                    action.get().quantity().orElse(MarkCost.DEFAULT_THROW_ENERGY), Chronos.window(action.get()));
            return;
        }
        // Nothing after it to feed: what is pulled goes into the body.
        absorb(context, element, element, action.map(SpellAction::quantity).orElse(OptionalDouble.empty())
                .orElse(DEFAULT_ABSORBED_UMU));
    }

    /**
     * Pulls {@code amount} of {@code element} into the mage's body, as {@code becomes} (converted on the way when a
     * vertere came after: {@code igni exsugat vertere aqua}, with nothing to spend it on, quenches thirst).
     */
    public static void absorb(SpellContext context, VitaElement element, VitaElement becomes, double amount) {
        if (!SpellEffects.isPlayerValid(context.player())) {
            return;
        }
        double pulled = pull(context, element, amount, bodyOf(context.player()));
        if (pulled > EPSILON) {
            context.absorbIntoBody(becomes, pulled);
            context.player().displayClientMessage(Component.literal("Absorveste "
                    + SpellCostCalculator.formatCost(pulled) + " UMU de " + becomes.runeId() + "."), true);
        } else {
            nothingFound(context, element);
        }
    }

    /**
     * Pays the spell with what is pulled from outside: the functions after the exsugat spend the captured source instead
     * of the mage's Vita. What the last source brings beyond the cost goes into the body, as {@code becomes}.
     */
    public static double capture(SpellContext context, VitaElement element, VitaElement becomes, SpellAction spender,
                                 double workShare) {
        if (!SpellEffects.isPlayerValid(context.player())) {
            return 0.0D;
        }
        double needed = context.payableCost();
        if (needed <= EPSILON) {
            return 0.0D;
        }
        // Converting it on the way takes its share of the energy in hand: a little more is pulled to cover it.
        double pulled = pull(context, element, needed / (1.0D - workShare), gatheringPoint(context, spender));
        if (pulled <= EPSILON) {
            nothingFound(context, element);
            return 0.0D;
        }
        double worked = pulled * (1.0D - workShare);
        context.addAmbientEnergy(becomes, Math.min(worked, needed));
        if (worked > needed + EPSILON) {
            context.absorbIntoBody(becomes, worked - needed);
        }
        return pulled;
    }

    /**
     * Pulls every source of {@code element} in reach at once, for a function written after a bare quantum
     * ({@code igni exsugat quantum iactare}: "o fogo é completamente extraído e lançado de uma só vez", book 4.3.2).
     * Returns what was taken (how much and from how many sources), for the executor to spend and account for.
     */
    public static Pulled captureAll(SpellContext context, List<VitaElement> chain, double limit, SpellAction spender,
                                    SpellAction exsugat) {
        VitaElement element = chain.get(0);
        VitaElement becomes = chain.get(chain.size() - 1);
        if (!SpellEffects.isPlayerValid(context.player())) {
            return new Pulled(0, 0.0D, 0, -1);
        }
        List<Source> taken = take(context, element, limit, exsugat == null ? Optional.empty() : exsugat.place());
        double pulled = taken.stream().mapToDouble(Source::value).sum();
        ServerPlayer player = context.player();
        ElementOrb orb = null;
        if (spender != null && spender.atOnce() && pulled > EPSILON) {
            // Condensing: it is gathered into one point for as long as the charge takes, then released.
            java.util.function.Supplier<Vec3> point = orbPoint(context, spender);
            List<Gatherings.Origin> origins = taken.stream()
                    .map(source -> new Gatherings.Origin(source.pos(), source.state(), source.value())).toList();
            int ticks = Charge.ticks(pulled);
            Gatherings.gather(player.serverLevel(), element, origins, point, ticks);
            // Converted too (firmo exsugat vertere igni quantum chronos 0 iactare): once gathered, the orb is unmade
            // and remade as the new element, which takes its own time.
            orb = ElementOrb.gathering(player.serverLevel(), player, chain, pulled, element == becomes ? coalIn(taken) : 0,
                    point, ticks);
            player.serverLevel().addFreshEntity(orb);
        } else {
            Vec3 toward = gatheringPoint(context, spender);
            for (Source source : taken) {
                flow(player.serverLevel(), element, source, toward);
            }
        }
        if (pulled <= EPSILON) {
            nothingFound(context, element);
        }
        return new Pulled(taken.size(), pulled, coalIn(taken), orb == null ? -1 : orb.getId());
    }

    /** Carbon: what was taken counts as coal only when nearly all of it was coal (ore gives one, a block nine). */
    private static int coalIn(List<Source> taken) {
        int coalSources = 0;
        int coal = 0;
        for (Source source : taken) {
            if (source.state().is(BlockTags.COAL_ORES)) {
                coalSources++;
                coal += 1;
            } else if (source.state().is(net.minecraftforge.common.Tags.Blocks.STORAGE_BLOCKS_COAL)) {
                coalSources++;
                coal += 9;
            }
        }
        return !taken.isEmpty() && coalSources >= 0.9D * taken.size() ? coal : 0;
    }

    /** What a whole capture took: how many sources, the UMU they held, the coal in them (when nearly all coal), and
     * the orb it is being gathered into ({@code -1} when it is not condensed). */
    public record Pulled(int sources, double total, int coal, int orb) {
    }

    private static void nothingFound(SpellContext context, VitaElement element) {
        MarkSpells.tell(context.player(), "Exsugat: nao ha " + element.runeId() + " por perto para puxar; o corpo pagou.");
    }

    /** Takes whole sources of {@code element}, nearest first, until {@code needed} UMU; returns what they held. */
    private static double pull(SpellContext context, VitaElement element, double needed, Vec3 toward) {
        List<Source> taken = take(context, element, needed,
                context.currentAction().flatMap(SpellAction::place));
        for (Source source : taken) {
            flow(context.player().serverLevel(), element, source, toward);
        }
        return taken.stream().mapToDouble(Source::value).sum();
    }

    /**
     * Where what is captured is gathered, so its particles are seen flowing there: where the verb that spends it makes it
     * appear, just before the mage's hand for a verb that throws it (where the shot leaves from), and into the mage
     * otherwise, as the lexicon says of each verb ({@code gathering}).
     */
    static Vec3 gatheringPoint(SpellContext context, SpellAction spender) {
        ServerPlayer player = context.player();
        VerbSpec.Gathering gathering = gatheringOf(spender);
        if (gathering == VerbSpec.Gathering.DESTINATION) {
            Optional<MarkSpells.Destination> place = spender.place().isPresent()
                    ? MarkSpells.destination(context, spender.place(), MarkSpells.SUMMON_RANGE) : Optional.empty();
            return place.map(MarkSpells.Destination::point)
                    .orElseGet(() -> SpellEffects.findImpact(player, MarkSpells.SUMMON_RANGE).location());
        }
        if (gathering == VerbSpec.Gathering.HAND) {
            return handOf(player);
        }
        return bodyOf(player);
    }

    /**
     * Where a condensation is gathered while it charges: before the mage's hand, wherever the hand goes, for a verb that
     * throws it; where it will appear, for a verb that makes it appear; at the hand for anything else.
     */
    public static java.util.function.Supplier<Vec3> orbPoint(SpellContext context, SpellAction spender) {
        ServerPlayer player = context.player();
        VerbSpec.Gathering gathering = gatheringOf(spender);
        if (gathering == VerbSpec.Gathering.HAND) {
            return () -> player.getEyePosition().subtract(0.0D, 0.35D, 0.0D).add(player.getViewVector(1.0F).scale(0.9D));
        }
        if (gathering == VerbSpec.Gathering.DESTINATION) {
            Vec3 fixed = gatheringPoint(context, spender);
            return () -> fixed;
        }
        return () -> handOf(player);
    }

    /** Where the verb that spends a capture gathers it, as the lexicon says; into the body when nothing spends it. */
    private static VerbSpec.Gathering gatheringOf(SpellAction spender) {
        if (spender == null) {
            return VerbSpec.Gathering.BODY;
        }
        return Lexicons.get().verb(spender.runeId()).map(VerbSpec::gathering).orElse(VerbSpec.Gathering.BODY);
    }

    private static Vec3 bodyOf(ServerPlayer player) {
        return player.position().add(0.0D, player.getBbHeight() * 0.6D, 0.0D);
    }

    private static void flow(ServerLevel level, VitaElement element, Source source, Vec3 toward) {
        Gatherings.stream(level, element, new Gatherings.Origin(source.pos(), source.state(), source.value()), toward,
                (int) Math.max(3.0D, Math.min(12.0D, source.value() * 4.0D)));
    }

    /** Drains whole sources of {@code element}, nearest to the aim first, until {@code needed} UMU; returns them. */
    private static List<Source> take(SpellContext context, VitaElement element, double needed,
                                     Optional<SpellPlace> from) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        Optional<Revelation.Kind> kind = kindOf(element);
        if (kind.isEmpty() || needed <= EPSILON) {
            return List.of();
        }
        // The mage's hand pulls the source in (or, with an ubis before the exsugat, it is pulled from around a mark
        // or a place). Asked for an amount, the spirit reaches out as far as it must to find it, up to a limit; asked
        // for everything, it takes what is in the usual reach.
        Vec3 center = handOf(player);
        if (from.isPresent()) {
            Optional<MarkSpells.Destination> there = MarkSpells.destination(context, from, MarkSpells.SUMMON_RANGE);
            if (there.isEmpty()) {
                return List.of();
            }
            center = there.get().point();
        }
        double range = needed >= Double.MAX_VALUE / 2.0D ? RANGE : SEARCH_REACH;
        // Standing in an impediunt of the element, what surrounds the mage is its ring: the reach takes it all in.
        Optional<ImpediuntZones.Edge> zone = from.isPresent() ? Optional.empty()
                : ImpediuntZones.zoneAt(level, player.position(), element);
        if (zone.isPresent()) {
            center = zone.get().center();
            range = Math.max(range, zone.get().radius() + 2.0D);
        }
        // The ground the mage stands on is not pulled out from under the feet.
        AABB feet = kind.get() != Revelation.Kind.FIRMO ? null
                : player.getBoundingBox().expandTowards(0.0D, -0.6D, 0.0D);
        // Pulling air, the mage keeps the breath in its own lungs: the space its body fills is not emptied.
        if (kind.get() == Revelation.Kind.AURA) {
            feet = player.getBoundingBox();
        }

        List<Source> sources = new ArrayList<>();
        BlockPos origin = BlockPos.containing(center);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double rangeSq = range * range + 1.0D;
        int reach = (int) Math.ceil(range);
        int height = Math.min(reach, VERTICAL_REACH);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -height; dy <= height; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    double distanceSq = center.distanceToSqr(Vec3.atCenterOf(cursor));
                    if (distanceSq > rangeSq || !level.hasChunkAt(cursor)) {
                        continue;
                    }
                    if (feet != null && feet.intersects(new AABB(cursor))) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    double value = valueOf(kind.get(), level, cursor, state);
                    if (value > EPSILON) {
                        sources.add(new Source(cursor.immutable(), distanceSq, value, state));
                    }
                }
            }
        }
        sources.sort(Comparator.comparingDouble(Source::distanceSq));
        Capture.Taken taken = Capture.take(sources.stream().map(Source::value).toList(), needed);
        List<Source> drained = List.copyOf(sources.subList(0, taken.sources()));
        for (Source source : drained) {
            drain(kind.get(), level, source.pos(), source.state());
        }
        if (kind.get() == Revelation.Kind.AURA && !drained.isEmpty()) {
            // Where the air was pulled from, a vacuum holds for a moment before the air rushes back.
            java.util.Set<BlockPos> emptied = new java.util.HashSet<>();
            drained.forEach(source -> emptied.add(source.pos()));
            AirSpots.vacuum(level, emptied, AirPressure.VACUUM_TICKS);
        }
        return drained;
    }

    /**
     * The captured source turned into another where it is ({@code firmo exsugat vertere igni}, with nothing after to
     * spend it): the earth nearby becomes fire, the fire water, taking whole sources nearest the aim until
     * {@code amount} UMU of the source have been converted.
     */
    public static double convertInPlace(SpellContext context, VitaElement element, VitaElement becomes, double amount,
                                        double workShare) {
        if (!SpellEffects.isPlayerValid(context.player())) {
            return 0.0D;
        }
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        List<Source> taken = take(context, element, amount, context.currentAction().flatMap(SpellAction::place));
        if (taken.isEmpty()) {
            nothingFound(context, element);
            return 0.0D;
        }
        // The work of unmaking and remaking it takes its share of the energy: a little less of the new element comes out.
        double umu = taken.stream().mapToDouble(Source::value).sum() * (1.0D - workShare);
        if (becomes == VitaElement.AURA) {
            // Matter turned to air: it is gone into the air, and its UMU bursts out there as a gust of that pressure.
            Vec3 center = centreOf(taken);
            AirSpots.burst(level, player, center, umu);
            player.displayClientMessage(Component.literal(format(umu) + " UMU de " + element.runeId()
                    + " convertidos em ar."), true);
            return umu;
        }
        // As many units of the new element as the UMU buys (Conversion), where the sources were first, then in the
        // free spots around them.
        int wanted = Conversion.units(umu, becomes);
        int placed = 0;
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        for (Source source : taken) {
            open.add(source.pos());
            seen.add(source.pos());
        }
        int visited = 0;
        while (placed < wanted && !open.isEmpty() && visited < 4096) {
            BlockPos pos = open.poll();
            visited++;
            if (become(level, pos, element, becomes)) {
                placed++;
            }
            for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.DOWN,
                    net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST,
                    net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.UP}) {
                BlockPos next = pos.relative(side);
                if (seen.add(next) && next.distSqr(pos) <= 1 && level.isLoaded(next)
                        && level.getBlockState(next).canBeReplaced()) {
                    open.add(next);
                }
            }
        }
        // What made no whole unit, or found no room, is not lost: it goes into the mage as the new element.
        double left = Conversion.leftover(umu, becomes, placed);
        if (left > EPSILON) {
            context.absorbIntoBody(becomes, left);
        }
        player.displayClientMessage(Component.literal(format(umu) + " UMU de " + element.runeId() + " convertidos em "
                + placed + " de " + becomes.runeId() + (left > EPSILON ? " (" + format(left) + " UMU ao corpo)" : "")
                + "."), true);
        return umu;
    }

    private static Vec3 centreOf(List<Source> taken) {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (Source source : taken) {
            x += source.pos().getX() + 0.5D;
            y += source.pos().getY() + 0.5D;
            z += source.pos().getZ() + 0.5D;
        }
        return new Vec3(x / taken.size(), y / taken.size(), z / taken.size());
    }

    private static String format(double umu) {
        return com.elderlexicon.mod.command.SpellCostCalculator.formatCost(umu);
    }

    /** Where a source was drained, one unit of what it was converted into appears; false when there is no room. */
    private static boolean become(ServerLevel level, BlockPos pos, VitaElement from, VitaElement to) {
        BlockState now = level.getBlockState(pos);
        Vec3 at = Vec3.atCenterOf(pos);
        switch (to) {
            case AQUA -> {
                if (now.hasProperty(BlockStateProperties.WATERLOGGED) && !now.getValue(BlockStateProperties.WATERLOGGED)) {
                    // A quenched campfire, a candle: the water stays in the block.
                    level.setBlock(pos, now.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
                } else if (now.canBeReplaced() && !now.getFluidState().isSource()) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                } else {
                    return false;
                }
                level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 12, 0.3D, 0.3D, 0.3D, 0.0D);
                return true;
            }
            case IGNI -> {
                BlockState fire = BaseFireBlock.getState(level, pos);
                if (!now.canBeReplaced() || !now.getFluidState().isEmpty() || !fire.canSurvive(level, pos)) {
                    return false; // fire only where it can burn
                }
                level.setBlock(pos, fire, Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 12, 0.3D, 0.3D, 0.3D, 0.01D);
                return true;
            }
            case FIRMO -> {
                if (!now.canBeReplaced()) {
                    return false;
                }
                // Water turned to earth is mud; anything else, loose soil.
                BlockState earth = from == VitaElement.AQUA ? Blocks.MUD.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                level.setBlock(pos, earth, Block.UPDATE_ALL);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, earth), at.x, at.y, at.z, 16,
                        0.3D, 0.3D, 0.3D, 0.0D);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private record Source(BlockPos pos, double distanceSq, double value, BlockState state) {
    }

    private static Optional<Revelation.Kind> kindOf(VitaElement element) {
        return switch (element) {
            case IGNI -> Optional.of(Revelation.Kind.IGNI);
            case AQUA -> Optional.of(Revelation.Kind.AQUA);
            case FIRMO -> Optional.of(Revelation.Kind.FIRMO);
            case AURA -> Optional.of(Revelation.Kind.AURA);
            default -> Optional.empty();
        };
    }

    /** Where the mage's hand is: just before the body, at the height of the chest, where the source is pulled in. */
    public static Vec3 handOf(ServerPlayer player) {
        return player.getEyePosition().subtract(0.0D, 0.35D, 0.0D).add(player.getViewVector(1.0F).scale(0.9D));
    }

    // ------------------------------------------------------------------ what each source holds, and what it leaves

    /** The UMU a block gives up to exsugat, 0 when it holds none it can give. */
    static double valueOf(Revelation.Kind kind, ServerLevel level, BlockPos pos, BlockState state) {
        return switch (kind) {
            case IGNI -> igniValue(level, pos, state);
            case AQUA -> aquaValue(state);
            case FIRMO -> firmoValue(level, pos, state);
            // Air: every block of it in reach, leaving a vacuum where it was pulled from.
            case AURA -> state.isAir() ? AirPressure.UMU_PER_AIR : 0.0D;
            default -> 0.0D;
        };
    }

    private static double igniValue(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.FIRE) || state.is(Blocks.JACK_O_LANTERN)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)
                || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
            return 1.0D;
        }
        if (state.getBlock() instanceof AbstractCandleBlock && state.hasProperty(AbstractCandleBlock.LIT)
                && state.getValue(AbstractCandleBlock.LIT)) {
            return 0.5D;
        }
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            return 3.0D;
        }
        if (state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) {
            return 2.0D;
        }
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return 2.0D;
        }
        if (state.getFluidState().is(FluidTags.LAVA) && state.getFluidState().isSource()) {
            return 10.0D;
        }
        return 0.0D;
    }

    private static double aquaValue(BlockState state) {
        if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()) {
            return 3.0D;
        }
        if (state.is(Blocks.WATER_CAULDRON)) {
            return state.getValue(LayeredCauldronBlock.LEVEL);
        }
        if (state.is(Blocks.WET_SPONGE)) {
            return 3.0D;
        }
        if (state.is(Blocks.MUD)) {
            return 1.0D;
        }
        if (state.is(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) > 0) {
            return 1.0D;
        }
        return 0.0D;
    }

    /**
     * Earth gives what the revelation sees in it, its hardness (docs/surgit-visao-design.md): loose soil little, rock
     * more, obsidian a great deal. What holds things (a chest, a furnace) is not torn apart for its earth.
     */
    private static double firmoValue(ServerLevel level, BlockPos pos, BlockState state) {
        if (!RevelationSight.carries(Revelation.Kind.FIRMO, state) || state.hasBlockEntity()) {
            return 0.0D;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness < 0.0F ? 0.0D : Revelation.earthUmu(hardness);
    }

    /** Takes the source out of the block: the flame goes out, the water dries, the earth crumbles away. */
    private static void drain(Revelation.Kind kind, ServerLevel level, BlockPos pos, BlockState state) {
        switch (kind) {
            case IGNI -> drainIgni(level, pos, state);
            case AQUA -> drainAqua(level, pos, state);
            case FIRMO -> level.destroyBlock(pos, false);
            default -> {
            }
        }
    }

    private static void drainIgni(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.FIRE)) {
            level.removeBlock(pos, false);
        } else if (state.is(Blocks.JACK_O_LANTERN)) {
            level.setBlock(pos, Blocks.CARVED_PUMPKIN.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING)),
                    Block.UPDATE_ALL);
        } else if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
            // The flame goes; the stick it burned on is left.
            level.removeBlock(pos, false);
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.3D, pos.getZ() + 0.5D,
                    new ItemStack(Items.STICK));
        } else if (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
            level.removeBlock(pos, false);
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.3D, pos.getZ() + 0.5D,
                    new ItemStack(Items.IRON_NUGGET, 8));
        } else if (state.getBlock() instanceof AbstractCandleBlock) {
            level.setBlock(pos, state.setValue(AbstractCandleBlock.LIT, false), Block.UPDATE_ALL);
        } else if (state.getBlock() instanceof CampfireBlock) {
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
        } else if (state.getBlock() instanceof AbstractFurnaceBlock) {
            quenchFurnace(level, pos);
            level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, false), Block.UPDATE_ALL);
        } else if (state.is(Blocks.MAGMA_BLOCK)) {
            level.setBlock(pos, Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.getFluidState().is(FluidTags.LAVA)) {
            // Lava without its heat is rock.
            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void drainAqua(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.WATER_CAULDRON)) {
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.is(Blocks.WET_SPONGE)) {
            level.setBlock(pos, Blocks.SPONGE.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.is(Blocks.MUD)) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.is(Blocks.FARMLAND)) {
            level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 0), Block.UPDATE_ALL);
        } else if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * A lit furnace only looks lit while its fuel burns, so the burning itself is put out, or it would light again at
     * once and its fire could be pulled forever. The game keeps the burn time private, behind the furnace's data.
     */
    private static void quenchFurnace(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return;
        }
        try {
            if (furnaceData == null) {
                for (Field field : AbstractFurnaceBlockEntity.class.getDeclaredFields()) {
                    if (field.getType() == ContainerData.class) {
                        field.setAccessible(true);
                        furnaceData = field;
                        break;
                    }
                }
            }
            if (furnaceData != null) {
                ((ContainerData) furnaceData.get(furnace)).set(0, 0); // 0 is the burn time left
                furnace.setChanged();
            }
        } catch (ReflectiveOperationException ignored) {
            // The furnace just lights again; the flame was still taken this once.
        }
    }
}
