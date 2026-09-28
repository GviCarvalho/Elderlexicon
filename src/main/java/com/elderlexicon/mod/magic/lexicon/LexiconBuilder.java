package com.elderlexicon.mod.magic.lexicon;

import com.elderlexicon.mod.spell.mark.NumberGlyphs;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link Lexicon}: from the mod's data, and then from whatever an addon brings (more runes, other meanings for
 * the old ones, new fusions). Building checks that the language holds together: the default source exists, every
 * shorthand stands for words the language has, and no two runes share a glyph.
 */
public final class LexiconBuilder {

    /** How deep shorthands may stand for other shorthands before the spirit gives up. */
    public static final int MAX_EXPANSION_DEPTH = 8;
    /** In a fusion's parts, any source at all (the forms: {@code hasta} is iactare and a source). */
    public static final String ANY_SOURCE = "source";

    private final Map<String, Rune> runes = new LinkedHashMap<>();
    private String defaultSource;
    private final List<String> repertoire = new ArrayList<>();
    private final List<Meeting> meetings = new ArrayList<>();
    private final Map<String, String> notes = new LinkedHashMap<>();

    private LexiconBuilder() {
    }

    public static LexiconBuilder empty() {
        return new LexiconBuilder();
    }

    /** A builder holding everything {@code lexicon} has, to add to or change. */
    public static LexiconBuilder from(Lexicon lexicon) {
        LexiconBuilder builder = new LexiconBuilder();
        lexicon.runes().forEach(builder::rune);
        builder.defaultSource = lexicon.defaultSource() == null ? null : lexicon.defaultSource().id();
        builder.repertoire.addAll(lexicon.repertoire());
        builder.meetings.addAll(lexicon.meetings());
        builder.notes.putAll(lexicon.notes());
        return builder;
    }

    /** Adds a rune, or gives an existing one a new meaning. */
    public LexiconBuilder rune(Rune rune) {
        runes.put(rune.id(), rune);
        return this;
    }

    public LexiconBuilder remove(String id) {
        runes.remove(Rune.normalize(id));
        return this;
    }

    public LexiconBuilder defaultSource(String id) {
        this.defaultSource = Rune.normalize(id);
        return this;
    }

    public LexiconBuilder repertoire(List<String> runeIds) {
        repertoire.clear();
        if (runeIds != null) {
            runeIds.stream().map(Rune::normalize).forEach(repertoire::add);
        }
        return this;
    }

    public LexiconBuilder meeting(Meeting meeting) {
        if (meeting != null) {
            meetings.add(meeting);
        }
        return this;
    }

    public LexiconBuilder clearMeetings() {
        meetings.clear();
        return this;
    }

    public LexiconBuilder note(String key, String text) {
        if (key != null && text != null) {
            notes.put(key, text);
        }
        return this;
    }

    /** Reads a lexicon file into this builder (see {@link LexiconReader} for the format). */
    public LexiconBuilder read(Reader json) {
        LexiconReader.read(json, this);
        return this;
    }

    public boolean has(String id) {
        return runes.containsKey(Rune.normalize(id));
    }

    public Lexicon build() {
        List<String> problems = new ArrayList<>();
        Rune fallback = defaultSource == null ? null : runes.get(defaultSource);
        if (fallback == null || !fallback.is(WordClass.SOURCE)) {
            problems.add("the default source '" + defaultSource + "' is not a source of the lexicon");
        }
        for (String id : repertoire) {
            if (!runes.containsKey(id)) {
                problems.add("the repertoire names '" + id + "', which is no rune");
            }
        }
        Map<String, String> glyphs = new HashMap<>();
        for (Rune rune : runes.values()) {
            rune.glyph().ifPresent(glyph -> {
                if (NumberGlyphs.read(glyph).isPresent()) {
                    problems.add("rune '" + rune.id() + "' is written '" + glyph + "', which reads as a number");
                }
                String other = glyphs.putIfAbsent(glyph, rune.id());
                if (other != null) {
                    problems.add("runes '" + other + "' and '" + rune.id() + "' share the glyph '" + glyph + "'");
                }
            });
            for (String part : rune.components()) {
                if (!ANY_SOURCE.equals(part) && !runes.containsKey(part)) {
                    problems.add("fusion '" + rune.id() + "' is made of '" + part + "', which is no rune");
                }
            }
            for (String word : rune.expansion()) {
                if (!Rune.NEXT_WORD.equals(word) && !runes.containsKey(word)) {
                    problems.add("'" + rune.id() + "' stands for '" + word + "', which is no rune");
                }
            }
            if (rune.isShorthand() && expandsForever(rune.id(), new HashSet<>(), 0)) {
                problems.add("'" + rune.id() + "' stands for itself, through its own words");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The lexicon does not hold together: " + String.join("; ", problems));
        }
        return new Lexicon(runes, defaultSource, repertoire, meetings, notes);
    }

    private boolean expandsForever(String id, Set<String> path, int depth) {
        if (depth > MAX_EXPANSION_DEPTH || !path.add(id)) {
            return true;
        }
        Rune rune = runes.get(id);
        if (rune != null) {
            for (String word : rune.expansion()) {
                Rune inner = runes.get(word);
                if (inner != null && inner.isShorthand() && expandsForever(word, path, depth + 1)) {
                    return true;
                }
            }
        }
        path.remove(id);
        return false;
    }
}
