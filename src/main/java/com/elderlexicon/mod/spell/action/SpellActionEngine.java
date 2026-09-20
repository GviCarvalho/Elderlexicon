package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.parser.ParserDictionary.RuneDefinition;
import com.elderlexicon.mod.parser.ParserDictionary.RuneType;
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
 */
public final class SpellActionEngine {

    private static final String VERTERE_RUNE_ID = "vertere";

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
        List<RuneToken> runes = new ArrayList<>();
        for (String lexeme : sanitized) {
            RuneDefinition definition = dictionary.lookup(lexeme).orElse(null);
            if (definition == null) {
                issues.add("Lexema desconhecido: " + lexeme);
                continue;
            }
            runes.add(new RuneToken(lexeme, definition, false));
        }

        if (runes.isEmpty()) {
            return new SpellActionResult(sanitized, List.of(), Optional.empty(), issues, List.of());
        }

        ensureLeadingSource(runes);
        Optional<Parser.PrimarySource> primarySource = determinePrimarySource(runes);

        List<VertereRequest> vertereRequests = new ArrayList<>();
        List<SpellAction> actions = buildActions(runes, issues, vertereRequests);
        return new SpellActionResult(sanitized, actions, primarySource, issues, vertereRequests);
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

    private void ensureLeadingSource(List<RuneToken> runes) {
        if (runes.isEmpty()) {
            return;
        }
        RuneToken head = runes.get(0);
        if (head.definition().type() == RuneType.SOURCE) {
            return;
        }
        RuneDefinition fallback = dictionary.lookup(VitaElement.BALANCED.runeId()).orElse(null);
        if (fallback == null) {
            return;
        }
        runes.add(0, new RuneToken(fallback.id(), fallback, true));
    }

    private Optional<Parser.PrimarySource> determinePrimarySource(List<RuneToken> runes) {
        RuneDefinition fallback = dictionary.lookup(VitaElement.BALANCED.runeId()).orElse(null);
        Parser.PrimarySource fallbackPrimary = fallback != null ? new Parser.PrimarySource(fallback) : null;
        Parser.PrimarySource latestSource = null;
        for (RuneToken rune : runes) {
            RuneDefinition definition = rune.definition();
            RuneType type = definition.type();
            if (type == RuneType.SOURCE) {
                latestSource = new Parser.PrimarySource(definition);
            }
        }
        if (latestSource != null) {
            return Optional.of(latestSource);
        }
        return Optional.ofNullable(fallbackPrimary);
    }

    private List<SpellAction> buildActions(List<RuneToken> runes,
                                           List<String> issues,
                                           List<VertereRequest> vertereRequests) {
        List<SpellAction> actions = new ArrayList<>();
        SpellForm form = new SpellForm();
        PendingFunction pendingFunction = null;

        for (int index = 0; index < runes.size(); index++) {
            RuneToken rune = runes.get(index);
            RuneDefinition definition = rune.definition();

            if (pendingFunction != null
                    && pendingFunction.requiresImmediateTarget()
                    && definition.type() != RuneType.SOURCE) {
                issues.add("Função '" + pendingFunction.id() + "' requer uma fonte alvo imediatamente após.");
                pendingFunction = null;
            }

            switch (definition.type()) {
                case SHAPE -> form.addShape(definition.translation());
                case SOURCE -> {
                    VitaElement element = VitaElement.fromRuneId(definition.id());
                    if (pendingFunction != null) {
                        if (pendingFunction.requiresExplicitTarget() && rune.implicit()) {
                            issues.add("Função '" + pendingFunction.id() + "' requer uma fonte alvo declarada.");
                        } else {
                            SpellAction functionAction = pendingFunction.build(rune, vertereRequests);
                            actions.add(functionAction);
                        }
                        pendingFunction = null;
                    }
                    if (!rune.implicit()) {
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
                    SpellAction.Builder builder = SpellAction.builder(definition.id(), SpellActionType.FUNCTION)
                            .element(form.element())
                            .shapes(form.snapshotShapes())
                            .putMetadata("elementRuneId", form.elementRuneId());
                    boolean isVertere = VERTERE_RUNE_ID.equals(definition.id());
                    if (isVertere && !hasImmediateLeadingSource(runes, index)) {
                        issues.add("Função 'vertere' requer uma fonte imediatamente antes.");
                        continue;
                    }
                    if (definition.requiresTarget()) {
                        if (pendingFunction != null) {
                            issues.add("Função pendente '" + pendingFunction.id() + "' substituída por '" + definition.id() + "'.");
                        }
                        pendingFunction = new PendingFunction(definition, builder, form.element(), isVertere);
                    } else {
                        actions.add(builder.build());
                    }
                }
                default -> {
                }
            }
        }

        if (pendingFunction != null) {
            issues.add("Função '" + pendingFunction.id() + "' requer uma fonte seguinte.");
        }
        return actions;
    }

    private boolean hasImmediateLeadingSource(List<RuneToken> runes, int index) {
        if (index <= 0) {
            return false;
        }
        RuneToken previous = runes.get(index - 1);
        return previous.definition().type() == RuneType.SOURCE && !previous.implicit();
    }

    private record RuneToken(String lexeme, RuneDefinition definition, boolean implicit) { }

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

        PendingFunction(RuneDefinition definition,
                        SpellAction.Builder builder,
                        VitaElement sourceElement,
                        boolean requiresImmediateTarget) {
            this.definition = definition;
            this.builder = builder;
            this.sourceElement = sourceElement == null ? VitaElement.BALANCED : sourceElement;
            this.requiresImmediateTarget = requiresImmediateTarget;
        }

        SpellAction build(RuneToken targetRune,
                          List<VertereRequest> vertereRequests) {
            builder.targetRuneId(targetRune.definition().id());
            SpellAction action = builder.build();
            if (requiresImmediateTarget && VERTERE_RUNE_ID.equals(definition.id()) && !targetRune.implicit()) {
                VitaElement targetElement = VitaElement.fromRuneId(targetRune.definition().id());
                vertereRequests.add(new VertereRequest(sourceElement, targetElement, 1.0D));
            }
            return action;
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
