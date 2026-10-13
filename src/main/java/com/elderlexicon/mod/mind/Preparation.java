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
 *       <b>vanish and strike</b>; and to keep itself whole (docs/feiticeiro-design.md, 3.8): a <b>shield</b>, a
 *       <b>flight</b>, <b>putting out the fire</b>, and the <b>cure</b>, every energy it knows drawn out of the world one
 *       after another (the life follows the one most lacking, so one alone heals only what took that one).</li>
 *   <li><b>Weigh</b> each draft (one a step), in three scenes: whole, eight blocks from its target; wounded, five;
 *       pressed, burning, with the foe at hand.</li>
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
    private static final Mind.Scene PRESSED = new Mind.Scene(null, null, 2.5D, 0.35F, WRITING_VIS, mark -> 0.0D,
            true, element -> true);
    private static final Mind.Foresight UNHURT = Mind.person(0.0D);
    private static final Mind.Foresight HURT = Mind.person(0.3D);

    /** The kinds of page it writes. */
    public enum Kind {
        STRIKE("golpe"),
        DRAW("mordida"),
        VEIL("véu"),
        VOLLEY("salva"),
        DRAW_AND_STRIKE("beber e ferir"),
        VEIL_AND_STRIKE("véu e golpe"),
        WARD("escudo"),
        FLEE("fuga"),
        DOUSE("apagar o fogo"),
        HEAL("cura");

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
    private final Map<String, Weighed> kept = new LinkedHashMap<>();
    /** The cheapest sentence that draws each energy in, out of the world: what the cure is written with. */
    private final Map<VitaElement, Option> healers = new EnumMap<>(VitaElement.class);

    private record Weighed(Option option, double score) {
    }

    private record Draft(Kind kind, String key, Option option) {
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
            if (score <= 0.0D && draft.kind() == Kind.HEAL) {
                // One energy alone heals only what took it (a burn, a fall): it is worth keeping for then.
                score = 0.01D;
            }
            if (score > 0.0D) {
                kept.merge(draft.key(), new Weighed(draft.option(), score),
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
        healer(option);
        Mind.Choice choice = null;
        for (Optional<Mind.Choice> found : List.of(Mind.evaluate(WHOLE, personality, option, UNHURT),
                Mind.evaluate(WOUNDED, personality, option, HURT), Mind.evaluate(PRESSED, personality, option, HURT))) {
            if (found.isPresent() && (choice == null || found.get().score() > choice.score())) {
                choice = found.get();
            }
        }
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

    /** A one-line sentence that only draws one energy in (healing, looking at no body): the cheapest of each energy. */
    private void healer(Option option) {
        if (option.lines().size() != 1 || option.acts().size() != 1) {
            return;
        }
        Act act = option.acts().get(0);
        if (Mind.serves(act, Aim.SKY) != Intent.HEAL) {
            return;
        }
        healers.merge(act.element(), option, (old, fresh) -> fresh.cost() < old.cost() ? fresh : old);
    }

    /** The drafts of every kind, out of the best sentences of each intent. */
    private void compose() {
        List<Option> strikes = options(Intent.STRIKE);
        List<Option> draws = options(Intent.DRAW);
        List<Option> veils = options(Intent.VEIL);
        options(Intent.WARD).forEach(ward -> draft(Kind.WARD, null, line(ward, 0)));
        options(Intent.FLEE).forEach(flee -> draft(Kind.FLEE, null, line(flee, 0)));
        options(Intent.DOUSE).forEach(douse -> draft(Kind.DOUSE, null, line(douse, 0)));
        // The cure: each energy it can draw in, one a step; and each alone, for what took only that one.
        List<Option.Line> cure = new ArrayList<>();
        for (Map.Entry<VitaElement, Option> healer : healers.entrySet()) {
            cure.add(line(healer.getValue(), cure.size()));
            draftAs(Kind.HEAL, "HEAL:" + healer.getKey().name(), Kind.HEAL.name(healer.getKey()),
                    line(healer.getValue(), 0));
        }
        if (cure.size() > 1) {
            draftAs(Kind.HEAL, "HEAL", Kind.HEAL.name(null), cure.toArray(new Option.Line[0]));
        }
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
        draftAs(kind, kind.name(), kind.name(element), lines);
    }

    /** A draft kept under {@code key} (the best of each key is kept), named {@code name}. */
    private void draftAs(Kind kind, String key, String name, Option.Line... lines) {
        Option.page(name, List.of(lines)).ifPresent(page -> drafts.add(new Draft(kind, key, page)));
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
        double pressed = Mind.evaluate(PRESSED, personality, page, HURT).map(Mind.Choice::score).orElse(0.0D);
        return Math.max(whole, Math.max(wounded, pressed));
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
