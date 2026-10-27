package com.elderlexicon.mod.spell.function;

import net.minecraft.world.entity.LivingEntity;
import com.elderlexicon.mod.spell.Casters;

import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.Pressure;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellFlow;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spell.nature.NatureWorld;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class VocantFunctionHandler implements SpellFunctionHandler {

    /**
     * What a vocant brings when no quantity says otherwise: one block of it, 16 UMU (docs/particulas-design.md, decided
     * 30/09/2026). Its effects are measured against it: a vocant of 16 UMU of fire burns as one of 10 did before.
     */
    public static final double DEFAULT_UMU = Particles.BLOCK_UMU;
    /** Vocant acts at once (book 8.4: it "makes the source appear where you point"); chronos stretches it, never delays it. */
    private static final int SUMMON_DELAY_TICKS = 0;
    private static final int LINGER_TICKS = 20;
    /** Where a marked thing brought to the mage is set down: just before them, along the aim. */
    private static final double BEFORE_THE_MAGE = 1.5D;

    @Override
    public void execute(SpellContext context, VitaElement element) {
        LivingEntity player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        ServerLevel level = Casters.level(player);
        Optional<SpellAction> action = context.currentAction();
        if (action.isPresent() && action.get().reversed()) {
            bringToCaster(context, element, action.get());
            return;
        }
        if (action.isPresent() && action.get().image()) {
            ImageSpells.vocant(context, element, action.get());
            return;
        }
        Optional<SpellPlace> place = action.flatMap(SpellAction::place);
        Optional<String> subject = action.flatMap(SpellAction::subjectMark);
        // Into a marked square of an invocation bench, what is invoked goes into the square (docs/invocacao-design.md).
        Optional<String> square = place.filter(at -> at.kind() == SpellPlace.Kind.MARK).map(SpellPlace::mark)
                .filter(mark -> com.elderlexicon.mod.invocation.BenchInvocation.isSquare(level.getServer(), mark));
        if (square.isPresent()) {
            intoSquare(context, element, action.get(), subject, square.get());
            return;
        }
        if (subject.isPresent()) {
            // m1 vocant: what carries the mark appears where the mage aims (or at the ubis place); with chronos it goes
            // back where it was when the window ends, like any permanent matter summoned for a time.
            MarkSpells.summon(context, subject.get(), place, SUMMON_DELAY_TICKS, Chronos.window(action.get()));
            return;
        }
        if (action.isPresent() && action.get().fromWorld() && !action.get().chained() && !action.get().gathers()) {
            // firmo tenet vocant: the matter is taken from the world as it is and put where the vocant puts things (to
            // gather it all, or condense it, the flow captures it as energy instead).
            Transfer.fromWorld(context, element, action.get());
            return;
        }
        // A source invoked into a marked wand goes into its gem; what the gem does not take appears where the wand is.
        Optional<SettingCharge.Bearer> gem = SettingCharge.find(level.getServer(), place);
        // The ubis place is fixed when the spell is cast; without one, the aim is read when it lands.
        Optional<MarkSpells.Destination> written = gem.isPresent()
                ? gem.map(SettingCharge.Bearer::destination)
                : place.isPresent()
                ? MarkSpells.destination(context, place, MarkSpells.SUMMON_RANGE)
                : Optional.empty();
        if (place.isPresent() && written.isEmpty()) {
            return;
        }
        if (written.isPresent() && written.get().level() != level) {
            MarkSpells.tell(player, "Esse lugar esta em outra dimensao.");
            return;
        }
        // aqua quantum 32 vocant brings twice the water (book 4.3.2, linear); what goes beyond one block is paid. Fire is
        // the exception: by itself, igni vocant brings what makes a flame of a block of air (user, 03/10/2026).
        double usual = element == VitaElement.IGNI ? NatureWorld.flame() : DEFAULT_UMU;
        // quantum 200 firmo quantum 4 vocant: the quantum before the source is how much, the one before the vocant how
        // many blocks it covers; with one quantum alone, it is how much, all of it in one block.
        int area = areaOf(action);
        double asked = area > 1 ? action.get().sourceValue().getAsDouble()
                : action.map(SpellAction::quantity).orElse(OptionalDouble.empty()).orElse(usual);
        // chronos is how long what was summoned stays or keeps acting (book 4.3.2: "igni exsugat chronos firmo vocant"),
        // and a tap held open: it spends that much for every two seconds (SpellFlow).
        int window = action.map(Chronos::window).orElse(0);
        context.addTotalCost(SpellFlow.total(asked, window) - usual);
        // The gem takes what is invoked into it in one instant; what it does not take is invoked as usual.
        boolean intoGem = gem.isPresent() && window == 0
                && action.map(spell -> !spell.handsOn() && !spell.atOnce()).orElse(true);
        double energy = intoGem ? SettingCharge.charge(player, gem.get(), element, asked) : asked;
        if (energy <= 1.0E-4D) {
            return;
        }
        int linger = window > 0 ? window : LINGER_TICKS;
        Optional<Supplier<Optional<MarkSpells.Destination>>> follow = place
                .filter(SpellPlace::followsMarks)
                .map(marked -> MarkSpells.follower(context, marked, MarkSpells.SUMMON_RANGE));

        // A vocant invokes into one block (user, 17/10/2026): all it brings is pressed into it, and how much is in that
        // one block decides what appears there. Up to one block's worth it is the common element; past it, earth as
        // dense as all of it, water pressed (ice past a point), air pressed (a bomb past a point), heat that melts and,
        // past a point, plasma. No chronos is needed for any of it; held open with chronos it is the common element.
        double each = energy / area;
        boolean concentrating = window <= 0 && !intoGem && action.map(SpellAction::concentrates).orElse(false);
        double intensity = concentrating ? intensityOf(element, each, action.get()) : 0.0D;
        boolean condensed = intensity > 0.0D && area == 1;
        // Spread over an area, earth is laid as blocks each as dense as its share.
        Double earthDensity = element == VitaElement.FIRMO && intensity > 0.0D && area > 1 ? intensity : null;
        // The verb after acts on what this makes (igni vocant iactare): it is handed on, to be made where it starts from.
        // A concentration is handed on too (igni quantum 40 vocant iactare): gathered before the hand, it flies as its
        // orb and is released where it strikes.
        Product product = action.isPresent() && action.get().handsOn()
                ? new Product(element, SpellFlow.total(energy, window)) : null;
        context.handOn(product); // nothing, when no verb after takes it: what an earlier verb left is not handed on
        String rune = context.elementRuneId();
        // A condensation is gathered where it will appear before it does: where the mage aimed when the gathering began,
        // wherever they look by the time it is released.
        int charge = action.map(SpellAction::charge).orElse(0);
        SpellEffects.SpellImpact aimed = charge > 0 ? written.map(MarkSpells.Destination::impact)
                .orElseGet(() -> SpellEffects.findImpact(player, MarkSpells.SUMMON_RANGE)) : null;
        // What is handed on waits a tick, for the verbs after it to be read and the one acting on it to take it.
        int delay = Math.max(product != null ? 1 : SUMMON_DELAY_TICKS, charge);
        SpellEffects.schedule(level, delay, () -> {
            ElementOrb orb = action.isPresent() && action.get().orb() >= 0
                    && level.getEntity(action.get().orb()) instanceof ElementOrb gathered ? gathered : null;
            if (!SpellEffects.isPlayerValid(player)) {
                if (orb != null) {
                    orb.spend();
                }
                return;
            }
            if (product != null && product.taken()) {
                // Taken by the verb after: it is made where it leaves from (the ubis place, or the mage's hand; a
                // condensation, where its orb is) and does what it does where that verb carries it.
                Vec3 from = orb != null ? orb.position()
                        : written.map(MarkSpells.Destination::point).orElseGet(() -> WorldSources.handOf(player));
                if (orb != null) {
                    product.carriedBy(orb);
                }
                product.ready(level, from, landed -> {
                    if (condensed) {
                        releaseCondensed(level, player, element, action.get(), intensity, landed, window);
                        return;
                    }
                    appear(context, player, element, rune, landed, Invocation.Where.fixed(landed), energy, window,
                            linger);
                    hotSpot(level, player, element, action, landed.location());
                });
                return;
            }
            // The orb the condensation grew into becomes what it held.
            if (orb != null) {
                orb.spend();
            }
            // What is invoked in one instant is weighed together first (docs/vita-design.md): energies that bind into
            // a being take no other form.
            SpellEffects.SpellImpact meeting = aimed != null ? aimed : written.map(MarkSpells.Destination::impact)
                    .orElseGet(() -> SpellEffects.findImpact(player, MarkSpells.SUMMON_RANGE));
            Quickening.offer(level, player, meeting.location(), element, energy, () -> {
            if (!SpellEffects.isPlayerValid(player)) {
                return;
            }
            if (condensed) {
                releaseCondensed(level, player, element, action.get(), intensity,
                        aimed != null ? aimed : meeting, window);
                return;
            }
            if (earthDensity != null) {
                SpellEffects.SpellImpact at = aimed != null ? aimed : written.map(MarkSpells.Destination::impact)
                        .orElseGet(() -> SpellEffects.findImpact(player, MarkSpells.SUMMON_RANGE));
                double left = Invocation.dense(player, Invocation.Where.fixed(at), energy, earthDensity);
                if (left > 1.0E-9D && !context.focusActive()) {
                    VitaSystem.restoreElementEnergy(player, element, left);
                }
                return;
            }
            SpellEffects.SpellImpact impact = aimed != null ? aimed : written
                    .map(MarkSpells.Destination::impact)
                    .orElseGet(() -> SpellEffects.findImpact(player, MarkSpells.SUMMON_RANGE));
            // A place written with marks moves with them: while chronos lasts, the invocation follows.
            Invocation.Where where = follow.map(following -> (Invocation.Where) new Invocation.Where() {
                @Override
                public Optional<SpellEffects.SpellImpact> now() {
                    return following.get().filter(now -> now.level() == level).map(MarkSpells.Destination::impact);
                }

                @Override
                public boolean atFeet() {
                    return true;
                }
            }).orElseGet(() -> Invocation.Where.fixed(impact));
            appear(context, player, element, context.elementRuneId(), impact, where, energy, window, linger);
            hotSpot(level, player, element, action, impact.location());
            });
        });
    }

    /**
     * What is invoked into a square of an invocation bench goes into it: a marked thing as it is, a source as so many
     * particles of it, paid as any vocant is (and a chronos, the time it will last once out of the bench, as a tap). What
     * the square does not take goes back into the body.
     */
    private static void intoSquare(SpellContext context, VitaElement element, SpellAction action,
                                   Optional<String> subject, String square) {
        LivingEntity player = context.player();
        ServerLevel level = Casters.level(player);
        if (subject.isPresent()) {
            java.util.List<net.minecraft.world.entity.item.ItemEntity> things = new java.util.ArrayList<>();
            for (MarkTargets.Marked thing : MarkTargets.find(level.getServer(), subject.get())) {
                if (thing.entity instanceof net.minecraft.world.entity.item.ItemEntity lying) {
                    things.add(lying);
                }
            }
            com.elderlexicon.mod.invocation.BenchInvocation.receive(level.getServer(), player, square, things);
            return;
        }
        double usual = element == VitaElement.IGNI ? NatureWorld.flame() : DEFAULT_UMU;
        double asked = action.quantity().orElse(usual);
        int window = Chronos.window(action);
        context.addTotalCost(SpellFlow.total(asked, window) - usual);
        if (asked <= 1.0E-4D) {
            return;
        }
        double left = com.elderlexicon.mod.invocation.BenchInvocation.receive(level.getServer(), player, square,
                element, asked, window);
        if (left > 1.0E-9D && !context.focusActive() && !element.isBalanced()) {
            VitaSystem.restoreElementEnergy(player, element, left);
        }
    }

    /** Condensed fire where it appears: a hot spot that cools little by little. */
    private static void hotSpot(ServerLevel level, LivingEntity player, VitaElement element,
                                Optional<SpellAction> action, Vec3 at) {
        double heat = action.map(SpellAction::intensity).orElse(Heat.COMMON);
        if (heat > Heat.COMMON && element == VitaElement.IGNI) {
            HeatSpots.strike(level, player, at, heat);
        }
    }

    /**
     * All that was condensed, released at {@code at}: vis shows its four aspects at once; air bursts out all around, or
     * goes off as a bomb; water is pressed into ice VII held while chronos lasts (two seconds without it) and then
     * bursts, or bursts at once when it was not pressed hard enough to freeze; earth is one block as dense as all of
     * it; fire is a heat that melts and, hot enough, is plasma (docs/condensacao-design.md).
     */
    private static void releaseCondensed(ServerLevel level, LivingEntity player, VitaElement element,
                                         SpellAction action, double intensity, SpellEffects.SpellImpact at,
                                         int window) {
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        switch (element) {
            case IGNI -> HeatSpots.strike(level, player, at.location(), intensity);
            case BALANCED -> VisSpots.release(level, player, at.location(), intensity);
            case AURA -> AirSpots.burst(level, player, at.location(), intensity);
            case AQUA -> {
                net.minecraft.core.BlockPos spot = spotOf(at);
                if (Pressure.band(intensity) == Pressure.Band.ICE) {
                    WaterSpots.ice(level, spot, intensity, window > 0 ? window : LINGER_TICKS * 2);
                } else {
                    WaterSpots.burst(level, Vec3.atCenterOf(spot), intensity);
                }
            }
            case FIRMO -> {
                EarthSpots.place(level, player, spotOf(at), intensity, action.carbon());
                // The rock it settles as holds what a block of it holds; what the block does not hold goes back into
                // the body, as what makes no whole block does.
                double invested = intensity * com.elderlexicon.mod.spell.Density.UMU_PER_DENSITY;
                double left = EarthSpots.settled(intensity)
                        .map(rock -> invested - rock.packing() * Particles.BLOCK_UMU).orElse(0.0D);
                if (left > 1.0E-6D && action.carbon() <= 0) {
                    VitaSystem.restoreElementEnergy(player, VitaElement.FIRMO, left);
                }
            }
            default -> {
            }
        }
    }

    /** How many blocks a vocant covers: the quantum before it, when another before the source says how much. */
    private static int areaOf(Optional<SpellAction> action) {
        if (action.isEmpty() || action.get().fromWorld() || action.get().sourceValue().isEmpty()
                || action.get().potency().isEmpty()) {
            return 1;
        }
        return (int) Math.max(1L, Math.min(4096L, Math.round(action.get().potency().getAsDouble())));
    }

    /**
     * How intense {@code umu} pressed into one block is, in each aspect's own measure, or 0 when it is no more than one
     * block of the common element holds: earth's density (UMU in the block over {@link
     * com.elderlexicon.mod.spell.Density#UMU_PER_DENSITY}); water's pressure (a still source, one block of it, is
     * {@link Pressure#SOURCE}); air's (a block of it is {@link com.elderlexicon.mod.spell.AirPressure#UMU_PER_AIR});
     * fire's heat (a flame is 1). Vis pressed into one point only when it was gathered.
     */
    static double intensityOf(VitaElement element, double umu, SpellAction action) {
        double block = Particles.BLOCK_UMU;
        return switch (element) {
            case FIRMO -> umu > block + 1.0E-6D ? umu / com.elderlexicon.mod.spell.Density.UMU_PER_DENSITY : 0.0D;
            case AQUA -> umu > block + 1.0E-6D ? umu / block * Pressure.SOURCE : 0.0D;
            case AURA -> umu > block + 1.0E-6D ? umu / block * com.elderlexicon.mod.spell.AirPressure.UMU_PER_AIR : 0.0D;
            case IGNI -> umu > NatureWorld.flame() + 1.0E-6D ? umu / NatureWorld.flame() : 0.0D;
            case BALANCED -> action.metadata().get(SpellAction.INTENSITY) != null ? action.intensity() : 0.0D;
            default -> 0.0D;
        };
    }

    /** The block a condensation is released into: where the creature stands, or before the face that was struck. */
    private static net.minecraft.core.BlockPos spotOf(SpellEffects.SpellImpact at) {
        return at.entity() != null ? at.entity().blockPosition()
                : java.util.Objects.requireNonNullElse(SpellEffects.firePlacementPos(at),
                net.minecraft.core.BlockPos.containing(at.location()));
    }

    /** What is summoned appears at {@code impact} and does there what it does. */
    private static void appear(SpellContext context, LivingEntity player, VitaElement element, String rune,
                               SpellEffects.SpellImpact impact, Invocation.Where where, double energy, int window,
                               int linger) {
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        SpellEffects.spawnSummonEffect(player, element, rune, impact);
        // Brought for good into fluid matter (aqua vocant into a pool of molten earth), it is poured in and mixes with it
        // (L4); summoned for a while, or anywhere else, it appears as it always does.
        if (window <= 0 && Pouring.summon(context, player, rune, impact, energy)) {
            return;
        }
        EmissionRecorder.pointAt(context, element, impact.location(), SpellFlow.total(energy, window), linger);
        boolean body = !context.focusActive();
        double left = Invocation.invoke(player, element, rune, where, energy, window, later -> {
            // What a flame held for a while did not spend goes back into the body too.
            if (body && later > 1.0E-9D && SpellEffects.isPlayerValid(player)) {
                VitaSystem.restoreElementEnergy(player, element, later);
            }
        });
        if (left > 1.0E-9D && body) {
            // What made no whole block, or found no room, goes back into the body (L1).
            VitaSystem.restoreElementEnergy(player, element, left);
        }
    }

    /**
     * vocant turned around (a negative quantity): what is at the point comes to the mage instead of going there. A marked
     * thing is brought before the mage; a source is taken into the body, as much as the quantity says, from around the
     * ubis place or, with none, from what is nearest the mage's hand; an image is drawn in.
     */
    private static void bringToCaster(SpellContext context, VitaElement element, SpellAction action) {
        if (action.image()) {
            ImageSpells.absorb(context, element, action);
            return;
        }
        Optional<String> subject = action.subjectMark();
        if (subject.isPresent()) {
            MarkSpells.summon(context, subject.get(), Optional.of(SpellPlace.distance(BEFORE_THE_MAGE)),
                    SUMMON_DELAY_TICKS, Chronos.window(action));
            return;
        }
        if (element.isBalanced()) {
            // vis quantum -10 vocant: a quarter of each of the four, out of the blocks around, fused into vis.
            VisDraw.absorb(context, action.quantity().orElse(WorldSources.DEFAULT_ABSORBED_UMU));
            return;
        }
        WorldSources.absorb(context, element, element,
                action.quantity().orElse(WorldSources.DEFAULT_ABSORBED_UMU));
    }
}
