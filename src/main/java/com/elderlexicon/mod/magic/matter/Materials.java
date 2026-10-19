package com.elderlexicon.mod.magic.matter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * The material table in force: the mod's own ({@value #RESOURCE}) with every extension an addon registered applied on
 * top, in order. An addon brings its substances and beings with a file of the same shape:
 * {@code Materials.extend(table -> table.read(reader))}; an extension that breaks the table is refused at once.
 */
public final class Materials {

    /** Where the mod's own substances and beings are kept. */
    public static final String RESOURCE = "/data/elderlexicon/lexicon/materials.json";

    private static final List<Consumer<MaterialTableBuilder>> EXTENSIONS = new CopyOnWriteArrayList<>();
    private static volatile MaterialTable current;
    /**
     * How hard each block of the game is (its destroy time, {@code minecraft:stone} 1.5), NaN for one the game lacks:
     * what packs a substance (docs/particulas-design.md, "Densidade"). With none, everything is as loose as earth.
     */
    private static volatile ToDoubleFunction<String> hardness;

    /** Earth's hardness: what is as hard as dirt is packed 1, a block of 4096 particles. */
    public static final double EARTH_HARDNESS = 0.5D;
    /** What cannot be broken (bedrock) is packed as if this hard. */
    public static final double UNBREAKABLE_HARDNESS = 100.0D;
    /** What breaks at a touch (a flower, a torch) is still something: never packed looser than this. */
    public static final double LEAST_PACKING = 0.1D;

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

    /**
     * Packs the table by the game's hardness of each substance's block, once the blocks are known: a block of stone
     * (1.5) holds three times the particles of a block of earth (0.5), obsidian (50) a hundred times.
     */
    public static void useHardness(ToDoubleFunction<String> source) {
        synchronized (Materials.class) {
            hardness = source;
            current = null;
        }
    }

    /**
     * How tightly a substance is packed: its solid block's hardness over earth's. A liquid, a gas, fire and the four
     * primordials are as loose as earth (4096 of water is water); so is what has no block.
     */
    public static double packing(Substance substance, ToDoubleFunction<String> hardness) {
        if (hardness == null || substance.nature() != State.SOLID || substance.primordial()) {
            return 1.0D;
        }
        java.util.Optional<String> block = substance.solidBlock();
        if (block.isEmpty()) {
            return 1.0D;
        }
        double hard = hardness.applyAsDouble(block.get());
        if (Double.isNaN(hard)) {
            return 1.0D;
        }
        double of = hard < 0.0D ? UNBREAKABLE_HARDNESS : hard;
        return Math.max(LEAST_PACKING, of / EARTH_HARDNESS);
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
        ToDoubleFunction<String> hard = hardness;
        if (hard != null) {
            builder.pack(substance -> packing(substance, hard));
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
