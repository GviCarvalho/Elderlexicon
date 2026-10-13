package com.elderlexicon.mod.spelling.flow;

import net.minecraft.world.entity.LivingEntity;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.ReferentSpec;
import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.spell.function.SceneMarks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    public static List<String> bind(LivingEntity player, List<String> words) {
        Lexicon lexicon = Lexicons.get();
        if (words.stream().noneMatch(lexicon::isReferent) || bonds(words)) {
            // A bond keeps its marks for good: it writes them itself, as lasting marks (see lasting).
            return words;
        }
        Set<String> triggers = com.elderlexicon.mod.spell.block.LineConditions.triggers(lexicon, words);
        List<String> bound = new ArrayList<>(words.size());
        for (String word : words) {
            Optional<ReferentSpec.Refers> refers = lexicon.referentOf(word);
            if (refers.isEmpty()) {
                bound.add(word);
            } else if (refers.get() == ReferentSpec.Refers.CASTER) {
                bound.add(SceneMarks.entity(word, player));
            } else {
                // What happened to a caster is only kept for players: a creature's ille has no one.
                bound.add((player instanceof ServerPlayer mage ? Happenings.recentOther(mage, triggers)
                        : Optional.<Happenings.Other>empty()).map(other -> mark(word, other)).orElse(word));
            }
        }
        return bound;
    }

    /**
     * Whether the line makes a lasting bond ({@code firmo ego ligabis ille}): it has a bond verb and no sense verb
     * before it (a bond of a sense, {@code surgit ille ligabis}, reads its thing once, as any spell).
     */
    public static boolean bonds(List<String> words) {
        Lexicon lexicon = Lexicons.get();
        for (String word : words) {
            if (lexicon.verb(word).map(verb -> verb.sense() != null).orElse(false)) {
                return false;
            }
            if (lexicon.isBinding(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A bond's words with each {@code ego} and {@code ille} written as the lasting mark of what it points at: the mark
     * the thing already bears, or a new one given to it ({@code ego_gui}, {@code ille_3f2a1c}).
     *
     * @param words      the words, ready for the bond's own grammar
     * @param sceneMarks the marks that came from the scene: what bears them is marked already
     * @param error      why the bond cannot be made, or null
     */
    public record Lasting(List<String> words, Set<String> sceneMarks, @Nullable String error) {
    }

    public static Lasting lasting(ServerPlayer player, List<String> words) {
        Lexicon lexicon = Lexicons.get();
        Set<String> triggers = com.elderlexicon.mod.spell.block.LineConditions.triggers(lexicon, words);
        List<String> written = new ArrayList<>(words.size());
        Set<String> scene = new LinkedHashSet<>();
        for (String word : words) {
            Optional<ReferentSpec.Refers> refers = lexicon.referentOf(word);
            if (refers.isEmpty()) {
                written.add(word);
                continue;
            }
            String mark;
            if (refers.get() == ReferentSpec.Refers.CASTER) {
                mark = lastingMark(player, word + "_" + player.getGameProfile().getName());
            } else {
                Optional<Happenings.Other> other = Happenings.recentOther(player, triggers);
                if (other.isEmpty()) {
                    return new Lasting(words, Set.of(), "O " + word + " nao aponta para ninguem agora.");
                }
                if (other.get() instanceof Happenings.OtherEntity entity) {
                    mark = lastingMark(entity.entity(), word + "_" + entity.entity().getUUID().toString().substring(0, 6));
                } else if (other.get() instanceof Happenings.OtherBlock block) {
                    mark = lastingMark(block.level(), block.pos(),
                            word + "_" + Long.toHexString(block.pos().asLong() & 0xFFFFFFL));
                } else {
                    return new Lasting(words, Set.of(), "O " + word + " e so um lugar (uma criatura morta, um bloco "
                            + "quebrado): nao ha o que ligar.");
                }
            }
            if (mark == null) {
                return new Lasting(words, Set.of(), "Nao foi possivel marcar o que o " + word + " aponta.");
            }
            written.add(mark);
            scene.add(mark);
        }
        return new Lasting(written, scene, null);
    }

    /** The mark an entity bears, or {@code fresh} given to it. */
    @Nullable
    private static String lastingMark(Entity entity, String fresh) {
        Optional<String> borne = MarkHelper.markForEntity(entity);
        if (borne.isPresent()) {
            return borne.get();
        }
        String mark = clean(fresh);
        return MarkHelper.applyMark(entity, mark) ? mark : null;
    }

    /** The mark a block bears, or {@code fresh} given to it. */
    @Nullable
    private static String lastingMark(ServerLevel level, BlockPos pos, String fresh) {
        LigabisManager manager = LigabisManager.get();
        String borne = manager == null ? null : manager.markedBlocks(level.dimension()).get(pos);
        if (borne == null) {
            borne = MarkHelper.markForBlock(level, pos).orElse(null);
        }
        if (borne != null) {
            return borne;
        }
        String mark = clean(fresh);
        return MarkHelper.applyMark(level, pos, mark) ? mark : null;
    }

    /** A new mark as marks are written: lower case, only letters, digits and _. */
    private static String clean(String fresh) {
        return fresh.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_]", "");
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
