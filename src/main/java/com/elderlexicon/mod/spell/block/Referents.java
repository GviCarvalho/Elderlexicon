package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.WordClass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the grimoire speaks of a referent rune (docs/fluxo-design.md): it stands where a mark would, so the texts written
 * for marks ("o que tem a marca “m1”") are said with its noun instead ("o alvo", "o próprio conjurador").
 */
public final class Referents {

    private Referents() {
    }

    /** {@code text} with every "o que tem a marca “x”" of a referent {@code x} said as its noun. */
    public static String speak(Lexicon lexicon, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String spoken = text;
        for (Rune rune : lexicon.runes()) {
            if (!rune.is(WordClass.REFERENT)) {
                continue;
            }
            Matcher matcher = Pattern.compile("([oO]) que tem a marca “" + Pattern.quote(rune.id()) + "”")
                    .matcher(spoken);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String noun = rune.noun();
                if (matcher.group(1).equals("O") && !noun.isEmpty()) {
                    noun = Character.toUpperCase(noun.charAt(0)) + noun.substring(1);
                }
                matcher.appendReplacement(out, Matcher.quoteReplacement(noun));
            }
            matcher.appendTail(out);
            spoken = out.toString();
        }
        return spoken;
    }
}
