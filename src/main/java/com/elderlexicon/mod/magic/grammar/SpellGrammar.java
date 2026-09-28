package com.elderlexicon.mod.magic.grammar;

import com.elderlexicon.mod.magic.lexicon.FilterSpec;
import com.elderlexicon.mod.magic.lexicon.Flow;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.LexiconBuilder;
import com.elderlexicon.mod.magic.lexicon.ObjectFrame;
import com.elderlexicon.mod.magic.lexicon.Parameter;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.VerbSpec;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.action.SpellActionType;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spell.mark.SpellWords;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The grammar of the Old Tongue, written for no rune in particular: it reads a sentence by what each word is (a source,
 * a verb, a filter, a form, a number, a mark, a name) and by the frames the lexicon gives each rune, and turns it into
 * the steps of a spell ({@link SpellAction}). Any rune the lexicon holds, the mod's or an addon's, is read by the same
 * rules.
 * <p>
 * The rules, as the book gives them (cap. 4 and 5) and as the mod has settled them:
 * <ul>
 *   <li>A sentence acts on a <b>subject</b>: the last source written (mana when none is, book 4.2) or a mark written
 *       right before a verb. The subject stays through the verbs that follow until another is written, so
 *       {@code m1 vocant iactare} brings m1 and then throws it.</li>
 *   <li><b>Verbs</b> act in the order they are written. A verb that captures ({@code exsugat}) or converts
 *       ({@code vertere}) changes the energy the next ones spend; a verb that views ({@code surgit}) written right
 *       before another makes that one work with the image of the subject alone.</li>
 *   <li>A <b>filter</b> acts on what comes after it: a value filter takes the number right after it (or right before
 *       it); a place filter takes the numbers and marks right before it. A quantity written before a source measures
 *       the source.</li>
 *   <li>A verb may <b>take a word after it</b>: a target ({@code vertere aqua}), its subject ({@code surgit r2}) or a
 *       name ({@code reframe fireball}), as its frame says.</li>
 *   <li>A <b>bond</b> verb ({@code ligabis}) reads its own aspect and marks; written with a sense verb as its aspect
 *       ({@code surgit m1 ligabis}) it binds that sense.</li>
 *   <li>A <b>fusion</b> that the lexicon writes out as other runes is read as those runes
 *       ({@code igni transiectio aqua} is {@code igni vertere aqua iactare}).</li>
 * </ul>
 */
public final class SpellGrammar {

    private final Lexicon lexicon;

    public SpellGrammar(Lexicon lexicon) {
        this.lexicon = Objects.requireNonNull(lexicon, "lexicon");
    }

    public Lexicon lexicon() {
        return lexicon;
    }

    public SpellActionResult read(List<String> rawLexemes) {
        if (rawLexemes == null || rawLexemes.isEmpty()) {
            return SpellActionResult.empty();
        }
        List<String> sanitized = sanitize(rawLexemes);
        List<String> issues = new ArrayList<>();
        List<String> words = expand(sanitized, issues);
        Optional<SpellActionResult> bond = boundSense(sanitized, words, issues);
        if (bond.isPresent()) {
            return bond.get();
        }
        // A bond reads its marks from the words themselves, and the word after a naming verb is the name it gives.
        boolean binding = words.stream().anyMatch(lexicon::isBinding);
        Set<Integer> names = namePositions(words);
        List<Token> tokens = new ArrayList<>();
        for (int index = 0; index < words.size(); index++) {
            String lexeme = words.get(index);
            Optional<Rune> rune = lexicon.rune(lexeme);
            if (rune.isPresent()) {
                tokens.add(Token.rune(lexeme, rune.get(), false));
                continue;
            }
            if (names.contains(index)) {
                continue;
            }
            SpellWords.Kind kind = SpellWords.classify(lexeme, word -> false);
            if (kind == SpellWords.Kind.MARK || kind == SpellWords.Kind.NUMBER) {
                if (!binding) {
                    tokens.add(new Token(lexeme, null, false, kind));
                }
                continue;
            }
            issues.add("Lexema desconhecido: " + lexeme);
        }

        if (tokens.isEmpty()) {
            return SpellActionResult.of(sanitized, List.of(), Optional.empty(), issues, List.of());
        }

        ensureLeadingSource(tokens);
        Optional<Parser.PrimarySource> primarySource = determinePrimarySource(tokens);
        List<VertereRequest> vertereRequests = new ArrayList<>();
        List<SpellAction> actions = buildActions(tokens, issues, vertereRequests);
        return SpellActionResult.of(sanitized, actions, primarySource, issues, vertereRequests);
    }

    // ------------------------------------------------------------------ fusions written out

    /**
     * Writes out every fusion that stands for other runes, the word after it going where its expansion says
     * ({@link Rune#NEXT_WORD}). A shorthand may stand for others, up to {@link LexiconBuilder#MAX_EXPANSION_DEPTH}.
     */
    public List<String> expand(List<String> words, List<String> issues) {
        List<String> current = words;
        for (int depth = 0; depth <= LexiconBuilder.MAX_EXPANSION_DEPTH; depth++) {
            boolean changed = false;
            List<String> next = new ArrayList<>(current.size());
            for (int index = 0; index < current.size(); index++) {
                Optional<Rune> rune = lexicon.rune(current.get(index)).filter(Rune::isShorthand);
                if (rune.isEmpty()) {
                    next.add(current.get(index));
                    continue;
                }
                changed = true;
                boolean takesNext = rune.get().expansion().contains(Rune.NEXT_WORD);
                String following = takesNext && index + 1 < current.size() ? current.get(index + 1) : null;
                for (String word : rune.get().expansion()) {
                    if (!Rune.NEXT_WORD.equals(word)) {
                        next.add(word);
                    } else if (following != null) {
                        next.add(following);
                    }
                }
                if (following != null) {
                    index++;
                }
            }
            current = next;
            if (!changed) {
                return current;
            }
        }
        issues.add("As fusões deste feitiço se desdobram sem fim.");
        return current;
    }

    // ------------------------------------------------------------------ a sense bound (surgit m1 ligabis)

    /**
     * A sense verb written before a bond ({@code surgit m1 ligabis}) binds that sense of the mage to what bears the mark
     * (docs/surgit-visao-design.md, section 2). Bonds lose their marks and numbers before the rest is read, so this one
     * is read here, whole: the first mark after the sense verb, and the first time filter for how long.
     */
    private Optional<SpellActionResult> boundSense(List<String> sanitized, List<String> words, List<String> issues) {
        int bond = indexOf(words, lexicon::isBinding);
        int sense = indexOf(words, word -> lexicon.verb(word).map(verb -> verb.sense() != null).orElse(false));
        if (sense < 0 || bond < 0 || sense > bond) {
            return Optional.empty();
        }
        String senseVerb = words.get(sense);
        String mark = null;
        for (int index = sense + 1; index < bond && mark == null; index++) {
            String word = words.get(index);
            if (!lexicon.isRune(word) && SpellWords.classify(word, known -> false) == SpellWords.Kind.MARK) {
                mark = word;
            }
        }
        if (mark == null) {
            issues.add("O vinculo do " + senseVerb + " precisa de uma marca depois dele: '" + senseVerb + " m1 "
                    + words.get(bond) + "'.");
            return Optional.of(SpellActionResult.of(sanitized, List.of(), Optional.empty(), issues, List.of()));
        }
        Double seconds = null;
        int time = indexOf(words, word -> lexicon.filter(word).map(f -> f.parameter() == Parameter.TIME).orElse(false));
        if (time >= 0 && time + 1 < words.size()) {
            OptionalDouble written = SpellWords.number(words.get(time + 1));
            if (written.isPresent()) {
                seconds = seconds(words.get(time), written.getAsDouble(), issues);
            }
        }
        Rune fallback = lexicon.defaultSource();
        SpellAction bound = SpellAction.builder(senseVerb, SpellActionType.FUNCTION)
                .element(lexicon.elementOf(fallback.id()))
                .putMetadata("elementRuneId", fallback.id())
                .putMetadata(SpellAction.SUBJECT_MARK, mark)
                .putMetadata(SpellAction.SIGHT_BOND, Boolean.TRUE)
                .putMetadata(SpellAction.SECONDS, seconds)
                .build();
        return Optional.of(SpellActionResult.of(sanitized, List.of(bound), determinePrimarySource(List.of()), issues,
                List.of()));
    }

    // ------------------------------------------------------------------ reading the sentence

    private List<String> sanitize(List<String> rawLexemes) {
        List<String> sanitized = new ArrayList<>(rawLexemes.size());
        for (String token : rawLexemes) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                sanitized.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return sanitized;
    }

    /** The words right after a verb that gives names ({@code reframe fireball}), unless they are runes. */
    private Set<Integer> namePositions(List<String> words) {
        Set<Integer> names = new HashSet<>();
        for (int index = 0; index + 1 < words.size(); index++) {
            if (lexicon.isNaming(words.get(index))) {
                names.add(index + 1);
            }
        }
        return names;
    }

    private void ensureLeadingSource(List<Token> tokens) {
        if (tokens.isEmpty() || tokens.get(0).isClass(WordClass.SOURCE)) {
            return;
        }
        Rune fallback = lexicon.defaultSource();
        if (fallback == null) {
            return;
        }
        tokens.add(0, Token.rune(fallback.id(), fallback, true));
    }

    private Optional<Parser.PrimarySource> determinePrimarySource(List<Token> tokens) {
        Rune fallback = lexicon.defaultSource();
        Parser.PrimarySource fallbackPrimary = fallback != null
                ? new Parser.PrimarySource(ParserDictionary.definitionOf(fallback)) : null;
        Parser.PrimarySource latestSource = null;
        for (Token token : tokens) {
            if (token.isClass(WordClass.SOURCE)) {
                latestSource = new Parser.PrimarySource(ParserDictionary.definitionOf(token.rune()));
            }
        }
        if (latestSource != null) {
            return Optional.of(latestSource);
        }
        return Optional.ofNullable(fallbackPrimary);
    }

    private List<SpellAction> buildActions(List<Token> tokens, List<String> issues,
                                           List<VertereRequest> vertereRequests) {
        List<SpellAction> actions = new ArrayList<>();
        SpellForm form = new SpellForm(lexicon);
        Pending pending = null;
        // Numbers and marks written one after the other, waiting for what gives them a role: a place filter makes them a
        // place, a value filter takes a number, a verb takes the last mark as its subject.
        List<Token> run = new ArrayList<>();
        SpellPlace place = null;
        // A value filter waiting for the number right after it (igni quantum 20 iactare).
        Token awaiting = null;
        Double quantity = null;
        // A quantity written before a source measures the source, not the verb (quantum 2 firmo surgit).
        Double sourceValue = null;
        Double seconds = null;
        // A view verb written right before another: that one works only with the image of its subject.
        boolean imageOnly = false;
        // A bare quantity followed by another filter (quantum chronos 0): all there is.
        boolean bareAll = false;
        // The mark the sentence acts on, until another subject is written.
        String subject = null;

        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);

            if (pending != null
                    && pending.requiresImmediateTarget()
                    && !token.isClass(WordClass.SOURCE)
                    && !(token.kind() == SpellWords.Kind.MARK && pending.acceptsMark())) {
                issues.add("Função '" + pending.id() + "' requer uma fonte alvo imediatamente após.");
                pending = null;
            }

            if (token.kind() == SpellWords.Kind.NUMBER) {
                double value = SpellWords.number(token.lexeme()).orElse(0.0D);
                if (awaiting != null) {
                    Parameter parameter = awaiting.filter().parameter();
                    if (parameter == Parameter.QUANTITY) {
                        quantity = positive(awaiting.lexeme(), value, issues, quantity);
                    } else if (parameter == Parameter.TIME) {
                        seconds = seconds(awaiting.lexeme(), value, issues);
                    } else {
                        place = replacePlace(place, SpellPlace.distance(value), issues);
                    }
                    awaiting = null;
                } else {
                    run.add(token);
                }
                continue;
            }
            if (token.kind() == SpellWords.Kind.MARK) {
                if (pending != null && pending.acceptsMark()) {
                    actions.add(pending.buildWithMark(token.lexeme()));
                    if (pending.flow() == Flow.CONVERT) {
                        subject = token.lexeme(); // m1 vertere m2: what bore m1 now bears m2
                    }
                    pending = null;
                    continue;
                }
                run.add(token);
                continue;
            }

            Rune rune = token.rune();
            switch (rune.wordClass()) {
                case FORM -> form.addShape(rune.form());
                case FILTER -> {
                    FilterSpec filter = token.filter();
                    if (filter.argument() == FilterSpec.Argument.VALUE) {
                        if (awaiting != null && awaiting.filter().bareAll()
                                && awaiting.filter().parameter() != filter.parameter()) {
                            // igni exsugat quantum chronos 0 iactare: all that was captured, released in one instant.
                            bareAll = true;
                            awaiting = null;
                        }
                        if (awaiting != null) {
                            issues.add("'" + awaiting.lexeme() + "' requer um número logo depois.");
                            awaiting = null;
                        }
                        // The book writes the number after (quantum 20); a number written just before (20 quantum),
                        // the way a place filter takes it, is accepted too.
                        if (!run.isEmpty() && run.get(run.size() - 1).kind() == SpellWords.Kind.NUMBER) {
                            double before = SpellWords.number(run.remove(run.size() - 1).lexeme()).orElse(0.0D);
                            if (filter.parameter() == Parameter.QUANTITY) {
                                quantity = positive(rune.id(), before, issues, quantity);
                            } else if (filter.parameter() == Parameter.TIME) {
                                seconds = seconds(rune.id(), before, issues);
                            } else {
                                place = replacePlace(place, SpellPlace.distance(before), issues);
                            }
                            break;
                        }
                        awaiting = token;
                        break;
                    }
                    SpellPlace written;
                    if (!run.isEmpty()) {
                        // Everything written right before a place filter is the place (book: "igni 10 ubis vocant").
                        written = placeOf(rune.id(), run, issues);
                        run.clear();
                    } else {
                        // Numbers written just after (ubis 10) are accepted too, as for the value filters.
                        List<Token> after = new ArrayList<>();
                        while (index + 1 < tokens.size() && tokens.get(index + 1).kind() == SpellWords.Kind.NUMBER) {
                            index++;
                            after.add(tokens.get(index));
                        }
                        if (after.isEmpty()) {
                            issues.add("'" + rune.id() + "' requer um lugar: um número, três números ou uma marca.");
                            written = null;
                        } else {
                            written = placeOf(rune.id(), after, issues);
                        }
                    }
                    if (written != null) {
                        if (filter.parameter() == Parameter.PLACE) {
                            place = replacePlace(place, written, issues);
                        } else if (written.kind() == SpellPlace.Kind.DISTANCE) {
                            // A filter of another kind that takes what is before it takes it as its value.
                            if (filter.parameter() == Parameter.QUANTITY) {
                                quantity = positive(rune.id(), written.distance(), issues, quantity);
                            } else {
                                seconds = seconds(rune.id(), written.distance(), issues);
                            }
                        }
                    }
                }
                case SOURCE -> {
                    VitaElement element = lexicon.elementOf(rune.id());
                    if (pending != null) {
                        if (!pending.acceptsSource()) {
                            issues.add("Função '" + pending.id() + "' " + pending.refusal());
                        } else if (pending.requiresImmediateTarget() && token.implicit()) {
                            issues.add("Função '" + pending.id() + "' requer uma fonte alvo declarada.");
                        } else {
                            actions.add(pending.buildWithSource(token, vertereRequests));
                        }
                        pending = null;
                    }
                    for (Token unused : run) {
                        if (unused.kind() == SpellWords.Kind.MARK) {
                            issues.add("Marca '" + unused.lexeme() + "' sem uso: escreva '" + unused.lexeme()
                                    + " " + placeWord() + "' para um lugar, ou a marca logo antes da função.");
                        } else {
                            issues.add("Número sem uso: " + unused.lexeme() + ".");
                        }
                    }
                    run.clear();
                    if (!token.implicit() && quantity != null) {
                        sourceValue = quantity;
                        quantity = null;
                    }
                    if (!token.implicit()) {
                        SpellAction sourceAction = SpellAction.builder(rune.id(), SpellActionType.SOURCE)
                                .element(element)
                                .shapes(form.snapshotShapes())
                                .putMetadata("elementRuneId", rune.id())
                                .build();
                        actions.add(sourceAction);
                    }
                    form.setElement(element, rune.id());
                    // A source written is the new subject: the verbs after it move energy, not the mark before it.
                    subject = null;
                }
                case VERB -> {
                    VerbSpec verb = token.verb();
                    if (verb.view() && index + 1 < tokens.size() && tokens.get(index + 1).isClass(WordClass.VERB)) {
                        // igni surgit vocant: the view leaves only the image of the subject; the mark, place and filters
                        // written so far are left for the verb that follows (docs/surgit-visao-design.md, 4).
                        imageOnly = true;
                        break;
                    }
                    // A bare quantity is all of the source there is (book 4.3.2): after a capture, all of it in reach
                    // (igni exsugat quantum iactare); without one, all of it in the mage's own body (firmo quantum
                    // chronos 0 iactare: all the body's earth; vis quantum …: all its mana).
                    boolean all = (awaiting != null && awaiting.filter().bareAll()) || bareAll;
                    bareAll = false;
                    if (all && awaiting != null && awaiting.filter().bareAll()) {
                        awaiting = null;
                    }
                    String mark = subjectOf(run, issues);
                    run.clear();
                    Double measure = null;
                    ObjectFrame frame = verb.object();
                    if (mark == null && verb.takesSubjectAfter()) {
                        // surgit r2: the mark written right after it is its subject ("read r2").
                        if (index + 1 < tokens.size() && tokens.get(index + 1).kind() == SpellWords.Kind.MARK) {
                            index++;
                            mark = tokens.get(index).lexeme();
                        }
                        // Written before its subject, a value written after says how much of the subject is meant:
                        // surgit m1 quantum 0 hides m1; with no mark (surgit quantum 0) the subject is what the mage
                        // aims at (docs/surgit-visao-design.md, section 5).
                        while (index + 2 < tokens.size() && tokens.get(index + 1).isValueFilter()
                                && tokens.get(index + 2).kind() == SpellWords.Kind.NUMBER) {
                            Token filterToken = tokens.get(index + 1);
                            double value = SpellWords.number(tokens.get(index + 2).lexeme()).orElse(0.0D);
                            Parameter parameter = filterToken.filter().parameter();
                            if (parameter == Parameter.QUANTITY) {
                                measure = value;
                            } else if (parameter == Parameter.TIME) {
                                seconds = seconds(filterToken.lexeme(), value, issues);
                            } else {
                                break;
                            }
                            index += 2;
                        }
                    }
                    if (mark == null) {
                        // The subject named earlier in the sentence is still the subject (m1 vocant iactare).
                        mark = subject;
                    }
                    SpellAction.Builder builder = SpellAction.builder(rune.id(), SpellActionType.FUNCTION)
                            .element(form.element())
                            .shapes(form.snapshotShapes())
                            .putMetadata("elementRuneId", form.elementRuneId())
                            .putMetadata(SpellAction.SUBJECT_MARK, mark)
                            .putMetadata(SpellAction.PLACE, place)
                            .putMetadata(SpellAction.QUANTITY, quantity)
                            .putMetadata(SpellAction.SOURCE_QUANTITY, sourceValue)
                            .putMetadata(SpellAction.SECONDS, seconds)
                            .putMetadata(SpellAction.IMAGE, imageOnly ? Boolean.TRUE : null)
                            .putMetadata(SpellAction.QUANTITY_ALL, all ? Boolean.TRUE : null);
                    if (frame != null && frame.measure() != null) {
                        builder.putMetadata(frame.measure(), measure);
                    }
                    imageOnly = false;
                    place = null;
                    quantity = null;
                    sourceValue = null;
                    seconds = null;
                    if (awaiting != null) {
                        issues.add("'" + awaiting.lexeme() + "' requer um número logo depois.");
                        awaiting = null;
                    }
                    subject = mark;
                    if (verb.awaitsTarget()) {
                        if (pending != null) {
                            issues.add("Função pendente '" + pending.id() + "' substituída por '" + rune.id() + "'.");
                        }
                        pending = new Pending(rune, verb, builder, form.element(), mark);
                    } else {
                        actions.add(builder.build());
                    }
                }
                default -> {
                }
            }
        }

        if (pending != null) {
            if (pending.mayStandAlone()) {
                actions.add(pending.buildAlone());
            } else {
                issues.add("Função '" + pending.id() + "' requer uma fonte seguinte.");
            }
        }
        for (Token unused : run) {
            issues.add(unused.kind() == SpellWords.Kind.MARK
                    ? "Marca '" + unused.lexeme() + "' sem função depois dela."
                    : "Número sem uso: " + unused.lexeme() + ".");
        }
        if (place != null) {
            issues.add("Lugar " + place.describe() + " sem função depois dele.");
        }
        if (awaiting != null) {
            issues.add("'" + awaiting.lexeme() + "' requer um número logo depois.");
        } else if (quantity != null || sourceValue != null || seconds != null) {
            issues.add("Filtro sem função depois dele.");
        }
        return actions;
    }

    /** The word the lexicon writes a place with, for the spirit's advice. */
    private String placeWord() {
        for (Rune rune : lexicon.runes()) {
            if (rune.filter().map(filter -> filter.parameter() == Parameter.PLACE).orElse(false)) {
                return rune.id();
            }
        }
        return "?";
    }

    private static SpellPlace replacePlace(SpellPlace place, SpellPlace written, List<String> issues) {
        if (place != null) {
            issues.add("Lugar " + place.describe() + " substituído por " + written.describe() + ".");
        }
        return written;
    }

    /** A quantity is more than nothing; asked for nothing, the verb keeps what it had. */
    private static Double positive(String filter, double value, List<String> issues, Double keep) {
        if (value > 0.0D) {
            return value;
        }
        issues.add("'" + filter + "' precisa de uma quantidade maior que zero.");
        return keep;
    }

    /** Negative time is "matter for another volume" (book 4.3.2): refused, so the verb keeps its own timing. */
    private static Double seconds(String filter, double value, List<String> issues) {
        if (value < 0.0D) {
            issues.add("'" + filter + "' negativo ainda nao e compreendido (materia para outro volume).");
            return null;
        }
        return value;
    }

    /**
     * The place written before a place filter: one number is a distance, two are a distance and a height (above the
     * mage), three are coordinates where any of them may be a mark (that coordinate of where the mark is), and a lone
     * mark is where the mark is.
     */
    private static SpellPlace placeOf(String filter, List<Token> written, List<String> issues) {
        if (written.size() == 1) {
            Token only = written.get(0);
            return only.kind() == SpellWords.Kind.MARK
                    ? SpellPlace.mark(only.lexeme())
                    : SpellPlace.distance(SpellWords.number(only.lexeme()).orElse(0.0D));
        }
        if (written.size() == 2) {
            if (written.get(0).kind() == SpellWords.Kind.NUMBER && written.get(1).kind() == SpellWords.Kind.NUMBER) {
                return SpellPlace.distanceAndHeight(SpellWords.number(written.get(0).lexeme()).orElse(0.0D),
                        SpellWords.number(written.get(1).lexeme()).orElse(0.0D));
            }
            issues.add("'" + filter + "' com dois valores quer dois números: distância e altura.");
            return null;
        }
        if (written.size() == 3) {
            return SpellPlace.coordinates(axisOf(written.get(0)), axisOf(written.get(1)), axisOf(written.get(2)));
        }
        issues.add("'" + filter + "' aceita um, dois ou três valores antes dele.");
        return null;
    }

    private static SpellPlace.Axis axisOf(Token token) {
        return token.kind() == SpellWords.Kind.MARK
                ? SpellPlace.Axis.mark(token.lexeme())
                : SpellPlace.Axis.of(SpellWords.number(token.lexeme()).orElse(0.0D));
    }

    /** The last mark written before a verb is its subject; anything else left there is unused. */
    private static String subjectOf(List<Token> run, List<String> issues) {
        String subject = null;
        for (int index = run.size() - 1; index >= 0; index--) {
            Token token = run.get(index);
            if (token.kind() == SpellWords.Kind.MARK && subject == null) {
                subject = token.lexeme();
            } else if (token.kind() == SpellWords.Kind.MARK) {
                issues.add("Marca '" + token.lexeme() + "' sem uso: duas marcas seguidas.");
            } else {
                issues.add("Número sem uso: " + token.lexeme() + ".");
            }
        }
        return subject;
    }

    private static int indexOf(List<String> words, Predicate<String> test) {
        for (int index = 0; index < words.size(); index++) {
            if (test.test(words.get(index))) {
                return index;
            }
        }
        return -1;
    }

    /** A word of the spell: a rune (with what the lexicon says of it), a number or a mark. */
    private record Token(String lexeme, Rune rune, boolean implicit, SpellWords.Kind kind) {

        static Token rune(String lexeme, Rune rune, boolean implicit) {
            return new Token(lexeme, rune, implicit, SpellWords.Kind.RUNE);
        }

        boolean isClass(WordClass wordClass) {
            return rune != null && rune.is(wordClass);
        }

        FilterSpec filter() {
            return rune.filter().orElseThrow();
        }

        VerbSpec verb() {
            return rune.verb().orElseThrow(() -> new IllegalStateException("'" + lexeme + "' has no verb of its own"));
        }

        boolean isValueFilter() {
            return rune != null && rune.filter().map(filter -> filter.argument() == FilterSpec.Argument.VALUE)
                    .orElse(false);
        }
    }

    private static final class SpellForm {
        private VitaElement element;
        private String elementRuneId;
        private final List<String> shapes = new ArrayList<>();

        SpellForm(Lexicon lexicon) {
            Rune fallback = lexicon.defaultSource();
            this.elementRuneId = fallback == null ? VitaElement.BALANCED.runeId() : fallback.id();
            this.element = lexicon.elementOf(elementRuneId);
        }

        void addShape(String shape) {
            if (shape == null || shape.isBlank()) {
                return;
            }
            shapes.add(shape);
        }

        List<String> snapshotShapes() {
            return Collections.unmodifiableList(new ArrayList<>(shapes));
        }

        void setElement(VitaElement element, String runeId) {
            this.element = element == null ? VitaElement.BALANCED : element;
            if (runeId == null || runeId.isBlank()) {
                this.elementRuneId = this.element.runeId();
            } else {
                this.elementRuneId = runeId.trim().toLowerCase(Locale.ROOT);
            }
        }

        VitaElement element() {
            return element;
        }

        String elementRuneId() {
            return elementRuneId;
        }
    }

    /** A verb waiting for the word it takes after it. */
    private final class Pending {
        private final Rune rune;
        private final VerbSpec verb;
        private final ObjectFrame frame;
        private final SpellAction.Builder builder;
        private final VitaElement sourceElement;
        private final String subjectMark;

        Pending(Rune rune, VerbSpec verb, SpellAction.Builder builder, VitaElement sourceElement, String subjectMark) {
            this.rune = rune;
            this.verb = verb;
            this.frame = verb.object();
            this.builder = builder;
            this.sourceElement = sourceElement == null ? VitaElement.BALANCED : sourceElement;
            this.subjectMark = subjectMark;
        }

        SpellAction buildWithSource(Token target, List<VertereRequest> vertereRequests) {
            builder.targetRuneId(target.rune().id());
            SpellAction action = builder.build();
            // A conversion of the caster's own energy converts the Vita; with a marked subject the conversion happens
            // in the marked thing.
            if (verb.flow() == Flow.CONVERT && subjectMark == null && !target.implicit()) {
                VitaElement targetElement = lexicon.elementOf(target.rune().id());
                vertereRequests.add(new VertereRequest(sourceElement, targetElement, 1.0D));
            }
            return action;
        }

        SpellAction buildWithMark(String targetMark) {
            return builder.putMetadata(SpellAction.TARGET_MARK, targetMark).build();
        }

        /** Without its target, a verb that may stand alone with a marked subject meets the caster. */
        SpellAction buildAlone() {
            return builder.build();
        }

        boolean acceptsMark() {
            return frame.takes(ObjectFrame.Kind.MARK) && (!frame.markNeedsMarkedSubject() || subjectMark != null);
        }

        boolean acceptsSource() {
            return frame.takes(ObjectFrame.Kind.SOURCE);
        }

        boolean mayStandAlone() {
            return frame.optionalWithSubject() && subjectMark != null;
        }

        boolean requiresImmediateTarget() {
            return frame.immediate();
        }

        Flow flow() {
            return verb.flow();
        }

        String refusal() {
            return frame.refusal() != null ? frame.refusal() : "age sobre coisas marcadas: escreva uma marca depois dela.";
        }

        String id() {
            return rune.id();
        }
    }
}
