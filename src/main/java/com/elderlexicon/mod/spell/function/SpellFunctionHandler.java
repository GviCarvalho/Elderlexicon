package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.vita.VitaElement;

public interface SpellFunctionHandler {
    void execute(SpellContext context, VitaElement element);
}
