package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.Optional;

/**
 * Transvocatio (vertere + vocant) swaps two things: {@code m1 transvocatio m2}. A missing side is the
 * caster, so {@code m1 transvocatio} and {@code transvocatio m2} swap the mage with the marked things.
 */
public final class TransvocatioFunctionHandler implements SpellFunctionHandler {

    @Override
    public void execute(SpellContext context, VitaElement element) {
        if (!SpellEffects.isPlayerValid(context.player())) {
            return;
        }
        Optional<SpellAction> action = context.currentAction();
        Optional<String> subject = action.flatMap(SpellAction::subjectMark);
        Optional<String> target = action.flatMap(SpellAction::targetMark);
        if (subject.isEmpty() && target.isEmpty()) {
            MarkSpells.tell(context.player(), "Transvocatio troca coisas marcadas: escreva uma marca antes ou depois dela.");
            return;
        }
        MarkSpells.swap(context, subject, target, Chronos.ticks(action.get(), MarkSpells.SWAP_DELAY_TICKS));
    }
}
