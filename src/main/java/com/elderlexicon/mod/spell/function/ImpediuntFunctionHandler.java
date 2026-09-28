package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.Barrier;
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
import java.util.function.Supplier;

/**
 * Impediunt, "they impede" (docs/impediunt-design.md). With a source it opens a zone its element cannot enter: what of
 * the element is inside is moved past the edge (fire and water kept out, earth piled into a wall), and while the zone
 * lasts nothing of it comes back in.
 * <ul>
 *   <li>{@code ubis} sets the centre: the mage without one, a mark (followed while it lasts) or a place.</li>
 *   <li>{@code quantum} is the UMU put in, and they buy area ({@link Barrier#radius}): 10 keep three blocks clear.</li>
 *   <li>{@code chronos} keeps the zone for that long, a tap held open (SpellFlow); two seconds without it.</li>
 * </ul>
 * With a mark before it ({@code m1 impediunt}) it pushes the marked thing away instead; after a verb whose result it
 * acts on ({@code igni vocant impediunt}), it pushes that away from the centre. Turned around (a negative quantity) it
 * draws in: the zone pulls its element toward the centre ({@code aura quantum -30 chronos 5 impediunt}, a vortex).
 */
public final class ImpediuntFunctionHandler implements SpellFunctionHandler {

    /** How long a zone keeps its element out without chronos: the default window of two seconds (book 4.3.2). */
    private static final int DEFAULT_TICKS = 40;

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        Optional<SpellAction> action = context.currentAction();
        if (action.isPresent() && action.get().image()) {
            ImageSpells.repel(context, element, action.get());
            return;
        }
        if (action.flatMap(SpellAction::subjectMark).isPresent()) {
            // m1 impediunt: the marked thing is pushed away from the mage (or from the ubis place); turned around, it is
            // drawn in.
            MarkSpells.Push push = action.get().reversed() ? MarkSpells.Push.TOWARD_CASTER
                    : MarkSpells.Push.AWAY_FROM_CASTER;
            MarkSpells.push(context, action.get().subjectMark().get(), action.get().place(), push,
                    action.get().quantity().orElse(MarkCost.DEFAULT_THROW_ENERGY), Chronos.window(action.get()));
            return;
        }

        // igni vocant impediunt: what the vocant made is pushed away from the centre (drawn in, turned around).
        if (action.isPresent() && Forces.onProduct(context, action.get(),
                action.get().reversed() ? MarkSpells.Push.TOWARD_CASTER : MarkSpells.Push.AWAY_FROM_CASTER)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        Optional<SpellPlace> place = action.flatMap(SpellAction::place);
        Supplier<Optional<Vec3>> center;
        if (place.isEmpty()) {
            // Around the mage, going where the mage goes.
            center = () -> SpellEffects.isPlayerValid(player) && player.level() == level
                    ? Optional.of(player.position()) : Optional.empty();
        } else if (place.get().followsMarks()) {
            Supplier<Optional<MarkSpells.Destination>> follower =
                    MarkSpells.follower(context, place.get(), MarkSpells.SUMMON_RANGE);
            center = () -> follower.get().filter(now -> now.level() == level).map(MarkSpells.Destination::point);
        } else {
            Optional<MarkSpells.Destination> written = MarkSpells.destination(context, place, MarkSpells.SUMMON_RANGE);
            if (written.isEmpty()) {
                return;
            }
            if (written.get().level() != level) {
                MarkSpells.tell(player, "Esse lugar esta em outra dimensao.");
                return;
            }
            Vec3 point = written.get().point();
            center = () -> Optional.of(point);
        }

        // igni quantum 40 impediunt keeps six blocks clear instead of three; what goes beyond 10 UMU is paid, and held
        // open with chronos it spends that much for every two seconds.
        double umu = action.map(SpellAction::quantity).orElse(OptionalDouble.empty()).orElse(Barrier.DEFAULT_UMU);
        int window = action.map(Chronos::window).orElse(0);
        context.addTotalCost(SpellFlow.total(umu, window) - Barrier.DEFAULT_UMU);
        // Turned around (a negative quantity), the force points in: the zone draws its element toward the centre.
        boolean drawsIn = action.map(SpellAction::reversed).orElse(false);
        ImpediuntZones.open(player, element, center, Barrier.radius(umu), window > 0 ? window : DEFAULT_TICKS, drawsIn);
        SpellEffects.playImpediuntSound(player);
    }
}
