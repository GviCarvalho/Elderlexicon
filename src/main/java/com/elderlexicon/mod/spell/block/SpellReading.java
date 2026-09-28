package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.magic.lexicon.FilterSpec;
import com.elderlexicon.mod.magic.lexicon.Flow;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Parameter;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.Template;
import com.elderlexicon.mod.magic.lexicon.VerbSpec;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.Conversion;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a line of a page the way the spirit will and says, for each word, what it is and what it does in that spell:
 * the grimoire shows it when the mage rests the pointer on a rune. Only the reading; nothing is cast.
 * <p>
 * What a word does comes from its place in the sentence (the grammar's rules) and its words from the lexicon, so a rune
 * an addon brings is explained like the book's own.
 */
public final class SpellReading {

    /** What a word is ({@code title}) and what it does in this spell ({@code role}). */
    public record Word(String rune, String title, String role) {
    }

    private enum Kind { EMPTY, SOURCE, FUNCTION, FILTER, FORM, NUMBER, MARK, NAMED }

    private final ParserDictionary dictionary;
    /** Runes the mage named with reframe in this grimoire, each with the spell it holds. */
    private final Map<String, List<String>> named;

    public SpellReading(ParserDictionary dictionary) {
        this(dictionary, Map.of());
    }

    public SpellReading(ParserDictionary dictionary, Map<String, List<String>> named) {
        this.dictionary = dictionary;
        this.named = named == null ? Map.of() : Map.copyOf(named);
    }

    /** The same reading, knowing the runes this grimoire named with reframe. */
    public SpellReading withNamed(Map<String, List<String>> runes) {
        return new SpellReading(dictionary, runes);
    }

    /** What a rune is, in general: its name and a line about it. */
    public static String lore(String rune) {
        Lexicon lexicon = Lexicons.get();
        return lexicon.rune(rune).map(Rune::lore).filter(line -> !line.isBlank())
                .orElseGet(() -> note(lexicon, "reading.fusion", Map.of()));
    }

    /** The title of a word: the rune's name and what it translates to, a number, a mark. */
    public String title(String rune) {
        Kind kind = kindOf(rune);
        return switch (kind) {
            case EMPTY -> note("reading.title.empty", Map.of());
            case NUMBER -> note("reading.title.number", Map.of("n", rune));
            case MARK -> note("reading.title.mark", Map.of("mark", rune));
            case NAMED -> note("reading.title.named", Map.of("name", rune));
            case SOURCE -> note("reading.title.rune", Map.of("rune", rune, "name", nameOf(lexicon().rune(rune).orElseThrow())));
            default -> rune;
        };
    }

    /** Each word of a line (normalized ids, {@code ""} for an empty cell) with what it does in that spell. */
    public List<Word> read(List<String> ids) {
        List<Word> words = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String rune = ids.get(i) == null ? "" : ids.get(i);
            words.add(new Word(rune, title(rune), role(ids, i)));
        }
        return words;
    }

    private String role(List<String> ids, int at) {
        String rune = ids.get(at);
        return switch (kindOf(rune)) {
            case EMPTY -> note("reading.empty", Map.of());
            case NUMBER -> numberRole(ids, at);
            case MARK -> markRole(ids, at);
            case NAMED -> note("reading.named", Map.of("spell", String.join(" ", named.get(rune))));
            case SOURCE -> sourceRole(ids, at);
            case FILTER -> filterRole(ids, at);
            case FUNCTION -> functionRole(ids, at);
            case FORM -> lore(rune);
        };
    }

    private String numberRole(List<String> ids, int at) {
        String number = ids.get(at);
        Optional<Rune> before = previousWord(ids, at).flatMap(i -> lexicon().ofClass(ids.get(i), WordClass.FILTER));
        if (before.isPresent() && argument(before.get()) == FilterSpec.Argument.VALUE) {
            Rune filter = before.get();
            String key = "0".equals(number) && filter.text("number.zero").isPresent() ? "number.zero" : "number";
            return text(filter, key, Map.of("filter", filter.id(), "n", number));
        }
        Optional<Rune> after = nextWord(ids, at).flatMap(i -> lexicon().ofClass(ids.get(i), WordClass.FILTER));
        if (after.isPresent() && argument(after.get()) == FilterSpec.Argument.OPERANDS) {
            return text(after.get(), "number", Map.of("filter", after.get().id(), "n", number));
        }
        return note("reading.number", Map.of());
    }

    private String markRole(List<String> ids, int at) {
        String mark = ids.get(at);
        Optional<Rune> after = nextWord(ids, at).flatMap(i -> lexicon().ofClass(ids.get(i), WordClass.FILTER));
        if (after.isPresent() && argument(after.get()) == FilterSpec.Argument.OPERANDS) {
            return text(after.get(), "mark", Map.of("filter", after.get().id(), "mark", mark));
        }
        for (String id : ids) {
            if (lexicon().isBinding(id)) {
                Rune bond = lexicon().rune(id).orElseThrow();
                return text(bond, "mark", Map.of("verb", bond.id(), "mark", mark));
            }
        }
        Optional<String> function = nextFunction(ids, at);
        return function.map(f -> note("reading.mark.object", Map.of("verb", f, "mark", mark)))
                .orElseGet(() -> note("reading.mark", Map.of("mark", mark)));
    }

    private String sourceRole(List<String> ids, int at) {
        Rune source = lexicon().rune(ids.get(at)).orElseThrow();
        String element = nameOf(source);
        Optional<Rune> previous = previousWord(ids, at).flatMap(i -> lexicon().ofClass(ids.get(i), WordClass.VERB));
        if (previous.isPresent() && flowOf(previous.get()) == Flow.CONVERT) {
            return text(previous.get(), "target", Map.of("verb", previous.get().id(), "element", element));
        }
        Optional<Rune> next = nextWord(ids, at).flatMap(i -> lexicon().ofClass(ids.get(i), WordClass.VERB));
        if (next.isPresent() && flowOf(next.get()) == Flow.CAPTURE) {
            return text(next.get(), "source", Map.of("verb", next.get().id(), "element", element));
        }
        String from = source.text("fromBody").orElse(element + " do corpo");
        return nextFunction(ids, at).map(f -> note("reading.source", Map.of("verb", f, "from", from)))
                .orElseGet(() -> note("reading.source.unused", Map.of("from", from)));
    }

    private String filterRole(List<String> ids, int at) {
        Rune filter = lexicon().rune(ids.get(at)).orElseThrow();
        FilterSpec spec = filter.filter().orElseThrow();
        Optional<Integer> next = nextWord(ids, at);
        String number = next.filter(i -> kindOf(ids.get(i)) == Kind.NUMBER).map(ids::get).orElse(null);
        Map<String, String> values = new HashMap<>();
        values.put("filter", filter.id());
        if (number != null) {
            values.put("n", number);
        }
        if (spec.parameter() == Parameter.QUANTITY) {
            if (number != null) {
                return text(filter, "role", values);
            }
            boolean captured = false;
            for (int i = 0; i < at; i++) {
                captured |= lexicon().verb(ids.get(i)).map(verb -> verb.flow() == Flow.CAPTURE).orElse(false);
            }
            return text(filter, captured ? "role.bare.world" : "role.bare.body", values);
        }
        if (spec.parameter() == Parameter.TIME) {
            return number == null ? text(filter, "role.bare", values)
                    : text(filter, "0".equals(number) ? "role.zero" : "role", values);
        }
        return previousWord(ids, at).map(i -> switch (kindOf(ids.get(i))) {
            case NUMBER -> text(filter, "role.number", Map.of("n", ids.get(i)));
            case MARK -> text(filter, "role.mark", Map.of("mark", ids.get(i)));
            default -> text(filter, "role.other", Map.of());
        }).orElseGet(() -> text(filter, "role.none", Map.of()));
    }

    private String functionRole(List<String> ids, int at) {
        Rune function = lexicon().rune(ids.get(at)).orElseThrow();
        Optional<VerbSpec> verb = function.verb();
        if (verb.isEmpty() || function.text("role").isEmpty() && verb.get().flow() != Flow.CONVERT) {
            return lore(function.id());
        }
        Map<String, String> values = new HashMap<>();
        values.put("what", subjectBefore(ids, at));
        if (verb.get().flow() == Flow.CONVERT) {
            return convertRole(function, ids, at);
        }
        if (verb.get().flow() == Flow.CAPTURE) {
            Optional<String> feeding = nextFunction(ids, at);
            if (feeding.isPresent() && function.text("role.feeding").isPresent()) {
                values.put("next", feeding.get());
                return text(function, "role.feeding", values) + ".";
            }
            return text(function, "role", values) + ".";
        }
        String condensed = condensedBefore(ids, at) ? function.text("role.condensed").orElse("") : "";
        return text(function, "role", values) + condensed + ".";
    }

    private String convertRole(Rune function, List<String> ids, int at) {
        Optional<Rune> from = sourceBefore(ids, at);
        Optional<Integer> next = nextWord(ids, at);
        Optional<Rune> to = next.flatMap(i -> lexicon().ofClass(ids.get(i), WordClass.SOURCE));
        if (to.isEmpty()) {
            return text(function, "role.missing", Map.of());
        }
        String target = nameOf(to.get());
        if (from.isEmpty()) {
            return text(function, "role.nosource", Map.of("to", target));
        }
        VitaElement fromElement = lexicon().elementOf(from.get().id());
        VitaElement toElement = lexicon().elementOf(to.get().id());
        int qualities = Conversion.qualities(fromElement, toElement);
        String cost = qualities == 0 ? text(function, "role.free", Map.of())
                : text(function, "role.cost", Map.of(
                        "q", String.valueOf(qualities),
                        "qualities", text(function, qualities == 1 ? "role.quality" : "role.qualities", Map.of()),
                        "p", String.valueOf((int) Math.round(qualities * Conversion.WORK_PER_QUALITY * 100.0D))));
        boolean inPlace = nextFunction(ids, next.get()).isEmpty();
        return text(function, "role", Map.of("from", nameOf(from.get()), "to", target, "cost", cost))
                + text(function, inPlace ? "role.inplace" : "role.end", Map.of());
    }

    // ------------------------------------------------------------------ reading around a word

    private Kind kindOf(String rune) {
        if (rune == null || rune.isEmpty()) {
            return Kind.EMPTY;
        }
        if (rune.matches("-?\\d+")) {
            return Kind.NUMBER;
        }
        if (named.containsKey(rune)) {
            return Kind.NAMED;
        }
        return lexicon().rune(rune).map(found -> switch (found.wordClass()) {
            case SOURCE -> Kind.SOURCE;
            case FILTER -> Kind.FILTER;
            case FORM -> Kind.FORM;
            case VERB -> Kind.FUNCTION;
        }).orElse(Kind.MARK);
    }

    /** What a source is called in the grimoire's tooltips (vis keeps its own name there). */
    private static String nameOf(Rune source) {
        return source.text("reading.name").orElse(source.name());
    }

    private static FilterSpec.Argument argument(Rune filter) {
        return filter.filter().map(FilterSpec::argument).orElse(FilterSpec.Argument.VALUE);
    }

    private static Flow flowOf(Rune verb) {
        return verb.verb().map(VerbSpec::flow).orElse(Flow.SPEND);
    }

    private static Optional<Integer> previousWord(List<String> ids, int at) {
        for (int i = at - 1; i >= 0; i--) {
            if (ids.get(i) != null && !ids.get(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> nextWord(List<String> ids, int at) {
        for (int i = at + 1; i < ids.size(); i++) {
            if (ids.get(i) != null && !ids.get(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /** The next verb after {@code at} that spends what comes before it (a conversion or a capture does not). */
    private Optional<String> nextFunction(List<String> ids, int at) {
        for (int i = at + 1; i < ids.size(); i++) {
            String rune = ids.get(i);
            if (kindOf(rune) == Kind.FUNCTION && lexicon().flowOf(rune) == Flow.SPEND) {
                return Optional.of(rune);
            }
        }
        return Optional.empty();
    }

    /** What a verb acts on: the last source before it (after any conversion), or a mark that is not a place. */
    private String subjectBefore(List<String> ids, int at) {
        for (int i = at - 1; i >= 0; i--) {
            String rune = ids.get(i);
            Kind kind = kindOf(rune);
            if (kind == Kind.SOURCE) {
                return lexicon().rune(rune).orElseThrow().noun();
            }
            boolean place = nextWord(ids, i).flatMap(n -> lexicon().ofClass(ids.get(n), WordClass.FILTER))
                    .filter(filter -> argument(filter) == FilterSpec.Argument.OPERANDS).isPresent();
            if (kind == Kind.MARK && !place) {
                return note("reading.subject.mark", Map.of("mark", rune));
            }
        }
        return note("reading.subject.energy", Map.of());
    }

    private Optional<Rune> sourceBefore(List<String> ids, int at) {
        for (int i = at - 1; i >= 0; i--) {
            Optional<Rune> source = lexicon().ofClass(ids.get(i), WordClass.SOURCE);
            if (source.isPresent()) {
                return source;
            }
        }
        return Optional.empty();
    }

    /** Whether a time filter written before says zero: all released in one instant, condensed. */
    private boolean condensedBefore(List<String> ids, int at) {
        for (int i = 0; i + 1 < at; i++) {
            boolean time = lexicon().filter(ids.get(i)).map(filter -> filter.parameter() == Parameter.TIME).orElse(false);
            if (time && "0".equals(nextWord(ids, i).map(ids::get).orElse(""))) {
                return true;
            }
        }
        return false;
    }

    /** The lexicon as it is now, so words an addon adds later are read too. */
    private Lexicon lexicon() {
        return dictionary.lexicon();
    }

    private String note(String key, Map<String, String> values) {
        return note(lexicon(), key, values);
    }

    private static String note(Lexicon lexicon, String key, Map<String, String> values) {
        return Template.fill(lexicon.note(key).orElse(key), values);
    }

    private static String text(Rune rune, String key, Map<String, String> values) {
        return Template.fill(rune.text(key).orElse(rune.lore()), values);
    }
}
