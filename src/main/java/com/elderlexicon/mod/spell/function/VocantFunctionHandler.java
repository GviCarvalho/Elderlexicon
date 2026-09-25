package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class VocantFunctionHandler implements SpellFunctionHandler {

    /** Vocant acts at once (book 8.4: it "makes the source appear where you point"); chronos stretches it, never delays it. */
    private static final int SUMMON_DELAY_TICKS = 0;
    private static final int LINGER_TICKS = 20;

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<SpellAction> action = context.currentAction();
        Optional<SpellPlace> place = action.flatMap(SpellAction::place);
        Optional<String> subject = action.flatMap(SpellAction::subjectMark);
        if (subject.isPresent()) {
            // m1 vocant: what carries the mark appears where the mage aims (or at the ubis place); with chronos it goes
            // back where it was when the window ends, like any permanent matter summoned for a time.
            MarkSpells.summon(context, subject.get(), place, SUMMON_DELAY_TICKS, Chronos.window(action.get()));
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
        context.addTotalCost(energy - EmissionRecorder.DEFAULT_QUANTITY_UMU);
        double power = energy / EmissionRecorder.DEFAULT_QUANTITY_UMU;
        // chronos is how long what was summoned stays or keeps acting (book 4.3.2: "igni exsugat chronos firmo vocant").
        int window = action.map(Chronos::window).orElse(0);
        int linger = window > 0 ? window : LINGER_TICKS;
        Optional<Supplier<Optional<MarkSpells.Destination>>> follow = place
                .filter(SpellPlace::followsMarks)
                .map(marked -> MarkSpells.follower(context, marked, MarkSpells.SUMMON_RANGE));

        SpellEffects.schedule(level, SUMMON_DELAY_TICKS, () -> {
            if (!SpellEffects.isPlayerValid(player)) {
                return;
            }
            SpellEffects.SpellImpact impact = written
                    .map(MarkSpells.Destination::impact)
                    .orElseGet(() -> SpellEffects.findImpact(player, MarkSpells.SUMMON_RANGE));
            SpellEffects.spawnSummonEffect(player, element, context.elementRuneId(), impact);
            EmissionRecorder.pointAt(context, element, impact.location(), energy, linger);
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
            Invocation.invoke(player, element, context.elementRuneId(), where, power, window);
        });
    }
}
