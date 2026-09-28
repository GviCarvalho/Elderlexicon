package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a {@link MaterialTable}: from the mod's data, and then from whatever an addon brings (more substances, other
 * recipes or forms for the old ones). Building checks that the matter holds together: there is one primordial for each
 * aspect, found in the state it names; no two recipes are so close that a mixture could be both; every block and item
 * reads as one substance only; every substance holds something.
 */
public final class MaterialTableBuilder {

    private final Map<String, Substance> substances = new LinkedHashMap<>();
    private double amalgamSeconds = MaterialTable.DEFAULT_AMALGAM_SECONDS;

    private MaterialTableBuilder() {
    }

    public static MaterialTableBuilder empty() {
        return new MaterialTableBuilder();
    }

    /** A builder holding every substance of {@code table}, to add to or change. */
    public static MaterialTableBuilder from(MaterialTable table) {
        MaterialTableBuilder builder = new MaterialTableBuilder();
        table.substances().forEach(builder::substance);
        builder.amalgamSeconds = table.amalgamSeconds();
        return builder;
    }

    /** Adds a substance, or gives an existing one a new recipe or new forms. */
    public MaterialTableBuilder substance(Substance substance) {
        substances.put(normalize(substance.id()), substance);
        return this;
    }

    /** How long an amalgam holds together before it falls apart (L5), in seconds. */
    public MaterialTableBuilder amalgamSeconds(double seconds) {
        if (!(seconds > 0.0D)) {
            throw new IllegalArgumentException("an amalgam must hold together for some time, not " + seconds + " s");
        }
        this.amalgamSeconds = seconds;
        return this;
    }

    public MaterialTableBuilder remove(String id) {
        substances.remove(normalize(id));
        return this;
    }

    /** Reads a materials file into this builder (see {@link MaterialTableReader} for the format). */
    public MaterialTableBuilder read(Reader json) {
        MaterialTableReader.read(json, this);
        return this;
    }

    public boolean has(String id) {
        return substances.containsKey(normalize(id));
    }

    public MaterialTable build() {
        List<String> problems = new ArrayList<>();
        Map<VitaElement, String> primordials = new HashMap<>();
        for (Substance substance : substances.values()) {
            if (!(substance.unit() > 0.0D)) {
                problems.add("'" + substance.id() + "' holds nothing in a block (its unit is not above zero)");
            }
            substance.recipe().pureAspect().ifPresent(aspect -> {
                String other = primordials.putIfAbsent(aspect, substance.id());
                if (other != null) {
                    problems.add("'" + other + "' and '" + substance.id() + "' are both all " + aspect.runeId());
                }
                State named = State.of(aspect).orElseThrow();
                if (substance.nature() != named) {
                    problems.add("'" + substance.id() + "' is all " + aspect.runeId() + " but is not found "
                            + named.name().toLowerCase(Locale.ROOT));
                }
            });
        }
        for (VitaElement aspect : VitaElement.values()) {
            if (aspect != VitaElement.BALANCED && !primordials.containsKey(aspect)) {
                problems.add("no primordial substance is all " + aspect.runeId());
            }
        }
        List<Substance> all = new ArrayList<>(substances.values());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                double distance = all.get(i).recipe().distance(all.get(j).recipe());
                if (distance < 2.0D * MaterialTable.TOLERANCE - 1.0E-9D) {
                    problems.add("the recipes of '" + all.get(i).id() + "' and '" + all.get(j).id() + "' are "
                            + String.format(Locale.ROOT, "%.2f", distance) + " apart: a mixture could be both (they must"
                            + " be at least " + String.format(Locale.ROOT, "%.2f", 2.0D * MaterialTable.TOLERANCE)
                            + " apart)");
                }
            }
        }
        Map<String, String> readers = new HashMap<>();
        for (Substance substance : all) {
            substance.forms().forEach((state, forms) -> {
                for (Form form : forms) {
                    if (!form.holdsMatter()) {
                        continue;
                    }
                    String key = form.kind().name().toLowerCase(Locale.ROOT) + " " + form.id();
                    String other = readers.putIfAbsent(key, substance.id() + " " + state.name().toLowerCase(Locale.ROOT));
                    if (other != null) {
                        problems.add("the " + key + " would read as both " + other + " and " + substance.id() + " "
                                + state.name().toLowerCase(Locale.ROOT));
                    }
                    if (!(form.umu() > 0.0D)) {
                        problems.add("the " + key + " of '" + substance.id() + "' holds nothing");
                    }
                }
            });
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The matter does not hold together: " + String.join("; ", problems));
        }
        return new MaterialTable(substances, amalgamSeconds);
    }

    static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
