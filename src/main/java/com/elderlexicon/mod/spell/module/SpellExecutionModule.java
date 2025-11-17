package com.elderlexicon.mod.spell.module;

import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellModule;
import com.elderlexicon.mod.spell.function.ExsugatFunctionHandler;
import com.elderlexicon.mod.spell.function.IactareFunctionHandler;
import com.elderlexicon.mod.spell.function.SpellFunctionHandler;
import com.elderlexicon.mod.spell.function.VocantFunctionHandler;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.elderlexicon.mod.parser.Parser;

public final class SpellExecutionModule implements SpellModule {

    private static final Map<String, SpellFunctionHandler> HANDLERS = createHandlers();

    @Override
    public void apply(SpellContext context) {
        List<String> lexemes = context.lexemes();
        if (lexemes.isEmpty()) {
            return;
        }
        Parser parser = context.parser();
        if (parser == null) {
            return;
        }

        VitaElement currentElement = context.primarySource()
                .map(primary -> VitaElement.fromRuneId(primary.definition().id()))
                .orElse(context.primaryElement());

        for (String lexeme : lexemes) {
            ParserDictionary.RuneDefinition definition = parser.lookup(lexeme).orElse(null);
            if (definition == null) {
                continue;
            }
            switch (definition.type()) {
                case SOURCE -> currentElement = VitaElement.fromRuneId(definition.id());
                case FUNCTION -> executeFunction(definition.id(), context, currentElement);
                default -> {
                }
            }
        }
    }

    private void executeFunction(String runeId, SpellContext context, VitaElement element) {
        if (runeId == null) {
            return;
        }
        SpellFunctionHandler handler = HANDLERS.get(runeId.toLowerCase(Locale.ROOT));
        if (handler != null) {
            handler.execute(context, element);
        }
    }

    private static Map<String, SpellFunctionHandler> createHandlers() {
        Map<String, SpellFunctionHandler> map = new HashMap<>();
        map.put("iactare", new IactareFunctionHandler());
        map.put("vocant", new VocantFunctionHandler());
        map.put("exsugat", new ExsugatFunctionHandler());
        return Map.copyOf(map);
    }
}
