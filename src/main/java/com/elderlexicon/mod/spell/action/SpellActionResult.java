package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.spell.vertere.VertereRequest;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Output payload produced by {@link SpellActionEngine} containing sanitized lexemes, actions and warnings.
 */
public final class SpellActionResult {

    private final List<String> lexemes;
    private final List<SpellAction> actions;
    private final Optional<Parser.PrimarySource> primarySource;
    private final List<String> issues;
    private final List<VertereRequest> vertereRequests;

    SpellActionResult(List<String> lexemes,
                      List<SpellAction> actions,
                      Optional<Parser.PrimarySource> primarySource,
                      List<String> issues,
                      List<VertereRequest> vertereRequests) {
        this.lexemes = List.copyOf(lexemes);
        this.actions = List.copyOf(actions);
        this.primarySource = Objects.requireNonNull(primarySource, "primarySource");
        this.issues = List.copyOf(issues);
        this.vertereRequests = List.copyOf(vertereRequests == null ? List.of() : vertereRequests);
    }

    public List<String> lexemes() {
        return lexemes;
    }

    public List<SpellAction> actions() {
        return actions;
    }

    public Optional<Parser.PrimarySource> primarySource() {
        return primarySource;
    }

    public List<String> issues() {
        return issues;
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public List<VertereRequest> vertereRequests() {
        return Collections.unmodifiableList(vertereRequests);
    }

    static SpellActionResult empty() {
        return new SpellActionResult(List.of(), List.of(), Optional.empty(), List.of(), List.of());
    }
}
