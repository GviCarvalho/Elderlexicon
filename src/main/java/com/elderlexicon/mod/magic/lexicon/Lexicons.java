package com.elderlexicon.mod.magic.lexicon;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The lexicon in force. It is the mod's own ({@value #RESOURCE}) with every extension an addon registered applied on
 * top, in the order they were registered:
 * <pre>
 *   Lexicons.extend(words -> words
 *       .rune(Rune.builder("glacies", WordClass.SOURCE)
 *           .source(new SourceSpec(null, Map.of(VitaElement.AQUA, 0.7, VitaElement.FIRMO, 0.3), null, traits))
 *           .build()));
 * </pre>
 * or with a data file of the same shape as the mod's: {@code Lexicons.extend(words -> words.read(reader))}.
 */
public final class Lexicons {

    /** Where the mod's own words are kept. */
    public static final String RESOURCE = "/data/elderlexicon/lexicon/runes.json";

    private static final List<Consumer<LexiconBuilder>> EXTENSIONS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile Lexicon current;

    private Lexicons() {
    }

    /** The lexicon in force, built the first time it is asked for. */
    public static Lexicon get() {
        Lexicon found = current;
        if (found == null) {
            synchronized (Lexicons.class) {
                found = current;
                if (found == null) {
                    found = assemble();
                    current = found;
                }
            }
        }
        return found;
    }

    /** The mod's own words, with nothing added. */
    public static Lexicon builtIn() {
        return builtInBuilder().build();
    }

    /**
     * Adds to or changes the language: the extension receives a builder holding every word so far. The lexicon is built
     * again the next time it is asked for, and an extension that breaks it is refused at once.
     */
    public static void extend(Consumer<LexiconBuilder> extension) {
        EXTENSIONS.add(extension);
        synchronized (Lexicons.class) {
            try {
                current = assemble();
            } catch (RuntimeException broken) {
                EXTENSIONS.remove(extension);
                current = null;
                throw broken;
            }
            GENERATION.incrementAndGet();
        }
    }

    /** Forgets every extension (tests, and a world that unloads its addons' words). */
    public static void reset() {
        synchronized (Lexicons.class) {
            EXTENSIONS.clear();
            current = null;
            GENERATION.incrementAndGet();
        }
    }

    /** Changes every time the lexicon does, so what was worked out from an older one can be worked out again. */
    public static int generation() {
        return GENERATION.get();
    }

    private static Lexicon assemble() {
        LexiconBuilder builder = builtInBuilder();
        for (Consumer<LexiconBuilder> extension : EXTENSIONS) {
            extension.accept(builder);
        }
        return builder.build();
    }

    private static LexiconBuilder builtInBuilder() {
        LexiconBuilder builder = LexiconBuilder.empty();
        InputStream stream = Lexicons.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("The lexicon '" + RESOURCE + "' is not on the classpath.");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return builder.read(reader);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("The lexicon '" + RESOURCE + "' could not be read", exception);
        }
    }
}
