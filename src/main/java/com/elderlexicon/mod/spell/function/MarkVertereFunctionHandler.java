package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Vertere on a marked thing: {@code m1 vertere aqua} changes its state, {@code m1 vertere m2} gives what bears m1 the
 * mark m2, and {@code aqua quantum 16 vertere m1} changes its core ({@link Cores}). Vertere between sources
 * ({@code igni vertere aqua}) stays in {@code SpellActionExecutor}, which only sends marked spells here.
 */
public final class MarkVertereFunctionHandler implements SpellFunctionHandler {

    @Override
    public void execute(SpellContext context, VitaElement element) {
        if (!SpellEffects.isPlayerValid(context.player())) {
            return;
        }
        Optional<SpellAction> action = context.currentAction();
        Optional<String> subject = action.flatMap(SpellAction::subjectMark);
        Optional<String> newMark = action.flatMap(SpellAction::targetMark);
        if (subject.isEmpty()) {
            newMark.ifPresent(mark -> core(context, action.get(), element, mark));
            return;
        }
        if (newMark.isPresent()) {
            MarkSpells.rename(context, subject.get(), newMark.get(), Chronos.ticks(action.get(), 0));
            return;
        }
        String targetRuneId = action.get().targetRuneId();
        if (targetRuneId == null) {
            MarkSpells.tell(context.player(), "Vertere precisa de uma fonte ou de uma marca depois dela.");
            return;
        }
        MarkSpells.convert(context, subject.get(), VitaElement.fromRuneId(targetRuneId), targetRuneId,
                action.get().quantity(), Chronos.ticks(action.get(), 0));
    }

    /**
     * {@code aqua quantum 16 vertere m1}: the source written is the primordial whose parts of the core change, the
     * quantum how many of the hundred. Written with no number (no quantum, or a bare one), it shares evenly what the
     * numbers leave with the other lines written so: {@code aqua vertere m1} alone makes m1 all water (user, 01/10/2026).
     */
    private static void core(SpellContext context, SpellAction action, VitaElement element, String mark) {
        OptionalDouble parts = action.quantityAll() ? OptionalDouble.empty() : action.quantity();
        Cores.ask(context, mark, element, parts, Chronos.ticks(action, 0));
    }
}
