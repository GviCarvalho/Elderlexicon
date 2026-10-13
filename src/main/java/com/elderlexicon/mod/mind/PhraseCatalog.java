package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.magic.grammar.SpellGrammar;
import com.elderlexicon.mod.magic.lexicon.FilterSpec;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.ObjectFrame;
import com.elderlexicon.mod.magic.lexicon.Parameter;
import com.elderlexicon.mod.magic.lexicon.ReferentSpec;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.VerbSpec;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.action.SpellCostProcessor;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Every sentence the mind knows what it does (docs/feiticeiro-design.md, 3.2). It is made from the lexicon alone, rune by
 * what each rune is, never by its name, so a rune an addon brings joins it with no code:
 * <ol>
 *   <li><b>Write:</b> short sentences in the shapes the grammar reads: a subject (a source, the caster or none), where
 *       the source comes from, a filter with a number, one verb or two, and what each verb takes after it.</li>
 *   <li><b>Read:</b> the grammar reads each one; a sentence it reads with any issue is dropped.</li>
 *   <li><b>Imagine:</b> the flow runs it in a world that only writes down what each verb would do ({@link
 *       ImaginedFlow}); what it does is its signature ({@link Act}).</li>
 *   <li><b>Keep:</b> one sentence for each signature, the cheapest (the shortest when they cost the same).</li>
 * </ol>
 * The catalog depends on the lexicon alone, so it is made once and kept. Who reads from it keeps only the sentences it
 * can read whole ({@link #readableBy}).
 * <p>
 * The other one of a happening ({@code ille}), conditions, naming and bonds are left out: they need a scene, a name
 * or marks the mind has not got.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.elderlexicon.mod.ElderLexicon.MODID)
public final class PhraseCatalog {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** The quantities a sentence is written with: the verb's own ten, twice it, and turned around. */
    static final List<String> QUANTITIES = List.of("10", "20", "-10");
    /** The times: at once (condensed) and a few seconds. */
    static final List<String> TIMES = List.of("0", "6");
    /** How much of something is seen, for a verb that measures it: nothing, all. */
    static final List<String> MEASURES = List.of("0", "10");

    private static volatile PhraseCatalog builtIn;

    private final List<Phrase> phrases;

    PhraseCatalog(List<Phrase> phrases) {
        this.phrases = List.copyOf(phrases);
    }

    /** The catalog of the lexicon in use, made the first time it is asked for. */
    public static PhraseCatalog get() {
        PhraseCatalog found = builtIn;
        if (found == null) {
            synchronized (PhraseCatalog.class) {
                found = builtIn;
                if (found == null) {
                    long start = System.nanoTime();
                    found = of(Lexicons.get());
                    builtIn = found;
                    LOGGER.info("Phrase catalog: {} sentences known in {} ms", found.phrases.size(),
                            (System.nanoTime() - start) / 1_000_000L);
                }
            }
        }
        return found;
    }

    /**
     * Makes the catalog away from the server's thread when the server starts (it takes a few seconds), so the first
     * mind that asks for it does not wait. One that asks before it is ready waits for it.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        Thread maker = new Thread(() -> {
            try {
                get();
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to make the phrase catalog", exception);
            }
        }, "Elder Lexicon phrase catalog");
        maker.setDaemon(true);
        maker.start();
    }

    /** The catalog, if it is made already: asking does not wait for it. */
    public static java.util.Optional<PhraseCatalog> ready() {
        return java.util.Optional.ofNullable(builtIn);
    }

    /** Forgets the catalog, for a lexicon that changed. */
    public static void forget() {
        builtIn = null;
    }

    /** The catalog of {@code lexicon}. */
    public static PhraseCatalog of(Lexicon lexicon) {
        SpellGrammar grammar = new SpellGrammar(lexicon);
        SpellCostProcessor costs = new SpellCostProcessor();
        Map<String, Phrase> bySignature = new LinkedHashMap<>();
        for (List<String> words : write(lexicon)) {
            SpellActionResult read = grammar.read(words);
            if (read.actions().isEmpty() || read.hasIssues()) {
                continue;
            }
            double cost = SpellCostProcessor.requiresEnergy(read.actions()) ? costs.computeTotalCost(read.actions())
                    : 0.0D;
            List<Act> acts;
            try {
                acts = ImaginedFlow.imagine(lexicon, read, cost);
            } catch (RuntimeException exception) {
                continue; // a sentence the flow cannot run is no sentence to keep
            }
            if (acts.isEmpty()) {
                continue;
            }
            Phrase phrase = new Phrase(words, words.stream().filter(lexicon::isRune).collect(Collectors.toSet()),
                    cost, acts);
            bySignature.merge(phrase.signature(), phrase, PhraseCatalog::better);
        }
        List<Phrase> kept = new ArrayList<>(bySignature.values());
        kept.sort(Comparator.comparingDouble(Phrase::cost).thenComparingInt(phrase -> phrase.words().size()));
        return new PhraseCatalog(kept);
    }

    private static Phrase better(Phrase one, Phrase other) {
        if (Math.abs(one.cost() - other.cost()) > 1.0E-6D) {
            return one.cost() < other.cost() ? one : other;
        }
        return one.words().size() <= other.words().size() ? one : other;
    }

    /**
     * What one sentence does, read and imagined as the catalog does it; empty when the grammar reads it with an issue or
     * it does nothing (a line of a page written by hand, for one).
     */
    public static java.util.Optional<Phrase> imagine(List<String> words) {
        Lexicon lexicon = Lexicons.get();
        SpellActionResult read = new SpellGrammar(lexicon).read(words);
        if (read.actions().isEmpty() || read.hasIssues()) {
            return java.util.Optional.empty();
        }
        double cost = SpellCostProcessor.requiresEnergy(read.actions())
                ? new SpellCostProcessor().computeTotalCost(read.actions()) : 0.0D;
        try {
            List<Act> acts = ImaginedFlow.imagine(lexicon, read, cost);
            return acts.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(new Phrase(words,
                    words.stream().filter(lexicon::isRune).collect(Collectors.toSet()), cost, acts));
        } catch (RuntimeException exception) {
            return java.util.Optional.empty();
        }
    }

    public List<Phrase> phrases() {
        return phrases;
    }

    /** The sentences one who knows {@code known} can read whole. */
    public List<Phrase> readableBy(Set<String> known) {
        return phrases.stream().filter(phrase -> phrase.readableBy(known)).toList();
    }

    /** The sentences with an act that {@code wanted} accepts. */
    public List<Phrase> doing(Predicate<Act> wanted) {
        return phrases.stream().filter(phrase -> phrase.acts().stream().anyMatch(wanted)).toList();
    }

    // ------------------------------------------------------------------ writing

    /** Every sentence in the shapes the catalog tries, written from what each rune of {@code lexicon} is. */
    static List<List<String>> write(Lexicon lexicon) {
        Words words = Words.of(lexicon);
        List<List<String>> sentences = new ArrayList<>();
        List<List<String>> subjects = new ArrayList<>();
        subjects.add(List.of());
        words.sources.forEach(source -> subjects.add(List.of(source)));
        words.casters.forEach(caster -> subjects.add(List.of(caster)));
        for (List<String> subject : subjects) {
            boolean source = !subject.isEmpty() && words.sources.contains(subject.get(0));
            List<List<String>> origins = new ArrayList<>();
            origins.add(List.of());
            if (source) {
                words.origins.forEach(origin -> origins.add(List.of(origin)));
            }
            for (List<String> origin : origins) {
                for (List<String> first : words.verbPhrases(true)) {
                    sentences.add(join(subject, origin, first));
                    for (List<String> second : words.verbPhrases(false)) {
                        sentences.add(join(subject, origin, first, second));
                    }
                }
            }
        }
        return sentences;
    }

    @SafeVarargs
    private static List<String> join(List<String>... parts) {
        List<String> joined = new ArrayList<>();
        for (List<String> part : parts) {
            joined.addAll(part);
        }
        return joined;
    }

    /** The words of each kind the catalog writes with, as the lexicon gives them. */
    private record Words(List<String> sources, List<String> casters, List<String> origins, List<String> quantities,
                         List<String> times, Map<String, VerbSpec> verbs) {

        static Words of(Lexicon lexicon) {
            Collection<Rune> runes = lexicon.runes();
            List<String> sources = ids(runes, rune -> rune.is(WordClass.SOURCE));
            List<String> casters = ids(runes, rune -> rune.is(WordClass.REFERENT) && rune.referent()
                    .map(spec -> spec.refers() == ReferentSpec.Refers.CASTER).orElse(false));
            List<String> origins = filters(runes, Parameter.ORIGIN);
            List<String> quantities = filters(runes, Parameter.QUANTITY);
            List<String> times = filters(runes, Parameter.TIME);
            Map<String, VerbSpec> verbs = new LinkedHashMap<>();
            for (Rune rune : runes) {
                rune.verb().filter(verb -> !verb.binds() && !verb.names()).ifPresent(verb -> verbs.put(rune.id(), verb));
            }
            return new Words(sources, casters, origins, quantities, times, verbs);
        }

        private static List<String> ids(Collection<Rune> runes, Predicate<Rune> keep) {
            return runes.stream().filter(keep).map(Rune::id).toList();
        }

        private static List<String> filters(Collection<Rune> runes, Parameter parameter) {
            return ids(runes, rune -> rune.filter().map(FilterSpec::parameter).filter(parameter::equals).isPresent());
        }

        /**
         * A verb as written: a filter with its number before it (or none), the verb, and what it takes after it.
         * The first verb of a sentence may take any filter; the second, a quantity or none.
         */
        List<List<String>> verbPhrases(boolean first) {
            List<List<String>> filtered = new ArrayList<>();
            filtered.add(List.of());
            for (String quantity : quantities) {
                QUANTITIES.forEach(number -> filtered.add(List.of(quantity, number)));
            }
            if (first) {
                for (String time : times) {
                    TIMES.forEach(number -> filtered.add(List.of(time, number)));
                }
            }
            List<List<String>> phrases = new ArrayList<>();
            for (Map.Entry<String, VerbSpec> verb : verbs.entrySet()) {
                for (List<String> object : objects(verb.getValue())) {
                    for (List<String> filter : filtered) {
                        phrases.add(join(filter, List.of(verb.getKey()), object));
                    }
                }
            }
            return phrases;
        }

        /** What a verb may take after it: nothing, a source, the caster (with a measure when the verb takes one). */
        private List<List<String>> objects(VerbSpec verb) {
            List<List<String>> objects = new ArrayList<>();
            ObjectFrame frame = verb.object();
            if (frame == null || !frame.awaitsTarget() || frame.optionalWithSubject()) {
                objects.add(List.of());
            }
            if (frame == null) {
                return objects;
            }
            if (frame.takes(ObjectFrame.Kind.SOURCE)) {
                sources.forEach(source -> objects.add(List.of(source)));
            }
            if (frame.takes(ObjectFrame.Kind.MARK)) {
                for (String caster : casters) {
                    objects.add(List.of(caster));
                    if (frame.measure() != null) {
                        for (String quantity : quantities) {
                            MEASURES.forEach(number -> objects.add(List.of(caster, quantity, number)));
                        }
                    }
                }
            }
            return objects;
        }
    }
}
