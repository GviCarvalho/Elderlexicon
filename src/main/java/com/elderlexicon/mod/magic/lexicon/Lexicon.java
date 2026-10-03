package com.elderlexicon.mod.magic.lexicon;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The words of the Old Tongue: every rune the spirit understands and what each one is. A lexicon is immutable; it is
 * built by {@link LexiconBuilder} from the mod's own data ({@code data/elderlexicon/lexicon/runes.json}) and whatever
 * addons add, and {@link Lexicons} holds the one in force.
 * <p>
 * The language stays small (a handful of sources, verbs and filters) and everything else is composition: sources mix
 * aspects, and named runes stand for whole spells. There are no fused runes (docs/particulas-design.md, stage 3): what
 * two runes do together comes from their order and from the instant and place they meet in.
 */
public final class Lexicon {

    private final Map<String, Rune> runes;
    private final String defaultSource;
    private final List<String> repertoire;
    private final List<Meeting> meetings;
    private final Map<String, String> notes;
    private final Map<String, String> retired;

    Lexicon(Map<String, Rune> runes, String defaultSource, List<String> repertoire, List<Meeting> meetings,
            Map<String, String> notes, Map<String, String> retired) {
        this.runes = Collections.unmodifiableMap(new LinkedHashMap<>(runes));
        this.defaultSource = defaultSource;
        this.repertoire = List.copyOf(repertoire);
        this.meetings = List.copyOf(meetings);
        this.notes = Collections.unmodifiableMap(new LinkedHashMap<>(notes));
        this.retired = Collections.unmodifiableMap(new LinkedHashMap<>(retired));
    }

    // ------------------------------------------------------------------ runes

    public Optional<Rune> rune(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(runes.get(Rune.normalize(id)));
    }

    public boolean isRune(String word) {
        return rune(word).isPresent();
    }

    /** Every rune, in the order the lexicon declares them. */
    public Collection<Rune> runes() {
        return runes.values();
    }

    public Optional<Rune> ofClass(String id, WordClass wordClass) {
        return rune(id).filter(rune -> rune.is(wordClass));
    }

    public Optional<SourceSpec> source(String id) {
        return rune(id).flatMap(Rune::source);
    }

    public Optional<VerbSpec> verb(String id) {
        return rune(id).flatMap(Rune::verb);
    }

    public Optional<FilterSpec> filter(String id) {
        return rune(id).flatMap(Rune::filter);
    }

    /**
     * The source that fills a sentence that names none (book 4.2: "o espírito preenche essa lacuna com mana"): the
     * energy a verb spends when nothing else was written.
     */
    public Rune defaultSource() {
        return runes.get(defaultSource);
    }

    /** The runes a new mage is given to recite in trance. */
    public List<String> repertoire() {
        return repertoire;
    }

    public List<Meeting> meetings() {
        return meetings;
    }

    /** A text of the language itself (not of one rune), by key. */
    public Optional<String> note(String key) {
        return Optional.ofNullable(notes.get(key));
    }

    /** Every text of the language itself, by key. */
    public Map<String, String> notes() {
        return notes;
    }

    /**
     * What the spirit tells a mage who writes a word the language no longer has ({@code exsugat}), or empty for a word
     * that is a rune or never was one.
     */
    public Optional<String> retired(String word) {
        if (word == null || word.isBlank() || isRune(word)) {
            return Optional.empty();
        }
        return Optional.ofNullable(retired.get(Rune.normalize(word)));
    }

    /** Every retired word, with what the spirit says of it. */
    public Map<String, String> retiredWords() {
        return retired;
    }

    // ------------------------------------------------------------------ what the engine asks

    /** The element a source follows the laws of; anything that is not a source is mana, as the book fills the gap. */
    public VitaElement elementOf(String sourceId) {
        return source(sourceId).map(SourceSpec::element).orElse(VitaElement.BALANCED);
    }

    /** How a source shows itself; a word that is no source shows as nothing in particular. */
    public Traits traitsOf(String sourceId) {
        return source(sourceId).map(SourceSpec::traits).orElse(Traits.NONE);
    }

    /** What a verb does to the energy in flow; anything else spends it. */
    public Flow flowOf(String verbId) {
        return verb(verbId).map(VerbSpec::flow).orElse(Flow.SPEND);
    }

    /** The operation the world runs for a verb, or empty for a word that is no verb of the lexicon. */
    public Optional<String> operationOf(String verbId) {
        return verb(verbId).map(VerbSpec::operation);
    }

    /** A verb's base cost in UMU; 0 for anything else. */
    public double costOf(String verbId) {
        return verb(verbId).map(VerbSpec::cost).orElse(0.0D);
    }

    /** Every verb's base cost, by rune id. */
    public Map<String, Double> verbCosts() {
        Map<String, Double> costs = new LinkedHashMap<>();
        for (Rune rune : runes.values()) {
            rune.verb().filter(verb -> verb.cost() > 0.0D).ifPresent(verb -> costs.put(rune.id(), verb.cost()));
        }
        return costs;
    }

    /** The first rune whose verb has {@code operation}, the canonical word for it. */
    public Optional<Rune> verbFor(String operation) {
        if (operation == null) {
            return Optional.empty();
        }
        for (Rune rune : runes.values()) {
            if (rune.verb().map(verb -> operation.equals(verb.operation())).orElse(false)) {
                return Optional.of(rune);
            }
        }
        return Optional.empty();
    }

    /** Whether a word is a verb that binds ({@code ligabis}), reading its own aspect and marks. */
    public boolean isBinding(String word) {
        return verb(word).map(VerbSpec::binds).orElse(false);
    }

    /** Whether a word is a verb that names what was written before it ({@code reframe}). */
    public boolean isNaming(String word) {
        return verb(word).map(VerbSpec::names).orElse(false);
    }
}
