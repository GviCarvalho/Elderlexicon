package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a caster does with its grimoire out of a fight (docs/feiticeiro-design.md, 2.1): it writes. The heavy work of the
 * mind is done here, a little at a time, so that in a fight it only weighs the pages it has:
 * <ol>
 *   <li><b>Read the catalog</b> (a few hundred sentences a step): of the sentences it can read whole that serve one
 *       intent alone, it keeps the best few for each intent, weighed against a person's body, with no scene before it
 *       ({@link Mind#person}).</li>
 *   <li><b>Compose</b> pages the catalog has none of, out of the best sentences: a sentence alone, a <b>volley</b> (the
 *       same strike three times, half a second apart), <b>draw and strike</b> (draw out of the body, then strike) and
 *       <b>vanish and strike</b>.</li>
 *   <li><b>Weigh</b> each draft (one a step), in two scenes: whole, eight blocks from its target; wounded, five.</li>
 *   <li><b>Keep</b> the best page of each kind: its written grimoire.</li>
 * </ol>
 */
public final class Preparation {

    /** How many sentences of the catalog it reads a step. */
    public static final int READ_PER_STEP = 400;
    /** How many of the best sentences it keeps for each intent, to compose with. */
    private static final int BEST_PER_INTENT = 3;
    /** Steps between the lines of a volley, and between a draw (or a veil) and the strike after it. */
    private static final int VOLLEY_STEPS = 2;
    private static final int FOLLOW_STEPS = 3;
    /** The Vis it writes for: what a page may spend (its Anchor of the Hundred, with room to spare). */
    private static final double WRITING_VIS = 60.0D;
    private static final Mind.Scene WHOLE = new Mind.Scene(null, null, 8.0D, 1.0F, WRITING_VIS);
    private static final Mind.Scene WOUNDED = new Mind.Scene(null, null, 5.0D, 0.35F, WRITING_VIS);
    private static final Mind.Foresight UNHURT = Mind.person(0.0D);
    private static final Mind.Foresight HURT = Mind.person(0.3D);

    /** The kinds of page it writes. */
    public enum Kind {
        STRIKE("golpe"),
        DRAW("mordida"),
        VEIL("véu"),
        VOLLEY("salva"),
        DRAW_AND_STRIKE("beber e ferir"),
        VEIL_AND_STRIKE("véu e golpe");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        /** What a page of it is called, with the energy it works with ("salva de fogo"). */
        String name(VitaElement element) {
            return element == null || element == VitaElement.BALANCED ? name : name + " de " + noun(element);
        }
    }

    private final Personality personality;
    private final List<Phrase> readable;
    private int read;
    private final Map<Intent, List<Weighed>> best = new EnumMap<>(Intent.class);
    private final Deque<Draft> drafts = new ArrayDeque<>();
    private boolean composed;
    private final Map<Kind, Weighed> kept = new LinkedHashMap<>();

    private record Weighed(Option option, double score) {
    }

    private record Draft(Kind kind, Option option) {
    }

    /** A preparation of one who knows {@code known} and weighs as {@code personality}, out of {@code catalog}. */
    public Preparation(PhraseCatalog catalog, Set<String> known, Personality personality) {
        this.personality = personality;
        this.readable = catalog.readableBy(known);
    }

    /** Whether it has written all it would. */
    public boolean done() {
        return composed && drafts.isEmpty();
    }

    /** One step of the work: some sentences read, or the drafts composed, or one draft weighed. */
    public void step() {
        if (read < readable.size()) {
            int end = Math.min(readable.size(), read + READ_PER_STEP);
            for (; read < end; read++) {
                consider(Option.of(readable.get(read)));
            }
            return;
        }
        if (!composed) {
            compose();
            composed = true;
            return;
        }
        Draft draft = drafts.poll();
        if (draft != null) {
            double score = worth(draft.option());
            if (score > 0.0D) {
                kept.merge(draft.kind(), new Weighed(draft.option(), score),
                        (old, fresh) -> fresh.score() > old.score() ? fresh : old);
            }
        }
    }

    /** Works until it is done (for a test, or a command). */
    public Preparation finish() {
        while (!done()) {
            step();
        }
        return this;
    }

    /** The pages it wrote, best kind first. */
    public List<Option> pages() {
        return kept.values().stream().sorted(Comparator.comparingDouble(Weighed::score).reversed())
                .map(Weighed::option).toList();
    }

    // ------------------------------------------------------------------ the work

    /** A sentence of the catalog: kept among the best of the intent it serves most, if it is. */
    private void consider(Option option) {
        if (Mind.worthWeighing(List.of(option)).isEmpty()) {
            return;
        }
        // What it composes with serves one intent alone: the pages of several (draw and strike, vanish and strike) are
        // the ones it composes itself.
        if (option.acts().stream().map(Mind::serves).filter(java.util.Objects::nonNull).distinct().count() > 1) {
            return;
        }
        Optional<Mind.Choice> whole = Mind.evaluate(WHOLE, personality, option, UNHURT);
        Optional<Mind.Choice> wounded = Mind.evaluate(WOUNDED, personality, option, HURT);
        Mind.Choice choice = wounded.filter(found -> whole.isEmpty() || found.score() > whole.get().score())
                .orElse(whole.orElse(null));
        if (choice == null) {
            return;
        }
        List<Weighed> list = best.computeIfAbsent(choice.intent(), intent -> new ArrayList<>());
        list.add(new Weighed(option, choice.score()));
        list.sort(Comparator.comparingDouble(Weighed::score).reversed());
        if (list.size() > BEST_PER_INTENT) {
            list.remove(list.size() - 1);
        }
    }

    /** The drafts of every kind, out of the best sentences of each intent. */
    private void compose() {
        List<Option> strikes = options(Intent.STRIKE);
        List<Option> draws = options(Intent.DRAW);
        List<Option> veils = options(Intent.VEIL);
        strikes.forEach(strike -> draft(Kind.STRIKE, element(strike), line(strike, 0)));
        draws.forEach(draw -> draft(Kind.DRAW, element(draw), line(draw, 0)));
        veils.forEach(veil -> draft(Kind.VEIL, null, line(veil, 0)));
        for (Option strike : strikes) {
            draft(Kind.VOLLEY, element(strike), line(strike, 0), line(strike, VOLLEY_STEPS),
                    line(strike, 2 * VOLLEY_STEPS));
            for (Option draw : draws) {
                draft(Kind.DRAW_AND_STRIKE, element(draw), line(draw, 0), line(strike, FOLLOW_STEPS));
            }
            for (Option veil : veils) {
                draft(Kind.VEIL_AND_STRIKE, element(strike), line(veil, 0), line(strike, FOLLOW_STEPS + 1));
            }
        }
    }

    private List<Option> options(Intent intent) {
        return best.getOrDefault(intent, List.of()).stream().map(Weighed::option).toList();
    }

    private void draft(Kind kind, VitaElement element, Option.Line... lines) {
        Option.page(kind.name(element), List.of(lines)).ifPresent(page -> drafts.add(new Draft(kind, page)));
    }

    private static Option.Line line(Option option, int delaySteps) {
        return new Option.Line(option.lines().get(0).words(), delaySteps);
    }

    /** The energy an option works with: that of its first act that serves an intent. */
    private static VitaElement element(Option option) {
        return option.acts().stream().filter(act -> Mind.serves(act) != null).map(Act::element).findFirst()
                .orElse(null);
    }

    /** What a page is worth: the better of the two scenes it is written for. */
    private double worth(Option page) {
        double whole = Mind.evaluate(WHOLE, personality, page, UNHURT).map(Mind.Choice::score).orElse(0.0D);
        double wounded = Mind.evaluate(WOUNDED, personality, page, HURT).map(Mind.Choice::score).orElse(0.0D);
        return Math.max(whole, wounded);
    }

    private static String noun(VitaElement element) {
        return switch (element) {
            case IGNI -> "fogo";
            case AQUA -> "água";
            case FIRMO -> "terra";
            case AURA -> "ar";
            case BALANCED -> "vis";
        };
    }
}
