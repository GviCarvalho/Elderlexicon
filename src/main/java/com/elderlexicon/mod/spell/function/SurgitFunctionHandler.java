package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.sight.Revelation;
import com.elderlexicon.mod.spell.sight.Visibility;
import com.elderlexicon.mod.spelling.server.ServerSpellingController;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Surgit in a spell: the spirit looks through the mage's eyes (book 5.1.1) for what was named before it, and the mage
 * sees only that ({@code docs/surgit-visao-design.md}, section 1). A source ({@code igni surgit}) shows what carries
 * it, mana or none ({@code vis surgit}) shows everything that carries magic, and a mark ({@code r2 surgit}) shows what
 * bears that mark. A quantum before surgit is the UMU the gaze spends, which buys its reach
 * ({@code firmo quantum 20 surgit}); a quantum before the source is the UMU sought, compared in whole UMU
 * ({@code quantum 2 firmo surgit}: earth as dense as stone). Chronos sets how long the gaze lasts.
 *
 * <p>Seeing a scroll is reading it, so a mark also reads the scrolls that carry it: this is how a ritual cadences its
 * steps. Chronos sets when that reading happens ({@code r2 chronos 3 surgit}); without it the reading waits one tick,
 * so a scroll that reads itself loops instead of recursing, paying each round.
 */
public final class SurgitFunctionHandler implements SpellFunctionHandler {

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        Optional<SpellAction> action = context.currentAction();
        Optional<String> mark = action.flatMap(SpellAction::subjectMark);
        if (action.isPresent() && action.get().place().isPresent() && action.get().visibility().isEmpty()
                && !action.get().sightBond()) {
            // 10 ubis surgit, m1 ubis surgit: the spirit leaves the body and looks from there (section 3).
            AstralProjections.start(context, action.get().place(),
                    action.get().seconds().isPresent() ? action.get().seconds().getAsDouble() : null);
            return;
        }
        if (action.isPresent() && action.get().sightBond() && mark.isPresent()) {
            // surgit m1 ligabis: the mage sees through m1's eyes (docs/surgit-visao-design.md, section 2).
            SightBonds.start(context, mark.get(),
                    action.get().seconds().isPresent() ? action.get().seconds().getAsDouble() : null);
            return;
        }
        if (action.isPresent() && action.get().visibility().isPresent()) {
            // surgit m1 quantum N: how much of m1 is seen; surgit quantum N: of what the mage aims at within touch
            // (docs/surgit-visao-design.md, section 5).
            Double duration = action.get().seconds().isPresent() ? action.get().seconds().getAsDouble() : null;
            int level = Visibility.level(action.get().visibility().getAsDouble());
            if (mark.isPresent()) {
                context.addTotalCost(SpellVisibility.apply(player, mark.get(), level, duration));
                return;
            }
            double owed = SpellVisibility.applyAimed(player, level, duration);
            if (owed < 0.0D) {
                MarkSpells.tell(player, "Nada ao alcance do toque para mudar a visibilidade.");
            } else {
                context.addTotalCost(owed);
            }
            return;
        }
        Double potency = action.filter(a -> a.potency().isPresent()).map(a -> a.potency().getAsDouble()).orElse(null);
        Double sought = action.filter(a -> a.sourceValue().isPresent()).map(a -> a.sourceValue().getAsDouble()).orElse(null);
        Double chronos = action.filter(a -> a.seconds().isPresent()).map(a -> a.seconds().getAsDouble()).orElse(null);

        Revelation.Kind kind = mark.isPresent()
                ? Revelation.Kind.MARK
                : Revelation.Kind.ofSource(action.map(a -> a.metadata().get("elementRuneId"))
                        .map(Object::toString).orElse(null));
        double seconds = Revelation.seconds(chronos);
        double radius = Revelation.radius(potency, seconds);
        context.addTotalCost(Revelation.cost(radius, seconds));
        RevelationSight.begin(player, kind, mark.orElse(null), sought, radius, Revelation.ticks(seconds));

        if (mark.isEmpty()) {
            return;
        }
        int delay = Math.max(1, Chronos.window(action.get()));
        SpellEffects.schedule(player.serverLevel(), delay, () -> {
            if (SpellEffects.isPlayerValid(player)) {
                ServerSpellingController.getInstance().readMarkedScrolls(player, mark.get());
            }
        });
    }
}
