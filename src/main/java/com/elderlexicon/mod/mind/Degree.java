package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.ReferentSpec;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How far a caster has gone in the language (docs/feiticeiro-design.md, 4): how many runes it knows. A creature is born
 * with a draw of them, as a mage learns some and not others; it reads only what it can read whole.
 */
public enum Degree {
    /** Two or three runes: one source, the verbs to throw and to bring it. */
    APPRENTICE(0, 0.5F),
    /** About eight: some more sources, verbs and filters. */
    MASTER(5, 0.35F),
    /** Nearly the whole language. */
    ELDER(Integer.MAX_VALUE, 0.15F);

    /** How many runes it knows beyond the first ones. */
    private final int more;
    /** How often a creature is born of it. */
    private final float chance;

    Degree(int more, float chance) {
        this.more = more;
        this.chance = chance;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Degree byId(String id) {
        for (Degree degree : values()) {
            if (degree.id().equals(id)) {
                return degree;
            }
        }
        return APPRENTICE;
    }

    /** The degree of one who knows {@code runes} runes: it grows as it learns (docs/feiticeiro-design.md, 4). */
    public static Degree of(int runes) {
        return runes <= 4 ? APPRENTICE : runes <= 10 ? MASTER : ELDER;
    }

    /**
     * The runes a creature can learn: the ones the mind writes with (sources, the verbs that do not name nor bind,
     * filters and the caster itself); not conditions, the other one of a happening, naming or bonds.
     */
    public static Set<String> learnable(Lexicon lexicon) {
        Set<String> runes = new LinkedHashSet<>();
        for (Rune rune : lexicon.runes()) {
            boolean verb = rune.verb().map(spec -> !spec.binds() && !spec.names()).orElse(false);
            boolean caster = rune.referent().map(spec -> spec.refers() == ReferentSpec.Refers.CASTER).orElse(false);
            if (rune.is(WordClass.SOURCE) || verb || rune.is(WordClass.FILTER) || caster) {
                runes.add(rune.id());
            }
        }
        return runes;
    }

    /** A degree drawn as creatures are born: most are apprentices, few are elders. */
    public static Degree draw(RandomSource random) {
        float roll = random.nextFloat();
        for (Degree degree : values()) {
            roll -= degree.chance;
            if (roll < 0.0F) {
                return degree;
            }
        }
        return APPRENTICE;
    }

    /**
     * The runes one of this degree knows, drawn from {@code lexicon}: always a source and the verbs that throw and bring
     * (so it can fight), then as many more as the degree holds. The runes the mind does not write with (conditions,
     * the other one of a happening, naming and bonds) are not drawn.
     */
    public Set<String> draw(Lexicon lexicon, RandomSource random) {
        List<String> sources = new ArrayList<>();
        List<String> fighting = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (Rune rune : lexicon.runes()) {
            if (rune.is(WordClass.SOURCE)) {
                sources.add(rune.id());
            } else if (rune.verb().map(verb -> !verb.binds() && !verb.names()).orElse(false)) {
                String operation = lexicon.operationOf(rune.id()).orElse("");
                (operation.equals("project") || operation.equals("manifest") ? fighting : others).add(rune.id());
            } else if (rune.is(WordClass.FILTER) || rune.referent()
                    .map(spec -> spec.refers() == ReferentSpec.Refers.CASTER).orElse(false)) {
                others.add(rune.id());
            }
        }
        Set<String> known = new LinkedHashSet<>();
        if (!sources.isEmpty()) {
            known.add(sources.remove(random.nextInt(sources.size())));
        }
        known.addAll(fighting);
        List<String> rest = new ArrayList<>(sources);
        rest.addAll(others);
        for (int i = 0; i < more && !rest.isEmpty(); i++) {
            known.add(rest.remove(random.nextInt(rest.size())));
        }
        return known;
    }
}
