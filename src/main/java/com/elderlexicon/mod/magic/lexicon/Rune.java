package com.elderlexicon.mod.magic.lexicon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One word of the Old Tongue, as the lexicon describes it. Everything the mod knows about a rune is here: its class and
 * glyph, what it is (a source's essence and traits, a verb's operation and frames, a filter's parameter), its fusion
 * parts and, for a fusion that is only a shorthand, the words it stands for. Nothing in the engine is written for one
 * rune in particular.
 */
public final class Rune {

    /** In an {@link #expansion()}, the word written right after the fusion goes here ({@code transiectio aqua}). */
    public static final String NEXT_WORD = "@";

    private final String id;
    private final WordClass wordClass;
    private final Origin origin;
    private final String glyph;
    private final String translation;
    private final String name;
    private final String noun;
    private final String lore;
    private final List<String> components;
    private final List<String> expansion;
    private final SourceSpec source;
    private final VerbSpec verb;
    private final FilterSpec filter;
    private final String form;
    private final Map<String, String> texts;

    private Rune(Builder builder) {
        this.id = normalize(Objects.requireNonNull(builder.id, "id"));
        this.wordClass = Objects.requireNonNull(builder.wordClass, "class of rune '" + id + "'");
        this.origin = builder.origin == null ? Origin.PRIMORDIAL : builder.origin;
        this.glyph = builder.glyph == null || builder.glyph.isBlank() ? null : builder.glyph.trim();
        this.translation = builder.translation == null ? id : builder.translation;
        this.name = builder.name == null ? this.translation : builder.name;
        this.noun = builder.noun == null ? this.name : builder.noun;
        this.lore = builder.lore == null ? "" : builder.lore;
        this.components = List.copyOf(builder.components.stream().map(Rune::normalize).toList());
        this.expansion = List.copyOf(builder.expansion.stream().map(Rune::normalize).toList());
        this.source = builder.source;
        this.verb = builder.verb;
        this.filter = builder.filter;
        this.form = builder.form == null ? this.translation : builder.form;
        this.texts = Collections.unmodifiableMap(new LinkedHashMap<>(builder.texts));
        switch (wordClass) {
            case SOURCE -> Objects.requireNonNull(source, "source rune '" + id + "' needs its essence");
            case VERB -> {
                if (verb == null && expansion.isEmpty()) {
                    throw new IllegalStateException("verb '" + id + "' needs an operation or an expansion");
                }
            }
            case FILTER -> Objects.requireNonNull(filter, "filter '" + id + "' needs a parameter");
            default -> {
            }
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

    public Origin origin() {
        return origin;
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

    /** The runes it is a fusion of ({@code igni}, {@code firmo} for {@code fusus}); empty for a primordial rune. */
    public List<String> components() {
        return components;
    }

    /** The words this rune stands for when it is only a shorthand of them; empty when it is a word of its own. */
    public List<String> expansion() {
        return expansion;
    }

    public boolean isShorthand() {
        return !expansion.isEmpty();
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

    /** The shape a form gives ({@code spear}). */
    public String form() {
        return form;
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
                .origin(origin)
                .glyph(glyph)
                .translation(translation)
                .name(name)
                .noun(noun)
                .lore(lore)
                .components(components)
                .expansion(expansion)
                .source(source)
                .verb(verb)
                .filter(filter)
                .form(form);
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
        private Origin origin;
        private String glyph;
        private String translation;
        private String name;
        private String noun;
        private String lore;
        private final List<String> components = new ArrayList<>();
        private final List<String> expansion = new ArrayList<>();
        private SourceSpec source;
        private VerbSpec verb;
        private FilterSpec filter;
        private String form;
        private final Map<String, String> texts = new LinkedHashMap<>();

        private Builder(String id, WordClass wordClass) {
            this.id = id;
            this.wordClass = wordClass;
        }

        public Builder origin(Origin origin) {
            this.origin = origin;
            return this;
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

        public Builder components(List<String> parts) {
            this.components.clear();
            if (parts != null) {
                this.components.addAll(parts);
            }
            return this;
        }

        public Builder expansion(List<String> words) {
            this.expansion.clear();
            if (words != null) {
                this.expansion.addAll(words);
            }
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

        public Builder form(String form) {
            this.form = form;
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
