package com.elderlexicon.mod.magic.matter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The material table in force: the mod's own ({@value #RESOURCE}) with every extension an addon registered applied on
 * top, in order. An addon brings its substances with a file of the same shape:
 * {@code Materials.extend(table -> table.read(reader))}; an extension that breaks the table is refused at once.
 */
public final class Materials {

    /** Where the mod's own substances are kept. */
    public static final String RESOURCE = "/data/elderlexicon/lexicon/materials.json";

    private static final List<Consumer<MaterialTableBuilder>> EXTENSIONS = new CopyOnWriteArrayList<>();
    private static volatile MaterialTable current;

    private Materials() {
    }

    /** The table in force, built the first time it is asked for. */
    public static MaterialTable get() {
        MaterialTable found = current;
        if (found == null) {
            synchronized (Materials.class) {
                found = current;
                if (found == null) {
                    found = assemble();
                    current = found;
                }
            }
        }
        return found;
    }

    /** The mod's own substances, with nothing added. */
    public static MaterialTable builtIn() {
        return builtInBuilder().build();
    }

    /** Adds to or changes the table; an extension that breaks it is refused at once and leaves no trace. */
    public static void extend(Consumer<MaterialTableBuilder> extension) {
        EXTENSIONS.add(extension);
        synchronized (Materials.class) {
            try {
                current = assemble();
            } catch (RuntimeException broken) {
                EXTENSIONS.remove(extension);
                current = null;
                throw broken;
            }
        }
    }

    /** Forgets every extension (tests, and a world that unloads its addons' substances). */
    public static void reset() {
        synchronized (Materials.class) {
            EXTENSIONS.clear();
            current = null;
        }
    }

    private static MaterialTable assemble() {
        MaterialTableBuilder builder = builtInBuilder();
        for (Consumer<MaterialTableBuilder> extension : EXTENSIONS) {
            extension.accept(builder);
        }
        return builder.build();
    }

    private static MaterialTableBuilder builtInBuilder() {
        MaterialTableBuilder builder = MaterialTableBuilder.empty();
        InputStream stream = Materials.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("The mod's materials are missing: " + RESOURCE);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            builder.read(reader);
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException("The mod's materials could not be read: " + RESOURCE, unreadable);
        }
        return builder;
    }
}
