package com.elderlexicon.arena;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.mind.Act;
import com.elderlexicon.mod.mind.Aim;
import com.elderlexicon.mod.mind.Intent;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.mind.Personality;
import com.elderlexicon.mod.mind.PhraseCatalog;
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
    private final Map<Intent, List<Option>> byIntent = new EnumMap<>(Intent.class);
    private final List<Option> all;
    private final Map<String, Optional<Mind.Choice>> weighed = new HashMap<>();

    Repertoire(Set<String> known, Personality personality) {
        this.personality = personality;
        PhraseCatalog catalog = PhraseCatalog.of(Lexicons.builtIn());
        all = Mind.worthWeighing(catalog.readableBy(known).stream().map(Option::of).toList());
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

    int size() {
        return all.size();
    }

    /** The best sentence for {@code intent} in the situation, if any serves it now and can be paid. */
    Optional<Mind.Choice> best(Intent intent, double distance, double health, double vis) {
        String key = intent.name() + ":" + (int) Math.floor(distance * 2.0D) + ":" + (int) (vis / 5.0D) + ":"
                + (int) (health * 10.0D);
        return weighed.computeIfAbsent(key, ignored -> {
            Mind.Scene scene = scene(Math.floor(distance * 2.0D) / 2.0D + 0.25D, health, Math.floor(vis / 5.0D) * 5.0D);
            Mind.Foresight foresight = Mind.person(1.0D - health);
            Mind.Choice best = null;
            for (Option option : byIntent.getOrDefault(intent, List.of())) {
                Optional<Mind.Choice> choice = Mind.evaluate(scene, personality, option, foresight);
                if (choice.isPresent() && choice.get().intent() == intent
                        && (best == null || choice.get().score() > best.score())) {
                    best = choice.get();
                }
            }
            return Optional.ofNullable(best);
        });
    }

    /** The best of everything, as the mind would read it with no doctrine (an elder: the very best, no luck). */
    Optional<Mind.Choice> bestOfAll(double distance, double health, double vis) {
        Mind.Choice best = null;
        for (Intent intent : Intent.values()) {
            Optional<Mind.Choice> choice = best(intent, distance, health, vis);
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
