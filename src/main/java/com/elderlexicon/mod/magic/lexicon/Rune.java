package com.elderlexicon.mod.magic.lexicon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One word of the Old Tongue, as the lexicon describes it. Everything the mod knows about a rune is here: its class and
 * glyph, and what it is (a source's essence and traits, a verb's operation and frames, a filter's parameter). Every
 * rune is a word of its own: the fused runes left the language (docs/particulas-design.md, stage 3). Nothing in the
 * engine is written for one rune in particular.
 */
public final class Rune {

    private final String id;
    private final WordClass wordClass;
    private final String glyph;
    private final String translation;
    private final String name;
    private final String noun;
    private final String lore;
    private final SourceSpec source;
    private final VerbSpec verb;
    private final FilterSpec filter;
    private final Map<String, String> texts;

    private Rune(Builder builder) {
        this.id = normalize(Objects.requireNonNull(builder.id, "id"));
        this.wordClass = Objects.requireNonNull(builder.wordClass, "class of rune '" + id + "'");
        this.glyph = builder.glyph == null || builder.glyph.isBlank() ? null : builder.glyph.trim();
        this.translation = builder.translation == null ? id : builder.translation;
        this.name = builder.name == null ? this.translation : builder.name;
        this.noun = builder.noun == null ? this.name : builder.noun;
        this.lore = builder.lore == null ? "" : builder.lore;
        this.source = builder.source;
        this.verb = builder.verb;
        this.filter = builder.filter;
        this.texts = Collections.unmodifiableMap(new LinkedHashMap<>(builder.texts));
        switch (wordClass) {
            case SOURCE -> Objects.requireNonNull(source, "source rune '" + id + "' needs its essence");
            case VERB -> Objects.requireNonNull(verb, "verb '" + id + "' needs an operation");
            case FILTER -> Objects.requireNonNull(filter, "filter '" + id + "' needs a parameter");
        }
    }

    public String id() {
        return id;
    }

    public WordClass wordClass() {
        return wordClass;
    }

    public boolean is(WordClass kind) {
        return wordClass == kind;
    }

    /** The SGA letter(s) the rune is written with, if it has any. */
    public Optional<String> glyph() {
        return Optional.ofNullable(glyph);
    }

    /** The English gloss ({@code fire}, {@code summon}). */
    public String translation() {
        return translation;
    }

    /** What it is called in the mage's tongue ({@code fogo}, {@code mana}). */
    public String name() {
        return name;
    }

    /** The name with its article, as a sentence uses it ({@code o fogo}). */
    public String noun() {
        return noun;
    }

    /** One line about the rune, for the grimoire. */
    public String lore() {
        return lore;
    }

    public Optional<SourceSpec> source() {
        return Optional.ofNullable(source);
    }

    public Optional<VerbSpec> verb() {
        return Optional.ofNullable(verb);
    }

    public Optional<FilterSpec> filter() {
        return Optional.ofNullable(filter);
    }

    /** A text of this rune for the grimoire and the spirit's messages, by key. */
    public Optional<String> text(String key) {
        return Optional.ofNullable(texts.get(key));
    }

    public Map<String, String> texts() {
        return texts;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(id, wordClass)
                .glyph(glyph)
                .translation(translation)
                .name(name)
                .noun(noun)
                .lore(lore)
                .source(source)
                .verb(verb)
                .filter(filter);
        texts.forEach(builder::text);
        return builder;
    }

    public static Builder builder(String id, WordClass wordClass) {
        return new Builder(id, wordClass);
    }

    static String normalize(String word) {
        return word == null ? null : word.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "Rune{" + id + ", " + wordClass + "}";
    }

    /** Builds a rune: the lexicon reader uses it, and so can an addon that brings its own words. */
    public static final class Builder {
        private final String id;
        private final WordClass wordClass;
        private String glyph;
        private String translation;
        private String name;
        private String noun;
        private String lore;
        private SourceSpec source;
        private VerbSpec verb;
        private FilterSpec filter;
        private final Map<String, String> texts = new LinkedHashMap<>();

        private Builder(String id, WordClass wordClass) {
            this.id = id;
            this.wordClass = wordClass;
        }

        public Builder glyph(String glyph) {
            this.glyph = glyph;
            return this;
        }

        public Builder translation(String translation) {
            this.translation = translation;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder noun(String noun) {
            this.noun = noun;
            return this;
        }

        public Builder lore(String lore) {
            this.lore = lore;
            return this;
        }

        public Builder source(SourceSpec source) {
            this.source = source;
            return this;
        }

        public Builder verb(VerbSpec verb) {
            this.verb = verb;
            return this;
        }

        public Builder filter(FilterSpec filter) {
            this.filter = filter;
            return this;
        }

        public Builder text(String key, String value) {
            if (key != null && value != null) {
                this.texts.put(key, value);
            }
            return this;
        }

        public Rune build() {
            return new Rune(this);
        }
    }
}
