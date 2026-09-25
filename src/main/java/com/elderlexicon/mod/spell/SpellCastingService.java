package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.parser.SpellTranscript;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionEngine;
import com.elderlexicon.mod.spell.action.SpellActionExecutor;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.action.SpellCostProcessor;
import com.elderlexicon.mod.spell.registry.SpellModuleRegistry;
import com.elderlexicon.mod.spell.scene.WorldScenes;
import com.elderlexicon.mod.spelling.item.SpellConduitItem;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaScoreboardManager;
import com.elderlexicon.mod.vita.VitaSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Shared service that converts rune lexemes into resolved spells and executes them.
 */
public final class SpellCastingService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double EPSILON = 1.0E-4D;
    /** Server ticks per block position (matches the Iactare channel step). */
    public static final int STEP_TICKS = 5;

    private final SpellActionEngine actionEngine;
    private final SpellActionExecutor actionExecutor;
    private final SpellCostProcessor costProcessor;

    private Parser parser;

    public SpellCastingService() {
        this(new SpellActionEngine(ParserDictionary.load()), new SpellActionExecutor(), new SpellCostProcessor());
    }

    public SpellCastingService(SpellActionEngine actionEngine,
                               SpellActionExecutor actionExecutor,
                               SpellCostProcessor costProcessor) {
        this.actionEngine = Objects.requireNonNull(actionEngine, "actionEngine");
        this.actionExecutor = Objects.requireNonNull(actionExecutor, "actionExecutor");
        this.costProcessor = Objects.requireNonNull(costProcessor, "costProcessor");
    }

    public Result cast(ServerPlayer player, List<String> rawLexemes) {
        Objects.requireNonNull(player, "player");
        List<String> lexemes = sanitizeLexemes(rawLexemes);
        if (lexemes.isEmpty()) {
            return Result.failure(Component.literal("Spell requer ao menos um termo."));
        }

        Parser resolvedParser = ensureParser();
        if (resolvedParser == null) {
            return Result.failure(Component.literal("Parser indisponivel: consulte os logs."));
        }

        SpellActionResult actionResult = actionEngine.generateActions(lexemes);
        List<SpellAction> actions = actionResult.actions();
        if (actions.isEmpty()) {
            String failureMessage = actionResult.hasIssues()
                    ? "Nao foi possivel interpretar o feitico: " + String.join(", ", actionResult.issues())
                    : "Nao foi possivel interpretar o feitico.";
            return Result.failure(Component.literal(failureMessage == null ? "" : failureMessage));
        }

        Optional<Parser.PrimarySource> primarySource = actionResult.primarySource();
        if (primarySource.isEmpty()) {
            primarySource = resolvedParser.findPrimarySource(lexemes);
        }

        SpellTranscript transcript = resolvedParser.transcribeActions(actions, lexemes, primarySource);
        if (!transcript.success()) {
            String failureMessage = transcript.message() == null || transcript.message().isBlank()
                    ? "Nao foi possivel transcrever o feitico."
                    : transcript.message();
            return Result.failure(Component.literal(failureMessage));
        }

        Optional<Parser.PrimarySource> transcriptPrimary = transcript.primarySource()
                .flatMap(id -> resolvedParser.lookup(id).map(Parser.PrimarySource::new));
        if (transcriptPrimary.isPresent()) {
            primarySource = transcriptPrimary;
        }

        List<Component> warnings = new ArrayList<>();
        if (actionResult.hasIssues()) {
            LOGGER.warn("Spell action issues: {}", actionResult.issues());
            warnings.add(Component.literal("Aviso de feitico: " + String.join(", ", actionResult.issues())));
        }

        double totalCost = requiresEnergyConsumption(actions)
                ? costProcessor.computeTotalCost(actions)
                : 0.0D;
        VitaElement primaryElement = primarySource
                .map(primary -> VitaElement.fromRuneId(primary.definition().id()))
                .orElse(VitaElement.BALANCED);
        String primaryLabel = primarySource
                .map(primary -> capitalize(primary.definition().translation()))
                .orElse(capitalize(primaryElement.runeId()));

        SpellContext context = new SpellContext(
                player,
                lexemes,
                primarySource,
                primaryElement,
                totalCost,
                actions,
                actionResult.vertereRequests());
        // Every spell of the world shares one scene, so spells interact whoever cast them and whenever.
        context.attachScene(WorldScenes.sceneOf(player.serverLevel()));
        context.setFocusActive(SpellConduitItem.holdsReadyConduit(player));

        actionExecutor.execute(context, actions);
        UmuLeakHandler.handleLeaks(context, actions);
        SpellModuleRegistry.snapshot().forEach(module -> module.apply(context));
        context.commitAmbientEnergy();

        double environmentalContribution = context.environmentalContribution();
        double payableCost = context.payableCost();
        double bodyLoad = Math.max(0.0D, context.conduitOverflow());

        String response = transcript.message() == null || transcript.message().isBlank()
                ? "Feitico interpretado com sucesso."
                : transcript.message();

        if (totalCost > EPSILON || environmentalContribution > EPSILON) {
            StringBuilder descriptor = new StringBuilder("Fonte: ")
                    .append(primaryLabel)
                    .append(" | Custo: ")
                    .append(SpellCostCalculator.formatCost(payableCost))
                    .append(" UMU");
            if (environmentalContribution > EPSILON) {
                descriptor.append(" | Ambiente: ")
                        .append(SpellCostCalculator.formatCost(environmentalContribution))
                        .append(" UMU");
            }
            response = response + " (" + descriptor + ")";
        }

        Component outputComponent = Component.literal(response == null ? "" : response);
        VitaScoreboardManager.update(player, VitaSystem.fromPlayer(player));
        // Pass actual UMU spent and UMU extra to Result
        return Result.success(outputComponent, warnings, payableCost, environmentalContribution, bodyLoad, primaryElement,
                context.focusActive());
    }

    /** One independent spell of a block, released {@code delaySteps} after the earliest one. */
    public record TimedSpell(List<String> lexemes, int delaySteps) {

        public TimedSpell {
            lexemes = lexemes == null ? List.of() : List.copyOf(lexemes);
            delaySteps = Math.max(0, delaySteps);
        }
    }

    /**
     * Casts several spells that the spirit read at once. Each spell runs on its own; the earliest
     * ones start immediately and the rest start after their delay (one step = {@link #STEP_TICKS}).
     * All spells are validated before any of them runs, so a malformed line cancels the whole block.
     *
     * @param delayedSink receives the result of every spell that starts later than the returned one
     */
    public Result castBlock(ServerPlayer player, List<TimedSpell> spells, Consumer<Result> delayedSink) {
        Objects.requireNonNull(player, "player");
        if (spells == null || spells.isEmpty()) {
            return Result.failure(Component.literal("Spell requer ao menos um termo."));
        }
        if (spells.size() == 1) {
            return cast(player, spells.get(0).lexemes());
        }

        for (int index = 0; index < spells.size(); index++) {
            List<String> lexemes = sanitizeLexemes(spells.get(index).lexemes());
            if (lexemes.isEmpty() || actionEngine.generateActions(lexemes).actions().isEmpty()) {
                return Result.failure(Component.literal("Linha " + (index + 1) + " do bloco nao pode ser interpretada."));
            }
        }

        int baseDelay = spells.stream().mapToInt(TimedSpell::delaySteps).min().orElse(0);
        List<Result> immediate = new ArrayList<>();
        for (TimedSpell spell : spells) {
            int delayTicks = (spell.delaySteps() - baseDelay) * STEP_TICKS;
            if (delayTicks <= 0) {
                immediate.add(cast(player, spell.lexemes()));
                continue;
            }
            scheduleLater(player, delayTicks, () -> {
                if (player.isRemoved() || !player.isAlive()) {
                    return;
                }
                try {
                    Result result = cast(player, spell.lexemes());
                    if (delayedSink != null) {
                        delayedSink.accept(result);
                    }
                } catch (RuntimeException exception) {
                    LOGGER.error("Delayed block spell failed", exception);
                }
            });
        }
        return mergeSimultaneous(immediate, spells.size());
    }

    private static void scheduleLater(ServerPlayer player, int delayTicks, Runnable action) {
        SpellTicks.schedule(player.server, delayTicks, action);
    }

    private static Result mergeSimultaneous(List<Result> results, int totalSpells) {
        List<Component> warnings = new ArrayList<>();
        Result firstSuccess = null;
        Result firstFailure = null;
        double spent = 0.0D;
        double extra = 0.0D;
        double bodyLoad = 0.0D;
        boolean focusActive = false;
        for (Result result : results) {
            warnings.addAll(result.warnings());
            if (result.failed()) {
                if (firstFailure == null) {
                    firstFailure = result;
                } else {
                    warnings.add(Component.literal("Linha ignorada: ").append(result.message()));
                }
                continue;
            }
            if (firstSuccess == null) {
                firstSuccess = result;
            }
            spent += result.umuSpent();
            extra += result.umuExtra();
            bodyLoad += result.bodyLoad();
            focusActive |= result.focusActive();
        }
        if (firstSuccess == null) {
            return new Result(false, firstFailure.message(), warnings, 0.0D, 0.0D, 0.0D, VitaElement.BALANCED);
        }
        if (firstFailure != null) {
            warnings.add(Component.literal("Linha ignorada: ").append(firstFailure.message()));
        }
        warnings.add(Component.literal("Feiticos simultaneos: " + totalSpells + "."));
        return Result.success(firstSuccess.message(), warnings, spent, extra, bodyLoad, firstSuccess.primaryElement(), focusActive);
    }

    private Parser ensureParser() {
        if (parser != null) {
            return parser;
        }
        try {
            parser = new Parser();
            return parser;
        } catch (IllegalStateException exception) {
            LOGGER.error("Unable to initialize parser", exception);
            return null;
        }
    }

    private List<String> sanitizeLexemes(List<String> lexemes) {
        if (lexemes == null || lexemes.isEmpty()) {
            return List.of();
        }
        return lexemes.stream()
                .filter(token -> token != null && !token.isBlank())
                .map(token -> token.trim())
                .toList();
    }

    private static boolean requiresEnergyConsumption(List<SpellAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        for (SpellAction action : actions) {
            if (action == null) {
                continue;
            }
            if (action.type() == com.elderlexicon.mod.spell.action.SpellActionType.FUNCTION
                    && !"exsugat".equalsIgnoreCase(action.runeId())) {
                return true;
            }
        }
        return false;
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    /**
     * Immutable result returned to callers.
     */
    public record Result(boolean success,
                         Component message,
                         List<Component> warnings,
                         double umuSpent,
                         double umuExtra,
                         double bodyLoad,
                         VitaElement primaryElement,
                         boolean focusActive) {

        public Result(boolean success,
                      Component message,
                      List<Component> warnings,
                      double umuSpent,
                      double umuExtra,
                      double bodyLoad,
                      VitaElement primaryElement) {
            this(success, message, warnings, umuSpent, umuExtra, bodyLoad, primaryElement, false);
        }

        public Result(boolean success,
                      Component message,
                      List<Component> warnings,
                      double umuSpent,
                      double umuExtra,
                      double bodyLoad,
                      VitaElement primaryElement,
                      boolean focusActive) {
            this.focusActive = focusActive;
            this.success = success;
            this.message = Objects.requireNonNull(message, "message");
            this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
            this.umuSpent = umuSpent;
            this.umuExtra = umuExtra;
            this.bodyLoad = Math.max(0.0D, bodyLoad);
            this.primaryElement = primaryElement == null ? VitaElement.BALANCED : primaryElement;
        }

        public static Result success(Component message,
                                     List<Component> warnings,
                                     double umuSpent,
                                     double umuExtra,
                                     double bodyLoad,
                                     VitaElement primaryElement) {
            return success(message, warnings, umuSpent, umuExtra, bodyLoad, primaryElement, false);
        }

        public static Result success(Component message,
                                     List<Component> warnings,
                                     double umuSpent,
                                     double umuExtra,
                                     double bodyLoad,
                                     VitaElement primaryElement,
                                     boolean focusActive) {
            return new Result(true, message, warnings == null ? List.of() : warnings, umuSpent, umuExtra, bodyLoad, primaryElement, focusActive);
        }

        public static Result failure(Component message) {
            return new Result(false, message, List.of(), 0.0, 0.0, 0.0, VitaElement.BALANCED);
        }

        public boolean failed() {
            return !success;
        }
    }
}
