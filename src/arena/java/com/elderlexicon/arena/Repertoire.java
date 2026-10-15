package com.elderlexicon.arena;

import com.elderlexicon.mod.illager.SorcererGrimoire;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spell.block.SpellBlock;
import com.elderlexicon.mod.mind.Act;
import com.elderlexicon.mod.mind.Aim;
import com.elderlexicon.mod.mind.Intent;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.mind.Personality;
import com.elderlexicon.mod.mind.PhraseCatalog;
import com.elderlexicon.mod.mind.Reach;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a caster in the arena can read: every sentence of the catalog it can read whole, as the mod's own mind weighs
 * them ({@link Mind#evaluate}). The arena chooses what for (a stance); this chooses the sentence, as the game does.
 * Weighed once for each situation in coarse steps: the same situation is met a great many times.
 */
final class Repertoire {

    private static final Aim[] AIMS = {Aim.TARGET, Aim.SKY, Aim.AWAY, Aim.FEET};

    private final Personality personality;
    private final Set<String> known;
    /** What it reads written farther (3.7), by sentence and the distance written: the same few are met again and again. */
    private final Map<String, Optional<Option>> farther = new HashMap<>();
    private final Map<Intent, List<Option>> byIntent = new EnumMap<>(Intent.class);
    private final List<Option> all;
    private final Map<String, List<Mind.Choice>> weighed = new HashMap<>();
    /**
     * How many of the best of each intent are kept for each situation, at least, and how many at most: every one worth
     * as much as the best, less {@code CLOSE}, is kept (the mind weighs many alike, and luck chooses among them).
     */
    private static final int KEPT = 12;
    private static final int MOST_KEPT = 80;
    private static final double CLOSE = 1.0D;
    /** How far what a caster learned moves an option's worth, at most (Memory.MOST). */
    private static final double MOST_LEARNED = 1.8D;

    Repertoire(Set<String> known, Personality personality) {
        this.personality = personality;
        this.known = Set.copyOf(known);
        PhraseCatalog catalog = PhraseCatalog.of(Lexicons.builtIn());
        // The pages of the grimoire it can read whole first, as the world gives them (CasterMind.handwritten), then
        // everything of the catalog it can read.
        List<Option> readable = new ArrayList<>(pages(known));
        catalog.readableBy(known).stream().map(Option::of).forEach(readable::add);
        all = Mind.worthWeighing(readable);
        for (Option option : all) {
            for (Act act : option.acts()) {
                for (Aim aim : AIMS) {
                    Intent intent = Mind.serves(act, aim);
                    if (intent != null) {
                        List<Option> list = byIntent.computeIfAbsent(intent, key -> new ArrayList<>());
                        if (list.isEmpty() || list.get(list.size() - 1) != option) {
                            list.add(option);
                        }
                    }
                }
            }
        }
    }

    /** The pages of the sorcerer's grimoire written by hand that one knowing {@code known} can read whole. */
    private static List<Option> pages(Set<String> known) {
        List<Option> pages = new ArrayList<>();
        for (SorcererGrimoire.Page page : SorcererGrimoire.get().pages()) {
            SpellBlock block = page.block();
            if (!known.containsAll(block.allRuneIds().stream().filter(Lexicons.get()::isRune).toList())) {
                continue;
            }
            List<Option.Line> lines = block.lines().stream()
                    .map(line -> new Option.Line(line.runeIds(), block.delaySteps(line))).toList();
            Option.page(page.name(), lines).ifPresent(pages::add);
        }
        return pages;
    }

    int size() {
        return all.size();
    }

    /** The best sentence for {@code intent} in the situation, if any serves it now and can be paid. */
    Optional<Mind.Choice> best(Intent intent, double distance, double health, double vis) {
        return best(intent, distance, health, vis, option -> 1.0D, null, 0.0D);
    }

    /**
     * The best sentence for {@code intent} in the situation, each one's worth multiplied by what the caster learned of
     * it ({@code learned}, as CasterMind.learnedAgainst: nothing for what it gave up on, less for what it read just
     * before).
     */
    Optional<Mind.Choice> best(Intent intent, double distance, double health, double vis,
                               java.util.function.ToDoubleFunction<Option> learned, java.util.Random random,
                               double luck) {
        Mind.Choice best = null;
        for (Mind.Choice choice : ranked(intent, distance, health, vis)) {
            if (best != null && choice.score() * MOST_LEARNED + luck <= best.score()) {
                break;
            }
            double factor = learned.applyAsDouble(choice.option());
            if (factor <= 0.0D) {
                continue;
            }
            // A little luck, as a degree weighs it (Mind.choose): among the many worth alike, it falls anywhere.
            double score = choice.score() * factor + (random == null ? 0.0D : random.nextDouble() * luck);
            if (best == null || score > best.score()) {
                best = new Mind.Choice(choice.option(), choice.intent(), score, choice.aim(), choice.harm(),
                        choice.healed());
            }
        }
        return Optional.ofNullable(best);
    }

    /** The best few sentences for {@code intent} in the situation, the best first. */
    List<Mind.Choice> ranked(Intent intent, double distance, double health, double vis) {
        String key = intent.name() + ":" + (int) Math.floor(distance * 2.0D) + ":" + (int) (vis / 5.0D) + ":"
                + (int) (health * 10.0D);
        return weighed.computeIfAbsent(key, ignored -> {
            Mind.Scene scene = scene(Math.floor(distance * 2.0D) / 2.0D + 0.25D, health, Math.floor(vis / 5.0D) * 5.0D);
            Mind.Foresight foresight = Mind.person(1.0D - health);
            List<Mind.Choice> found = new ArrayList<>();
            double at = scene.distance();
            for (Option option : byIntent.getOrDefault(intent, List.of())) {
                Mind.evaluate(scene, personality, option, foresight)
                        .filter(choice -> choice.intent() == intent).ifPresent(found::add);
                // What falls short, written farther, as one who knows how to write a distance does (CasterMind.farther).
                if ((intent == Intent.STRIKE || intent == Intent.CALL) && !Reach.gets(option, at)) {
                    Optional<Option> far = farther.computeIfAbsent(option.name() + "@" + (long) Reach.written(at),
                            ignored2 -> Reach.farther(option, at, known));
                    far.flatMap(written -> Mind.evaluate(scene, personality, written, foresight))
                            .filter(choice -> choice.intent() == intent).ifPresent(found::add);
                }
            }
            found.sort(java.util.Comparator.comparingDouble(Mind.Choice::score).reversed());
            int kept = Math.min(KEPT, found.size());
            while (kept < Math.min(MOST_KEPT, found.size())
                    && found.get(kept).score() >= found.get(0).score() - CLOSE) {
                kept++;
            }
            return List.copyOf(found.subList(0, kept));
        });
    }

    /** The best of everything, as the mind would read it with no doctrine (an elder: the very best, no luck). */
    Optional<Mind.Choice> bestOfAll(double distance, double health, double vis,
                                    java.util.function.ToDoubleFunction<Option> learned, java.util.Random random,
                                    double luck) {
        Mind.Choice best = null;
        for (Intent intent : Intent.values()) {
            Optional<Mind.Choice> choice = best(intent, distance, health, vis, learned, random, luck);
            if (choice.isPresent() && (best == null || choice.get().score() > best.score())) {
                best = choice.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Mind.Scene scene(double distance, double health, double vis) {
        // On open ground: air and earth around its hand, nothing burning, no band to call.
        return new Mind.Scene(null, null, distance, (float) health, vis, mark -> 0.0D, false,
                element -> element == VitaElement.AURA || element == VitaElement.FIRMO);
    }
}
