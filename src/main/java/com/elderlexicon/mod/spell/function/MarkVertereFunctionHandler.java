package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.Optional;

/**
 * Vertere on a marked thing ({@code m1 vertere aqua}, {@code m1 vertere m2}). Vertere between sources
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
        if (subject.isEmpty()) {
            return;
        }
        Optional<String> newMark = action.flatMap(SpellAction::targetMark);
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
}
