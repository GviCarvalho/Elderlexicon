package com.elderlexicon.mod.magic.lexicon;

import com.elderlexicon.mod.spell.mark.NumberGlyphs;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link Lexicon}: from the mod's data, and then from whatever an addon brings (more runes, other meanings for
 * the old ones). Building checks that the language holds together: the default source exists and no two runes share a
 * glyph. No fused rune can be brought: they left the language (docs/particulas-design.md, stage 3).
 */
public final class LexiconBuilder {

    private final Map<String, Rune> runes = new LinkedHashMap<>();
    private String defaultSource;
    private final List<String> repertoire = new ArrayList<>();
    private final List<Meeting> meetings = new ArrayList<>();
    private final Map<String, String> notes = new LinkedHashMap<>();
    private final Map<String, String> retired = new LinkedHashMap<>();

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
        builder.retired.putAll(lexicon.retiredWords());
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

    /**
     * A word the language had and no longer has ({@code exsugat}), with what the spirit tells a mage who still writes it.
     * A rune of that name, if one is added again, is read as the rune.
     */
    public LexiconBuilder retire(String id, String note) {
        if (id != null && note != null) {
            retired.put(Rune.normalize(id), note);
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
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The lexicon does not hold together: " + String.join("; ", problems));
        }
        return new Lexicon(runes, defaultSource, repertoire, meetings, notes, retired);
    }
}
