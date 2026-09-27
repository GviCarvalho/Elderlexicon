package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.parser.ParserDictionary.RuneDefinition;
import com.elderlexicon.mod.parser.ParserDictionary.RuneType;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spell.mark.SpellWords;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts rune lexemes into structured {@link SpellAction}s without triggering gameplay logic.
 * <p>
 * Besides runes, a spell may hold numbers and marks (see {@code docs/marcas-como-runas-design.md}):
 * <ul>
 *   <li>a mark right before a function is its subject: {@code m1 vocant} summons what carries m1;</li>
 *   <li>{@code ubis} turns the operand right before it into a place: one number (distance), three
 *       numbers (coordinates) or a mark ({@code m1 ubis igni vocant});</li>
 *   <li>a mark after {@code transvocatio}, or after {@code vertere} with a marked subject, is its target.</li>
 * </ul>
 */
public final class SpellActionEngine {

    private static final String VERTERE_RUNE_ID = "vertere";
    private static final String EXSUGAT_RUNE_ID = "exsugat";
    private static final String TRANSVOCATIO_RUNE_ID = "transvocatio";
    private static final String SURGIT_RUNE_ID = "surgit";
    private static final String UBIS_RUNE_ID = "ubis";
    private static final String QUANTUM_RUNE_ID = "quantum";
    private static final String CHRONOS_RUNE_ID = "chronos";
    private static final String LIGABIS_RUNE_ID = "ligabis";
    private static final String REFRAME_RUNE_ID = "reframe";

    private final ParserDictionary dictionary;

    public SpellActionEngine(ParserDictionary dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
    }

    public SpellActionResult generateActions(List<String> rawLexemes) {
        if (rawLexemes == null || rawLexemes.isEmpty()) {
            return SpellActionResult.empty();
        }

        List<String> sanitized = sanitize(rawLexemes);
        List<String> issues = new ArrayList<>();
        Optional<SpellActionResult> bond = sightBond(sanitized, issues);
        if (bond.isPresent()) {
            return bond.get();
        }
        List<Token> tokens = new ArrayList<>();
        // Ligabis reads its marks from the lexemes itself, and the word after reframe is the name it records.
        boolean ligabisSpell = sanitized.contains(LIGABIS_RUNE_ID);
        int reframeName = sanitized.indexOf(REFRAME_RUNE_ID) + 1;
        for (int index = 0; index < sanitized.size(); index++) {
            String lexeme = sanitized.get(index);
            RuneDefinition definition = dictionary.lookup(lexeme).orElse(null);
            if (definition != null) {
                tokens.add(Token.rune(lexeme, definition, false));
                continue;
            }
            if (reframeName > 0 && index == reframeName) {
                continue;
            }
            SpellWords.Kind kind = SpellWords.classify(lexeme, word -> false);
            if (kind == SpellWords.Kind.MARK || kind == SpellWords.Kind.NUMBER) {
                if (!ligabisSpell) {
                    tokens.add(new Token(lexeme, null, false, kind));
                }
                continue;
            }
            issues.add("Lexema desconhecido: " + lexeme);
        }

        if (tokens.isEmpty()) {
            return new SpellActionResult(sanitized, List.of(), Optional.empty(), issues, List.of());
        }

        ensureLeadingSource(tokens);
        Optional<Parser.PrimarySource> primarySource = determinePrimarySource(tokens);

        List<VertereRequest> vertereRequests = new ArrayList<>();
        List<SpellAction> actions = buildActions(tokens, issues, vertereRequests);
        return new SpellActionResult(sanitized, actions, primarySource, issues, vertereRequests);
    }

    /**
     * {@code surgit m1 ligabis}: surgit written before a mark and bound with ligabis is a bond of sight, the mage seeing
     * through what bears the mark (docs/surgit-visao-design.md, section 2). Ligabis spells lose their marks and numbers
     * before the rest is read, so this one is read here, whole: the first mark after surgit, and chronos for how long.
     */
    private Optional<SpellActionResult> sightBond(List<String> sanitized, List<String> issues) {
        int surgit = sanitized.indexOf(SURGIT_RUNE_ID);
        int ligabis = sanitized.indexOf(LIGABIS_RUNE_ID);
        if (surgit < 0 || ligabis < 0 || surgit > ligabis) {
            return Optional.empty();
        }
        String mark = null;
        for (int index = surgit + 1; index < ligabis && mark == null; index++) {
            String word = sanitized.get(index);
            if (dictionary.lookup(word).isEmpty()
                    && SpellWords.classify(word, known -> false) == SpellWords.Kind.MARK) {
                mark = word;
            }
        }
        if (mark == null) {
            issues.add("O vinculo de visao precisa de uma marca depois do surgit: 'surgit m1 ligabis'.");
            return Optional.of(new SpellActionResult(sanitized, List.of(), Optional.empty(), issues, List.of()));
        }
        Double seconds = null;
        int chronos = sanitized.indexOf(CHRONOS_RUNE_ID);
        if (chronos >= 0 && chronos + 1 < sanitized.size()) {
            java.util.OptionalDouble written = SpellWords.number(sanitized.get(chronos + 1));
            if (written.isPresent()) {
                seconds = chronosSeconds(written.getAsDouble(), issues);
            }
        }
        SpellAction bond = SpellAction.builder(SURGIT_RUNE_ID, SpellActionType.FUNCTION)
                .element(VitaElement.BALANCED)
                .putMetadata("elementRuneId", VitaElement.BALANCED.runeId())
                .putMetadata(SpellAction.SUBJECT_MARK, mark)
                .putMetadata(SpellAction.SIGHT_BOND, Boolean.TRUE)
                .putMetadata(SpellAction.SECONDS, seconds)
                .build();
        return Optional.of(new SpellActionResult(sanitized, List.of(bond), determinePrimarySource(List.of()), issues,
                List.of()));
    }

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

    private void ensureLeadingSource(List<Token> tokens) {
        if (tokens.isEmpty() || tokens.get(0).is(RuneType.SOURCE)) {
            return;
        }
        RuneDefinition fallback = dictionary.lookup(VitaElement.BALANCED.runeId()).orElse(null);
        if (fallback == null) {
            return;
        }
        tokens.add(0, Token.rune(fallback.id(), fallback, true));
    }

    private Optional<Parser.PrimarySource> determinePrimarySource(List<Token> tokens) {
        RuneDefinition fallback = dictionary.lookup(VitaElement.BALANCED.runeId()).orElse(null);
        Parser.PrimarySource fallbackPrimary = fallback != null ? new Parser.PrimarySource(fallback) : null;
        Parser.PrimarySource latestSource = null;
        for (Token token : tokens) {
            if (token.is(RuneType.SOURCE)) {
                latestSource = new Parser.PrimarySource(token.definition());
            }
        }
        if (latestSource != null) {
            return Optional.of(latestSource);
        }
        return Optional.ofNullable(fallbackPrimary);
    }

    private List<SpellAction> buildActions(List<Token> tokens,
                                           List<String> issues,
                                           List<VertereRequest> vertereRequests) {
        List<SpellAction> actions = new ArrayList<>();
        SpellForm form = new SpellForm();
        PendingFunction pendingFunction = null;
        // Numbers and marks written one after the other, waiting for what gives them a role: ubis makes them a place,
        // quantum or chronos takes a number, a function takes the last mark as its subject.
        List<Token> run = new ArrayList<>();
        SpellPlace place = null;
        // quantum and chronos take the number right after them (igni quantum 20 iactare).
        String awaitingNumber = null;
        Double quantity = null;
        // A quantum written before a source measures the source, not the function (quantum 2 firmo surgit).
        Double sourceValue = null;
        Double seconds = null;
        // surgit written right before another function: that function works only with the image of its source.
        boolean imageOnly = false;
        // An exsugat was written: the functions after it work with the source it captures.
        boolean captured = false;
        // A quantum with no number, followed by another filter (quantum chronos 0): all that was captured.
        boolean bareQuantum = false;

        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);

            if (pendingFunction != null
                    && pendingFunction.requiresImmediateTarget()
                    && !token.is(RuneType.SOURCE)
                    && !(token.kind() == SpellWords.Kind.MARK && pendingFunction.acceptsMark())) {
                issues.add("Função '" + pendingFunction.id() + "' requer uma fonte alvo imediatamente após.");
                pendingFunction = null;
            }

            if (token.kind() == SpellWords.Kind.NUMBER) {
                double value = SpellWords.number(token.lexeme()).orElse(0.0D);
                if (QUANTUM_RUNE_ID.equals(awaitingNumber)) {
                    if (value > 0.0D) {
                        quantity = value;
                    } else {
                        issues.add("'quantum' precisa de uma quantidade maior que zero.");
                    }
                    awaitingNumber = null;
                } else if (CHRONOS_RUNE_ID.equals(awaitingNumber)) {
                    seconds = chronosSeconds(value, issues);
                    awaitingNumber = null;
                } else {
                    run.add(token);
                }
                continue;
            }
            if (token.kind() == SpellWords.Kind.MARK) {
                if (pendingFunction != null && pendingFunction.acceptsMark()) {
                    actions.add(pendingFunction.buildWithMark(token.lexeme()));
                    pendingFunction = null;
                    continue;
                }
                run.add(token);
                continue;
            }

            RuneDefinition definition = token.definition();
            switch (definition.type()) {
                case SHAPE -> form.addShape(definition.translation());
                case FILTER -> {
                    if (QUANTUM_RUNE_ID.equals(definition.id()) || CHRONOS_RUNE_ID.equals(definition.id())) {
                        if (QUANTUM_RUNE_ID.equals(awaitingNumber) && CHRONOS_RUNE_ID.equals(definition.id())) {
                            // igni exsugat quantum chronos 0 iactare: all that was captured, released in one instant.
                            bareQuantum = true;
                            awaitingNumber = null;
                        }
                        if (awaitingNumber != null) {
                            issues.add("'" + awaitingNumber + "' requer um número logo depois.");
                            awaitingNumber = null;
                        }
                        // The book writes the number after (quantum 20); a number written just before (20 quantum),
                        // the way ubis takes it, is accepted too.
                        if (!run.isEmpty() && run.get(run.size() - 1).kind() == SpellWords.Kind.NUMBER) {
                            double before = SpellWords.number(run.remove(run.size() - 1).lexeme()).orElse(0.0D);
                            if (QUANTUM_RUNE_ID.equals(definition.id())) {
                                if (before > 0.0D) {
                                    quantity = before;
                                } else {
                                    issues.add("'quantum' precisa de uma quantidade maior que zero.");
                                }
                            } else {
                                seconds = chronosSeconds(before, issues);
                            }
                            break;
                        }
                        awaitingNumber = definition.id();
                        break;
                    }
                    if (!UBIS_RUNE_ID.equals(definition.id())) {
                        break;
                    }
                    SpellPlace written;
                    if (!run.isEmpty()) {
                        // Everything written right before ubis is the place (book: "igni 10 ubis vocant").
                        written = placeOf(run, issues);
                        run.clear();
                    } else {
                        // Numbers written just after (ubis 10) are accepted too, as for quantum and chronos.
                        List<Token> after = new ArrayList<>();
                        while (index + 1 < tokens.size() && tokens.get(index + 1).kind() == SpellWords.Kind.NUMBER) {
                            index++;
                            after.add(tokens.get(index));
                        }
                        if (after.isEmpty()) {
                            issues.add("'ubis' requer um lugar: um número, três números ou uma marca.");
                            written = null;
                        } else {
                            written = placeOf(after, issues);
                        }
                    }
                    if (written != null) {
                        if (place != null) {
                            issues.add("Lugar " + place.describe() + " substituído por " + written.describe() + ".");
                        }
                        place = written;
                    }
                }
                case SOURCE -> {
                    VitaElement element = VitaElement.fromRuneId(definition.id());
                    if (pendingFunction != null) {
                        if (!pendingFunction.acceptsSource()) {
                            issues.add("Função '" + pendingFunction.id() + "' troca coisas marcadas: escreva uma marca depois dela.");
                        } else if (pendingFunction.requiresExplicitTarget() && token.implicit()) {
                            issues.add("Função '" + pendingFunction.id() + "' requer uma fonte alvo declarada.");
                        } else {
                            actions.add(pendingFunction.buildWithSource(token, vertereRequests));
                        }
                        pendingFunction = null;
                    }
                    for (Token unused : run) {
                        if (unused.kind() == SpellWords.Kind.MARK) {
                            issues.add("Marca '" + unused.lexeme() + "' sem uso: escreva '" + unused.lexeme()
                                    + " ubis' para um lugar, ou a marca logo antes da função.");
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
                        SpellAction sourceAction = SpellAction.builder(definition.id(), SpellActionType.SOURCE)
                                .element(element)
                                .shapes(form.snapshotShapes())
                                .putMetadata("elementRuneId", definition.id())
                                .build();
                        actions.add(sourceAction);
                    }
                    form.setElement(element, definition.id());
                }
                case FUNCTION -> {
                    if (SURGIT_RUNE_ID.equals(definition.id()) && index + 1 < tokens.size()
                            && tokens.get(index + 1).is(RuneType.FUNCTION)) {
                        // igni surgit vocant: surgit filters the source down to its light; the mark, place and filters
                        // written so far are left for the function that follows (docs/surgit-visao-design.md, 4).
                        imageOnly = true;
                        break;
                    }
                    // A quantum with no number is all of the source there is (book 4.3.2): after an exsugat, all of it
                    // in reach (igni exsugat quantum iactare); without one, all of it in the mage's own body (firmo
                    // quantum chronos 0 iactare: all the body's earth; vis quantum …: all its mana).
                    boolean all = QUANTUM_RUNE_ID.equals(awaitingNumber) || bareQuantum;
                    bareQuantum = false;
                    if (all && QUANTUM_RUNE_ID.equals(awaitingNumber)) {
                        awaitingNumber = null;
                    }
                    String mark = subjectOf(run, issues);
                    run.clear();
                    // surgit also reads the mark written right after it ("surgit r2", read r2), like transvocatio's target.
                    Double visibility = null;
                    if (mark == null && SURGIT_RUNE_ID.equals(definition.id())) {
                        if (index + 1 < tokens.size() && tokens.get(index + 1).kind() == SpellWords.Kind.MARK) {
                            index++;
                            mark = tokens.get(index).lexeme();
                        }
                        // Written before its subject, surgit is "the sight of" it, and a quantum after sets how much of
                        // it is seen: surgit m1 quantum 0 hides m1; with no mark (surgit quantum 0) the subject is what
                        // the mage aims at (docs/surgit-visao-design.md, section 5).
                        while (index + 2 < tokens.size() && tokens.get(index + 1).kind() == SpellWords.Kind.RUNE
                                && tokens.get(index + 2).kind() == SpellWords.Kind.NUMBER
                                && (QUANTUM_RUNE_ID.equals(tokens.get(index + 1).definition().id())
                                || CHRONOS_RUNE_ID.equals(tokens.get(index + 1).definition().id()))) {
                            double value = SpellWords.number(tokens.get(index + 2).lexeme()).orElse(0.0D);
                            if (QUANTUM_RUNE_ID.equals(tokens.get(index + 1).definition().id())) {
                                visibility = value;
                            } else {
                                seconds = chronosSeconds(value, issues);
                            }
                            index += 2;
                        }
                    }
                    SpellAction.Builder builder = SpellAction.builder(definition.id(), SpellActionType.FUNCTION)
                            .element(form.element())
                            .shapes(form.snapshotShapes())
                            .putMetadata("elementRuneId", form.elementRuneId())
                            .putMetadata(SpellAction.SUBJECT_MARK, mark)
                            .putMetadata(SpellAction.PLACE, place)
                            .putMetadata(SpellAction.QUANTITY, quantity)
                            .putMetadata(SpellAction.SOURCE_QUANTITY, sourceValue)
                            .putMetadata(SpellAction.SECONDS, seconds)
                            .putMetadata(SpellAction.VISIBILITY, visibility)
                            .putMetadata(SpellAction.IMAGE, imageOnly ? Boolean.TRUE : null)
                            .putMetadata(SpellAction.QUANTITY_ALL, all ? Boolean.TRUE : null);
                    if (EXSUGAT_RUNE_ID.equals(definition.id()) && mark == null && !imageOnly) {
                        captured = true;
                    }
                    imageOnly = false;
                    String subject = mark;
                    place = null;
                    quantity = null;
                    sourceValue = null;
                    seconds = null;
                    if (awaitingNumber != null) {
                        issues.add("'" + awaitingNumber + "' requer um número logo depois.");
                        awaitingNumber = null;
                    }
                    // Vertere converts the source in force: the last one written (igni exsugat vertere aqua converts the
                    // captured fire) or, with none, mana (book 4.2: "o espírito preenche essa lacuna com mana").
                    boolean isVertere = VERTERE_RUNE_ID.equals(definition.id());
                    if (definition.requiresTarget()) {
                        if (pendingFunction != null) {
                            issues.add("Função pendente '" + pendingFunction.id() + "' substituída por '" + definition.id() + "'.");
                        }
                        pendingFunction = new PendingFunction(definition, builder, form.element(), isVertere, subject);
                    } else {
                        actions.add(builder.build());
                    }
                }
                default -> {
                }
            }
        }

        if (pendingFunction != null) {
            if (pendingFunction.mayStandAlone()) {
                actions.add(pendingFunction.buildAlone());
            } else {
                issues.add("Função '" + pendingFunction.id() + "' requer uma fonte seguinte.");
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
        if (awaitingNumber != null) {
            issues.add("'" + awaitingNumber + "' requer um número logo depois.");
        } else if (quantity != null || sourceValue != null || seconds != null) {
            issues.add("Filtro sem função depois dele.");
        }
        return actions;
    }

    /** Negative time is "matter for another volume" (book 4.3.2): refused, so the function keeps its own timing. */
    private static Double chronosSeconds(double value, List<String> issues) {
        if (value < 0.0D) {
            issues.add("'chronos' negativo ainda nao e compreendido (materia para outro volume).");
            return null;
        }
        return value;
    }

    /**
     * The place written before ubis: one number is a distance, two are a distance and a height (above the mage),
     * three are coordinates where any of them may be a mark (that coordinate of where the mark is), and a lone
     * mark is where the mark is.
     */
    private static SpellPlace placeOf(List<Token> written, List<String> issues) {
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
            issues.add("'ubis' com dois valores quer dois números: distância e altura.");
            return null;
        }
        if (written.size() == 3) {
            return SpellPlace.coordinates(axisOf(written.get(0)), axisOf(written.get(1)), axisOf(written.get(2)));
        }
        issues.add("'ubis' aceita um, dois ou três valores antes dele.");
        return null;
    }

    private static SpellPlace.Axis axisOf(Token token) {
        return token.kind() == SpellWords.Kind.MARK
                ? SpellPlace.Axis.mark(token.lexeme())
                : SpellPlace.Axis.of(SpellWords.number(token.lexeme()).orElse(0.0D));
    }

    /** The last mark written before a function is its subject; anything else left there is unused. */
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

    /** A word of the spell: a rune (with its definition), a number or a mark. */
    private record Token(String lexeme, RuneDefinition definition, boolean implicit, SpellWords.Kind kind) {

        static Token rune(String lexeme, RuneDefinition definition, boolean implicit) {
            return new Token(lexeme, definition, implicit, SpellWords.Kind.RUNE);
        }

        boolean is(RuneType type) {
            return definition != null && definition.type() == type;
        }
    }

    private static final class SpellForm {
        private VitaElement element = VitaElement.BALANCED;
        private String elementRuneId = VitaElement.BALANCED.runeId();
        private final List<String> shapes = new ArrayList<>();

        void addShape(String translation) {
            if (translation == null || translation.isBlank()) {
                return;
            }
            shapes.add(translation);
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

    private static final class PendingFunction {
        private final RuneDefinition definition;
        private final SpellAction.Builder builder;
        private final VitaElement sourceElement;
        private final boolean requiresImmediateTarget;
        private final String subjectMark;

        PendingFunction(RuneDefinition definition,
                        SpellAction.Builder builder,
                        VitaElement sourceElement,
                        boolean requiresImmediateTarget,
                        String subjectMark) {
            this.definition = definition;
            this.builder = builder;
            this.sourceElement = sourceElement == null ? VitaElement.BALANCED : sourceElement;
            this.requiresImmediateTarget = requiresImmediateTarget;
            this.subjectMark = subjectMark;
        }

        SpellAction buildWithSource(Token targetRune,
                                    List<VertereRequest> vertereRequests) {
            builder.targetRuneId(targetRune.definition().id());
            SpellAction action = builder.build();
            // With a marked subject the conversion happens in the marked thing, never in the caster's Vita.
            if (requiresImmediateTarget && VERTERE_RUNE_ID.equals(definition.id())
                    && subjectMark == null && !targetRune.implicit()) {
                VitaElement targetElement = VitaElement.fromRuneId(targetRune.definition().id());
                vertereRequests.add(new VertereRequest(sourceElement, targetElement, 1.0D));
            }
            return action;
        }

        SpellAction buildWithMark(String targetMark) {
            return builder.putMetadata(SpellAction.TARGET_MARK, targetMark).build();
        }

        /** Transvocatio without a target swaps its subject with the caster. */
        SpellAction buildAlone() {
            return builder.build();
        }

        boolean acceptsMark() {
            return TRANSVOCATIO_RUNE_ID.equals(id()) || (VERTERE_RUNE_ID.equals(id()) && subjectMark != null);
        }

        boolean acceptsSource() {
            return !TRANSVOCATIO_RUNE_ID.equals(id());
        }

        boolean mayStandAlone() {
            return TRANSVOCATIO_RUNE_ID.equals(id()) && subjectMark != null;
        }

        String id() {
            return definition.id();
        }

        boolean requiresImmediateTarget() {
            return requiresImmediateTarget;
        }

        boolean requiresExplicitTarget() {
            return requiresImmediateTarget;
        }
    }
}
