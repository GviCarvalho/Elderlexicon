package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.Pressure;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellFlow;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalDouble;

public final class IactareFunctionHandler implements SpellFunctionHandler {

    private static final double RANGE = 20.0D;
    private static final int CAST_DURATION_TICKS = 40; // 2 seconds default channel (book 4.3.2)
    private static final int CAST_STEP_TICKS = 5;
    private static final double BEAM_LENGTH = 8.0D; // matches the drawn projectile (16 segments x 0.5)

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<SpellAction> action = context.currentAction();
        if (action.isPresent() && action.get().image()) {
            ImageSpells.iactare(context, element, action.get());
            return;
        }
        Optional<String> subject = action.flatMap(SpellAction::subjectMark);
        if (subject.isPresent()) {
            // m1 iactare: the marked thing is thrown from where it is toward the aim (or the ubis place).
            MarkSpells.push(context, subject.get(), action.flatMap(SpellAction::place), MarkSpells.Push.TOWARD_AIM,
                    action.get().quantity().orElse(MarkCost.DEFAULT_THROW_ENERGY),
                    Chronos.window(action.get()));
            return;
        }
        // igni quantum 20 iactare throws 20 UMU instead of 10 (book 4.3.2); what goes beyond the default is paid.
        double perWindow = action.map(SpellAction::quantity).orElse(OptionalDouble.empty())
                .orElse(EmissionRecorder.DEFAULT_QUANTITY_UMU);
        // igni chronos 5 iactare keeps the tap open for 5 seconds instead of 2: the same flow, for longer, spending
        // more (SpellFlow); chronos 0 releases it at once.
        int duration = action.map(spell -> Chronos.ticks(spell, CAST_DURATION_TICKS)).orElse(CAST_DURATION_TICKS);
        double energy = SpellFlow.total(perWindow, duration);
        context.addTotalCost(energy - EmissionRecorder.DEFAULT_QUANTITY_UMU);
        // A condensation is gathered first: the release waits for its charge (the aim is read when it is released).
        int charge = action.map(SpellAction::charge).orElse(0);
        if (action.isPresent() && action.get().atOnce() && action.get().metadata().get(SpellAction.INTENSITY) != null) {
            releaseCondensed(level, player, element, action.get(), charge);
            return;
        }
        double power = energy / EmissionRecorder.DEFAULT_QUANTITY_UMU;
        int pulses = duration / CAST_STEP_TICKS + 1;
        // igni 200 ubis iactare reaches 200 blocks instead of 20; coordinates or a mark fix where it lands.
        Optional<SpellPlace> place = action.flatMap(SpellAction::place);
        double reach = place.filter(written -> written.kind() == SpellPlace.Kind.DISTANCE)
                .map(SpellPlace::distance).orElse(RANGE);
        Optional<MarkSpells.Destination> target = place.filter(written -> written.kind() != SpellPlace.Kind.DISTANCE)
                .flatMap(written -> MarkSpells.destination(context, place, RANGE));
        if (place.isPresent() && place.get().kind() != SpellPlace.Kind.DISTANCE && target.isEmpty()) {
            return;
        }

        // The evocation follows the mage's aim: every pulse draws the beam and lands its share of the effect where
        // the mage looks at that moment, so sweeping the aim spreads the effect; held still, it all lands on one spot.
        SpellEffects.Stream stream = new SpellEffects.Stream(power, pulses);
        double heat = action.map(SpellAction::intensity).orElse(Heat.COMMON);
        boolean[] reached = {false};
        for (int elapsed = 0; elapsed <= duration; elapsed += CAST_STEP_TICKS) {
            final boolean last = elapsed + CAST_STEP_TICKS > duration;
            SpellEffects.schedule(level, charge + elapsed, () -> {
                if (!SpellEffects.isPlayerValid(player)) {
                    return;
                }
                // The world scene draws the beam, so it fades as the laws use its energy up.
                // Each pulse outlives the gap to the next one by a tick. A pulse is recorded at the end of its tick, after
                // the scene has stepped, so a beam living exactly one interval left the scene empty for a tick between
                // pulses, and an empty scene forgets the charge and pressure it had built (no lightning, no steam).
                EmissionRecorder.beamFromCaster(context, element, BEAM_LENGTH, energy / pulses, CAST_STEP_TICKS + 1);
                SpellEffects.SpellImpact impact = target.map(MarkSpells.Destination::impact)
                        .orElseGet(() -> SpellEffects.findImpact(player, reach));
                boolean hit = SpellEffects.applyElementPulse(player, element, context.elementRuneId(), impact, stream);
                reached[0] |= hit;
                if (hit && heat > Heat.COMMON && element == VitaElement.IGNI) {
                    // Condensed fire: where it strikes, its heat stays and cools little by little.
                    Vec3 at = impact.entity() != null ? impact.entity().position() : impact.location();
                    HeatSpots.strike(level, player, at, heat);
                }
                if (last && !reached[0]) {
                    MarkSpells.tell(player, "O feitico se perdeu: nada ao alcance (" + Math.round(reach) + " blocos) onde voce mirava.");
                }
            });
        }
    }

    /** Runs {@code release} once the charge is gathered, if the mage is still there to release it. */
    private static void released(ServerLevel level, ServerPlayer player, int charge, Runnable release) {
        SpellEffects.schedule(level, charge, () -> {
            if (SpellEffects.isPlayerValid(player)) {
                release.run();
            }
        });
    }

    /** How fast a condensed orb flies, in blocks a tick. */
    private static final double ORB_SPEED = 2.0D;

    /**
     * A condensation, once its charge is gathered: fire and air fly as the orb they grew into and do what they carry
     * where it strikes (fire leaves its heat, air bursts or goes off as a bomb); water goes as a jet or a stone of
     * pressed ice, earth as a block with weight, and their orb is spent.
     */
    private static void releaseCondensed(ServerLevel level, ServerPlayer player, VitaElement element, SpellAction action,
                                         int charge) {
        double intensity = action.intensity();
        released(level, player, charge, () -> {
            ElementOrb orb = orbOf(level, action);
            Vec3 velocity = player.getViewVector(1.0F).scale(ORB_SPEED);
            switch (element) {
                case IGNI -> {
                    if (orb != null) {
                        orb.launch(velocity, at -> HeatSpots.strike(level, player, at, intensity));
                    } else {
                        HeatSpots.strike(level, player, SpellEffects.findImpact(player, RANGE).location(), intensity);
                    }
                }
                case AURA -> {
                    if (orb != null) {
                        orb.launch(velocity, at -> AirSpots.burst(level, player, at, intensity));
                    } else {
                        AirSpots.blast(level, player, intensity);
                    }
                }
                case AQUA -> {
                    spend(orb);
                    if (Pressure.band(intensity) == Pressure.Band.ICE) {
                        WaterSpots.launchIce(level, player, intensity);
                    } else {
                        WaterSpots.jet(level, player, intensity);
                    }
                }
                case FIRMO -> {
                    if (com.elderlexicon.mod.spell.Density.rock(intensity) == com.elderlexicon.mod.spell.Density.Rock.BLACK_HOLE
                            && orb != null) {
                        // No block can hold it: it flies as the black orb it is, and falls into itself where it strikes.
                        orb.launch(velocity, at -> BlackHole.form(level, at, intensity));
                    } else {
                        spend(orb);
                        EarthSpots.launch(level, player, intensity, action.carbon());
                    }
                }
                case BALANCED -> {
                    // Vis, pure energy: for now, only light where it lands.
                    if (orb != null) {
                        orb.launch(velocity, at -> VisSpots.light(level, at, intensity));
                    } else {
                        VisSpots.light(level, SpellEffects.findImpact(player, RANGE).location(), intensity);
                    }
                }
                default -> spend(orb);
            }
        });
    }

    /** The orb the condensation was gathered into, if it is still there. */
    private static ElementOrb orbOf(ServerLevel level, SpellAction action) {
        return action.orb() >= 0 && level.getEntity(action.orb()) instanceof ElementOrb orb ? orb : null;
    }

    private static void spend(ElementOrb orb) {
        if (orb != null) {
            orb.spend();
        }
    }
}
