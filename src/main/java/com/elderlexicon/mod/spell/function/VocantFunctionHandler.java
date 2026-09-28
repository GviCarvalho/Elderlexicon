package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.Pressure;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellFlow;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class VocantFunctionHandler implements SpellFunctionHandler {

    /** Vocant acts at once (book 8.4: it "makes the source appear where you point"); chronos stretches it, never delays it. */
    private static final int SUMMON_DELAY_TICKS = 0;
    private static final int LINGER_TICKS = 20;
    /** Where a marked thing brought to the mage is set down: just before them, along the aim. */
    private static final double BEFORE_THE_MAGE = 1.5D;

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
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
        // The ubis place is fixed when the spell is cast; without one, the aim is read when it lands.
        Optional<MarkSpells.Destination> written = place.isPresent()
                ? MarkSpells.destination(context, place, MarkSpells.SUMMON_RANGE)
                : Optional.empty();
        if (place.isPresent() && written.isEmpty()) {
            return;
        }
        if (written.isPresent() && written.get().level() != level) {
            MarkSpells.tell(player, "Esse lugar esta em outra dimensao.");
            return;
        }
        // aqua quantum 20 vocant brings twice the water (book 4.3.2, linear); what goes beyond 10 UMU is paid.
        double energy = action.map(SpellAction::quantity).orElse(OptionalDouble.empty())
                .orElse(EmissionRecorder.DEFAULT_QUANTITY_UMU);
        // chronos is how long what was summoned stays or keeps acting (book 4.3.2: "igni exsugat chronos firmo vocant"),
        // and a tap held open: it spends that much for every two seconds (SpellFlow).
        int window = action.map(Chronos::window).orElse(0);
        context.addTotalCost(SpellFlow.total(energy, window) - EmissionRecorder.DEFAULT_QUANTITY_UMU);
        int linger = window > 0 ? window : LINGER_TICKS;
        Optional<Supplier<Optional<MarkSpells.Destination>>> follow = place
                .filter(SpellPlace::followsMarks)
                .map(marked -> MarkSpells.follower(context, marked, MarkSpells.SUMMON_RANGE));

        boolean condensedEarth = element == VitaElement.FIRMO && action.isPresent() && action.get().atOnce()
                && action.get().metadata().get(SpellAction.INTENSITY) != null;
        boolean condensedWater = element == VitaElement.AQUA && action.isPresent() && action.get().atOnce()
                && action.get().metadata().get(SpellAction.INTENSITY) != null;
        boolean condensedAir = element == VitaElement.AURA && action.isPresent() && action.get().atOnce()
                && action.get().metadata().get(SpellAction.INTENSITY) != null;
        boolean condensedVis = element == VitaElement.BALANCED && action.isPresent() && action.get().atOnce()
                && action.get().metadata().get(SpellAction.INTENSITY) != null;
        // The verb after acts on what this makes (igni vocant iactare): it is handed on, to be made where it starts from.
        boolean condensed = condensedEarth || condensedWater || condensedAir || condensedVis;
        Product product = action.isPresent() && action.get().handsOn() && !condensed
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
            // The orb the condensation grew into becomes what it held.
            if (action.isPresent() && action.get().orb() >= 0
                    && level.getEntity(action.get().orb()) instanceof ElementOrb orb) {
                orb.spend();
            }
            if (!SpellEffects.isPlayerValid(player)) {
                return;
            }
            if (condensedVis) {
                // All that Vis released at the point: its four aspects at once.
                SpellEffects.SpellImpact at = aimed;
                VisSpots.release(level, player, at.location(), action.get().intensity());
                return;
            }
            if (condensedAir) {
                // All the air captured, released at the point: it bursts out all around, or goes off as a bomb.
                SpellEffects.SpellImpact at = aimed;
                AirSpots.burst(level, player, at.location(), action.get().intensity());
                return;
            }
            if (condensedWater) {
                // All the water captured, pressed into one point: ice VII held while chronos lasts (two seconds
                // without it), then it bursts; not pressed hard enough to freeze, it bursts at once.
                SpellEffects.SpellImpact at = aimed;
                net.minecraft.core.BlockPos spot = at.entity() != null ? at.entity().blockPosition()
                        : java.util.Objects.requireNonNullElse(SpellEffects.firePlacementPos(at),
                        net.minecraft.core.BlockPos.containing(at.location()));
                double pressure = action.get().intensity();
                if (Pressure.band(pressure) == Pressure.Band.ICE) {
                    WaterSpots.ice(level, spot, pressure, window > 0 ? window : LINGER_TICKS * 2);
                } else {
                    WaterSpots.burst(level, net.minecraft.world.phys.Vec3.atCenterOf(spot), pressure);
                }
                return;
            }
            if (condensedEarth) {
                // All the earth captured, in one block as dense as all of it (docs/condensacao-design.md).
                SpellEffects.SpellImpact at = aimed;
                net.minecraft.core.BlockPos spot = at.entity() != null ? at.entity().blockPosition()
                        : java.util.Objects.requireNonNullElse(SpellEffects.firePlacementPos(at),
                        net.minecraft.core.BlockPos.containing(at.location()));
                EarthSpots.place(level, player, spot, action.get().intensity(), action.get().carbon());
                return;
            }
            if (product != null && product.taken()) {
                // Taken by the verb after: it is made where it leaves from (the ubis place, or the mage's hand) and
                // does what it does where that verb carries it.
                Vec3 from = written.map(MarkSpells.Destination::point).orElseGet(() -> WorldSources.handOf(player));
                product.ready(level, from, landed -> appear(context, player, element, rune, landed,
                        Invocation.Where.fixed(landed), energy, window, linger));
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
            double heat = action.map(SpellAction::intensity).orElse(Heat.COMMON);
            if (heat > Heat.COMMON && element == VitaElement.IGNI) {
                // Condensed fire invoked in place: a hot spot that cools little by little.
                HeatSpots.strike(level, player, impact.location(), heat);
            }
        });
    }

    /** What is summoned appears at {@code impact} and does there what it does. */
    private static void appear(SpellContext context, ServerPlayer player, VitaElement element, String rune,
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
        Invocation.invoke(player, element, rune, where, energy / EmissionRecorder.DEFAULT_QUANTITY_UMU, window);
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
        WorldSources.absorb(context, element, element,
                action.quantity().orElse(WorldSources.DEFAULT_ABSORBED_UMU));
    }
}
