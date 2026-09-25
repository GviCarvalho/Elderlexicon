package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spelling.server.ServerSpellingController;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Surgit with a mark ({@code r2 surgit}), written in a scroll: the spirit reads the scrolls carrying that mark,
 * which is how a ritual cadences its steps (book 5.1: Surgit is the reading command). Chronos sets when
 * ({@code r2 chronos 3 surgit}); without it the reading waits one tick, so a scroll that reads itself loops
 * instead of recursing, paying each round.
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
        if (mark.isEmpty()) {
            MarkSpells.tell(player, "Surgit num feitico precisa de uma marca antes: 'r2 surgit' le os pergaminhos marcados r2.");
            return;
        }
        int delay = Math.max(1, Chronos.window(action.get()));
        SpellEffects.schedule(player.serverLevel(), delay, () -> {
            if (!SpellEffects.isPlayerValid(player)) {
                return;
            }
            int read = ServerSpellingController.getInstance().readMarkedScrolls(player, mark.get());
            if (read == 0) {
                MarkSpells.tell(player, "Nenhum pergaminho '" + mark.get()
                        + "' ao alcance do toque (ou vinculado com 'vis ... ligabis " + mark.get() + "').");
            }
        });
    }
}
