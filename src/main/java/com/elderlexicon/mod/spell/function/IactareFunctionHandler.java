package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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
        Optional<String> subject = action.flatMap(SpellAction::subjectMark);
        if (subject.isPresent()) {
            // m1 iactare: the marked thing is thrown from where it is toward the aim (or the ubis place).
            MarkSpells.push(context, subject.get(), action.flatMap(SpellAction::place), MarkSpells.Push.TOWARD_AIM,
                    action.get().quantity().orElse(MarkCost.DEFAULT_THROW_ENERGY),
                    Chronos.window(action.get()));
            return;
        }
        // igni quantum 20 iactare throws 20 UMU instead of 10 (book 4.3.2); what goes beyond the default is paid.
        double energy = action.map(SpellAction::quantity).orElse(OptionalDouble.empty())
                .orElse(EmissionRecorder.DEFAULT_QUANTITY_UMU);
        context.addTotalCost(energy - EmissionRecorder.DEFAULT_QUANTITY_UMU);
        double power = energy / EmissionRecorder.DEFAULT_QUANTITY_UMU;
        // igni chronos 5 iactare stretches the 2 second evocation to 5; chronos 0 releases it at once.
        int duration = action.map(spell -> Chronos.ticks(spell, CAST_DURATION_TICKS)).orElse(CAST_DURATION_TICKS);
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
        boolean[] reached = {false};
        for (int elapsed = 0; elapsed <= duration; elapsed += CAST_STEP_TICKS) {
            final boolean last = elapsed + CAST_STEP_TICKS > duration;
            SpellEffects.schedule(level, elapsed, () -> {
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
                reached[0] |= SpellEffects.applyElementPulse(player, element, context.elementRuneId(), impact, stream);
                if (last && !reached[0]) {
                    MarkSpells.tell(player, "O feitico se perdeu: nada ao alcance (" + Math.round(reach) + " blocos) onde voce mirava.");
                }
            });
        }
    }
}
