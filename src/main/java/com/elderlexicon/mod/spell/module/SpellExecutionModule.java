package com.elderlexicon.mod.spell.module;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellModule;

/**
 * Legacy alias kept only to guard against accidental re-wiring onto the old lexeme pipeline.
 */
@Deprecated(forRemoval = true, since = "Sprint4")
public final class SpellExecutionModule implements SpellModule {

    @Override
    public void apply(SpellContext context) {
        throw new UnsupportedOperationException("SpellExecutionModule foi removido. Utilize SpellActionExecutor via SpellActionEngine.");
    }
}
