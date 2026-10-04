package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.magic.lexicon.FilterSpec;
import com.elderlexicon.mod.magic.lexicon.Flow;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Meeting;
import com.elderlexicon.mod.magic.lexicon.Parameter;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.Template;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.Conversion;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The grimoire's own description of a page of spells, written once the spirit has cast it: a name for it, what each
 * spell does (grouped by the instant it is released) and notes on what the elements may do where they meet, from the
 * aspects of energy (docs/interacoes-design.md). Only words; what really happens is up to the laws of nature.
 * <p>
 * Every word of it comes from the lexicon: each verb says how its deed is told and each source how it is named, so a
 * rune an addon brings is described like the book's own.
 */
public final class SpellDescription {

    /** What the grimoire writes about a page. */
    public record Text(String name, List<String> paragraphs, List<String> notes) {
    }

    /** Energy a spell spends when no quantum says otherwise (book 4.3.2). */
    private static final int DEFAULT_UMU = 10;

    /**
     * One spell of the page, as the grimoire understands it: {@code capture} when its source is taken from the world
     * (from {@code origin}, or within the mage's reach), {@code reversed} when a negative quantity turns its verb around.
     */
    private record Spell(int release, String source, String mark, boolean capture, String origin, String amount,
                         boolean reversed, List<String> turns, boolean condensed, String seconds, String place,
                         String function, String core, List<String> when) {

        String element() {
            return turns.isEmpty() ? source : turns.get(turns.size() - 1);
        }
    }

    private final ParserDictionary dictionary;
    private final Map<String, List<String>> named;

    public SpellDescription(ParserDictionary dictionary, Map<String, List<String>> named) {
        this.dictionary = dictionary;
        this.named = named == null ? Map.of() : Map.copyOf(named);
    }

    /** The description of a page: its rows as normalized ids ({@code ""} for an empty cell). */
    public Text describe(List<List<String>> rows) {
        List<Spell> spells = new ArrayList<>();
        for (List<String> row : rows) {
            Spell spell = read(row);
            if (spell != null) {
                spells.add(spell);
            }
        }
        if (spells.isEmpty()) {
            return new Text(note("describe.blank", Map.of()), List.of(), List.of());
        }
        TreeMap<Integer, List<Spell>> byInstant = new TreeMap<>();
        for (Spell spell : spells) {
            byInstant.computeIfAbsent(spell.release(), key -> new ArrayList<>()).add(spell);
        }
        List<String> paragraphs = new ArrayList<>();
        Set<String> notes = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<Spell>> instant : byInstant.entrySet()) {
            String when = seconds(instant.getKey());
            List<Spell> together = instant.getValue();
            if (together.size() == 1) {
                paragraphs.add(sentence(together.get(0)) + " " + note("describe.released", Map.of("when", when)));
            } else {
                paragraphs.add(note("describe.together", Map.of("when", when)));
                for (Spell spell : together) {
                    paragraphs.add("• " + sentence(spell));
                }
                notes.addAll(meetings(together));
            }
            for (Spell spell : together) {
                notes.addAll(own(spell));
            }
        }
        return new Text(name(byInstant, spells), paragraphs, new ArrayList<>(notes));
    }

    // ------------------------------------------------------------------ reading a spell

    private Spell read(List<String> row) {
        List<String> written = new ArrayList<>();
        int release = -1;
        for (int i = 0; i < row.size(); i++) {
            String id = row.get(i) == null ? "" : row.get(i);
            if (id.isEmpty()) {
                continue;
            }
            release = i;
            // A named rune is read as the spell it holds.
            written.addAll(named.getOrDefault(id, List.of(id)));
        }
        if (written.isEmpty()) {
            return null;
        }
        // A condition says when the line holds (docs/fluxo-design.md); it is no part of what the spell does.
        List<String> when = written.stream().filter(lexicon()::isCondition).toList();
        List<String> words = written.stream().filter(word -> !lexicon().isCondition(word)).toList();
        String source = null;
        String mark = null;
        boolean capture = false;
        String origin = null;
        String amount = null;
        boolean reversed = false;
        List<String> turns = new ArrayList<>();
        boolean condensed = false;
        String time = null;
        String place = null;
        String function = null;
        // The mark after a conversion of a source: the thing whose core changes (aqua quantum 16 vertere m1).
        String core = null;
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            String next = i + 1 < words.size() ? words.get(i + 1) : "";
            boolean number = word.matches("-?\\d+");
            Optional<Rune> placeFilter = lexicon().ofClass(next, WordClass.FILTER)
                    .filter(filter -> filter.filter().map(spec -> spec.argument() == FilterSpec.Argument.OPERANDS)
                            .orElse(false));
            if ((number || isMark(word)) && placeFilter.isPresent()) {
                String where = number ? text(placeFilter.get(), "place.distance", Map.of("n", word))
                        : text(placeFilter.get(), "place.mark", Map.of("mark", word));
                // The operands of an origin say where the source comes from; those of a place, where the verb acts.
                if (parameterOf(placeFilter.get()) == Parameter.ORIGIN) {
                    capture = true;
                    origin = where;
                } else {
                    place = where;
                }
                i++;
                continue;
            }
            if (isMark(word)) {
                mark = word;
                continue;
            }
            Optional<Rune> rune = lexicon().rune(word);
            if (rune.isEmpty()) {
                continue;
            }
            if (rune.get().is(WordClass.SOURCE)) {
                if (source == null) {
                    source = word;
                }
                continue;
            }
            Flow flow = rune.get().verb().map(verb -> verb.flow()).orElse(null);
            Parameter parameter = rune.get().filter()
                    .filter(filter -> filter.argument() == FilterSpec.Argument.VALUE)
                    .map(FilterSpec::parameter).orElse(null);
            if (parameterOf(rune.get()) == Parameter.ORIGIN) {
                capture = true;
            } else if (flow == Flow.CONVERT) {
                if (lexicon().ofClass(next, WordClass.SOURCE).isPresent()) {
                    turns.add(next);
                    i++;
                } else if (mark == null && !next.isEmpty() && isMark(next)) {
                    core = next;
                    i++;
                }
            } else if (parameter == Parameter.QUANTITY) {
                if (next.matches("-?\\d+")) {
                    // Negative, it turns the verb around; its size is still what is spent.
                    reversed = next.startsWith("-");
                    amount = reversed ? next.substring(1) : next;
                    i++;
                } else {
                    amount = "all";
                }
            } else if (parameter == Parameter.TIME) {
                if ("0".equals(next)) {
                    condensed = true;
                    i++;
                } else if (next.matches("-?\\d+")) {
                    time = next;
                    i++;
                }
            } else if (flow == Flow.SPEND) {
                function = word;
            }
        }
        return new Spell(release, source, mark, capture, origin, amount, reversed, turns, condensed, time, place,
                function, core, when);
    }

    private static Parameter parameterOf(Rune rune) {
        return rune.filter().map(FilterSpec::parameter).orElse(null);
    }

    private boolean isMark(String word) {
        return !word.matches("-?\\d+") && !named.containsKey(word) && !lexicon().isRune(word);
    }

    // ------------------------------------------------------------------ writing it

    private String sentence(Spell spell) {
        if (spell.when().isEmpty()) {
            return deed(spell);
        }
        List<String> happenings = spell.when().stream()
                .map(word -> lexicon().rune(word).flatMap(rune -> rune.text("describe")).orElse(word))
                .toList();
        return note("describe.when", Map.of("when", String.join(note("describe.and", Map.of()), happenings)))
                + " " + deed(spell);
    }

    private String deed(Spell spell) {
        StringBuilder text = new StringBuilder();
        if (spell.core() != null) {
            String element = elementName(spell.source() == null ? lexicon().defaultSource().id() : spell.source());
            String key = spell.amount() == null ? "describe.core.unmeasured"
                    : "all".equals(spell.amount()) ? "describe.core.all" : "describe.core";
            return note(key, Map.of("mark", spell.core(), "element", element,
                    "parts", spell.amount() == null ? "" : spell.amount())) + ".";
        }
        if (spell.source() == null && spell.mark() != null) {
            text.append(capitalize(action(spell, note("describe.marked", Map.of("mark", spell.mark())))));
            return text.append('.').toString();
        }
        if (spell.source() == null && spell.amount() == null) {
            // No energy named: only what is done (surgit reads, a verb on its own).
            String done = action(spell, null);
            return capitalize(done.startsWith("e ") ? done.substring(2) : done) + ".";
        }
        String element = elementName(spell.source());
        String amount = "all".equals(spell.amount()) ? note("describe.all", Map.of("element", element))
                : note("describe.amount", Map.of("amount", spell.amount() == null ? String.valueOf(DEFAULT_UMU)
                        : spell.amount(), "element", element));
        String origin = origin(spell);
        if (!spell.turns().isEmpty()) {
            List<String> into = spell.turns().stream().map(this::elementName).toList();
            text.append(note(spell.capture() ? "describe.capture" : "describe.convert", Map.of())).append(' ')
                    .append(amount).append(origin)
                    .append(note(spell.capture() ? "describe.capture.convert" : "describe.into", Map.of()))
                    .append(String.join(note("describe.then", Map.of()), into));
        } else {
            text.append(note(spell.capture() ? "describe.capture" : "describe.use", Map.of())).append(' ')
                    .append(amount).append(origin);
        }
        if (spell.condensed()) {
            text.append(note("describe.condensed", Map.of()));
        }
        if (spell.seconds() != null) {
            text.append(note("describe.seconds", Map.of("s", spell.seconds())));
        }
        text.append(' ').append(action(spell, null));
        return text.append('.').toString();
    }

    /**
     * Where the energy comes from: the world (around where the origin says, or the mage's reach), where a verb turned
     * around takes it from, or the body.
     */
    private String origin(Spell spell) {
        Optional<Rune> verb = spell.function() == null ? Optional.empty() : lexicon().rune(spell.function());
        if (spell.reversed() && verb.isPresent() && verb.get().text("origin.reversed").isPresent()) {
            return verb.get().text("origin.reversed").get();
        }
        if (spell.capture()) {
            return spell.origin() == null ? note("describe.world", Map.of())
                    : note("describe.world.place", Map.of("place", spell.origin()));
        }
        return lexicon().rune(spell.source() == null ? "" : spell.source()).flatMap(rune -> rune.text("origin"))
                .orElseGet(() -> note("describe.body", Map.of()));
    }

    /** What the spell does with its energy; {@code subject} names it when it is a marked thing. */
    private String action(Spell spell, String subject) {
        if (spell.function() == null) {
            return note("describe.nothing", Map.of());
        }
        Optional<Rune> verb = lexicon().rune(spell.function());
        if (verb.isEmpty() || verb.get().text("describe").isEmpty() && verb.get().text("describe.marked").isEmpty()) {
            return note("describe.unformed", Map.of("verb", spell.function()));
        }
        String where = spell.place() == null ? verb.get().text("describe.where").orElse("") : " " + spell.place();
        if (spell.reversed() && verb.get().text("describe.reversed").isPresent()) {
            return text(verb.get(), "describe.reversed", Map.of("where", where));
        }
        if (subject != null && verb.get().text("describe.marked").isPresent()) {
            return text(verb.get(), "describe.marked", Map.of("what", subject, "where", where));
        }
        return text(verb.get(), "describe", Map.of("where", where));
    }

    /** What the elements released in the same instant may do to each other, by their aspects. */
    private List<String> meetings(List<Spell> together) {
        Set<VitaElement> elements = elementsOf(together);
        List<String> notes = new ArrayList<>();
        for (Meeting meeting : lexicon().meetings()) {
            if (meeting.among(elements)) {
                notes.add(meeting.note());
            }
        }
        return notes;
    }

    private Set<VitaElement> elementsOf(List<Spell> spells) {
        Set<VitaElement> elements = EnumSet.noneOf(VitaElement.class);
        for (Spell spell : spells) {
            // A core changed releases nothing: its source only says which part of the core changes.
            if (spell.element() != null && spell.core() == null) {
                elements.add(lexicon().elementOf(spell.element()));
            }
        }
        return elements;
    }

    /** What a spell alone is worth noting. */
    private List<String> own(Spell spell) {
        List<String> notes = new ArrayList<>();
        if (spell.capture() && spell.source() != null) {
            lexicon().rune(spell.source()).flatMap(rune -> rune.text("captureNote")).ifPresent(notes::add);
        }
        if (spell.condensed()) {
            notes.add(note("note.condensed", Map.of()));
        }
        if (spell.condensed() && spell.element() != null) {
            lexicon().rune(spell.element()).flatMap(rune -> rune.text("condensedNote")).ifPresent(notes::add);
        }
        if (!spell.turns().isEmpty() && crossesSteps(spell)) {
            notes.add(note("note.conversion", Map.of()));
        }
        if (spell.core() != null) {
            notes.add(note("note.core", Map.of()));
        }
        return notes;
    }

    /** Whether the conversions of a spell cross any rung of the ladder (making anything of Vis crosses none). */
    private boolean crossesSteps(Spell spell) {
        VitaElement from = lexicon().elementOf(spell.source() == null ? lexicon().defaultSource().id() : spell.source());
        for (String turn : spell.turns()) {
            VitaElement to = lexicon().elementOf(turn);
            if (Conversion.steps(from, to) > 0) {
                return true;
            }
            from = to;
        }
        return false;
    }

    // ------------------------------------------------------------------ the name

    private String name(TreeMap<Integer, List<Spell>> byInstant, List<Spell> spells) {
        for (List<Spell> together : byInstant.values()) {
            if (together.size() < 2) {
                continue;
            }
            Set<VitaElement> elements = elementsOf(together);
            for (Meeting meeting : lexicon().meetings()) {
                if (meeting.name() != null && meeting.among(elements)) {
                    return meeting.name();
                }
            }
        }
        Set<String> names = new LinkedHashSet<>();
        for (Spell spell : spells) {
            names.add(name(spell));
        }
        List<String> distinct = new ArrayList<>(names);
        return distinct.size() == 1 ? distinct.get(0)
                : distinct.get(0) + note("describe.and", Map.of()) + lower(distinct.get(1));
    }

    private String name(Spell spell) {
        if (spell.core() != null) {
            Optional<String> title = lexicon().runes().stream()
                    .filter(rune -> rune.verb().map(verb -> verb.flow() == Flow.CONVERT).orElse(false))
                    .map(rune -> rune.text("title.core")).flatMap(Optional::stream).findFirst();
            if (title.isPresent()) {
                return title.get();
            }
        }
        if (spell.function() == null) {
            return note("describe.incomplete", Map.of());
        }
        Optional<Rune> verb = lexicon().rune(spell.function());
        String operation = verb.flatMap(Rune::verb).map(spec -> spec.operation()).orElse(spell.function());
        String key = "title." + operation + (spell.condensed() ? ".condensed" : "");
        Optional<String> own = spell.element() == null ? Optional.empty()
                : lexicon().rune(spell.element()).flatMap(rune -> rune.text(key));
        if (own.isPresent()) {
            return own.get();
        }
        if (verb.isPresent() && verb.get().text("title").isPresent()) {
            return text(verb.get(), "title", Map.of("element", elementName(spell.element())));
        }
        if (verb.isPresent() && spell.mark() != null && verb.get().text("title.marked").isPresent()) {
            return verb.get().text("title.marked").get();
        }
        return verb.flatMap(rune -> rune.text("title.fallback")).orElseGet(() -> note("describe.spell", Map.of()));
    }

    /** An element's name in the text; a spell with no element works on "energia". */
    private String elementName(String element) {
        if (element == null) {
            return note("describe.energy", Map.of());
        }
        return lexicon().rune(element).map(Rune::name).orElse(element);
    }

    /** The lexicon as it is now, so words an addon adds later are read too. */
    private Lexicon lexicon() {
        return dictionary.lexicon();
    }

    private String note(String key, Map<String, String> values) {
        return Template.fill(lexicon().note(key).orElse(key), values);
    }

    private static String text(Rune rune, String key, Map<String, String> values) {
        return Template.fill(rune.text(key).orElse(""), values);
    }

    private static String seconds(int column) {
        return String.format(Locale.ROOT, "%.2f", column * 0.25D).replace('.', ',');
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String lower(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
