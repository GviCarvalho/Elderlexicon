package com.elderlexicon.mod.spelling.flow;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.ReferentSpec;
import com.elderlexicon.mod.spell.function.SceneMarks;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Binds the referent runes of a line to the scene of its casting (docs/fluxo-design.md), just before the spirit acts
 * on it: {@code ego} to the one who casts, {@code ille} to the other one of the most recent happening the line's
 * conditions speak of (of any happening, when the line has none), within the last second. Each becomes a mark of its
 * own ({@link SceneMarks}); an {@code ille} with no one in the scene is left as written, and finds nothing, as a mark
 * no one bears.
 */
public final class SceneWords {

    private SceneWords() {
    }

    public static List<String> bind(ServerPlayer player, List<String> words) {
        Lexicon lexicon = Lexicons.get();
        if (words.stream().noneMatch(lexicon::isReferent)) {
            return words;
        }
        Set<String> triggers = new LinkedHashSet<>();
        words.forEach(word -> lexicon.triggerOf(word).ifPresent(triggers::add));
        List<String> bound = new ArrayList<>(words.size());
        for (String word : words) {
            Optional<ReferentSpec.Refers> refers = lexicon.referentOf(word);
            if (refers.isEmpty()) {
                bound.add(word);
            } else if (refers.get() == ReferentSpec.Refers.CASTER) {
                bound.add(SceneMarks.entity(word, player));
            } else {
                bound.add(Happenings.recentOther(player, triggers).map(other -> mark(word, other)).orElse(word));
            }
        }
        return bound;
    }

    private static String mark(String word, Happenings.Other other) {
        if (other instanceof Happenings.OtherEntity entity) {
            return SceneMarks.entity(word, entity.entity());
        }
        if (other instanceof Happenings.OtherBlock block) {
            return SceneMarks.block(word, block.level(), block.pos());
        }
        Happenings.OtherPoint point = (Happenings.OtherPoint) other;
        return SceneMarks.point(word, point.level(), point.at());
    }
}
