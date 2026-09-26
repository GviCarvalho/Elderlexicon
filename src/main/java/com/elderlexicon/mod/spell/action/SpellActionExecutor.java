package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.function.ExsugatFunctionHandler;
import com.elderlexicon.mod.spell.function.SpellFunctionHandler;
import com.elderlexicon.mod.spell.registry.SpellFunctionHandlerRegistry;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Applies {@link SpellAction}s to the existing function handlers without consulting the parser.
 */
public final class SpellActionExecutor {

    private static final String VERTERE_RUNE_ID = "vertere";
    private static final String EXSUGAT_RUNE_ID = "exsugat";
    private static final double EPSILON = 1.0E-4D;

    private final Function<String, SpellFunctionHandler> handlerResolver;
    private final VertereGateway vertereGateway;

    public SpellActionExecutor() {
        this(runeId -> SpellFunctionHandlerRegistry.find(runeId).orElse(null), new VitaVertereGateway());
    }

    public SpellActionExecutor(Map<String, SpellFunctionHandler> handlers) {
        this(handlers, new VitaVertereGateway());
    }

    public SpellActionExecutor(Map<String, SpellFunctionHandler> handlers, VertereGateway vertereGateway) {
        Map<String, SpellFunctionHandler> copy = Map.copyOf(handlers);
        this.handlerResolver = runeId -> {
            if (runeId == null || runeId.isBlank()) {
                return null;
            }
            return copy.get(runeId.toLowerCase(Locale.ROOT));
        };
        this.vertereGateway = vertereGateway;
    }

    private SpellActionExecutor(Function<String, SpellFunctionHandler> handlerResolver,
                                VertereGateway vertereGateway) {
        this.handlerResolver = handlerResolver;
        this.vertereGateway = vertereGateway;
    }

    public void execute(SpellContext context, List<SpellAction> actions) {
        if (context == null || actions == null || actions.isEmpty()) {
            return;
        }

        VitaElement currentElement = context.primarySource()
                .map(primary -> VitaElement.fromRuneId(primary.definition().id()))
                .orElse(context.primaryElement());
        String currentRuneId = context.elementRuneId();
        List<VertereRequest> vertereQueue = context.vertereRequests();
        int vertereIndex = 0;

        // A source captured by exsugat (book 8.2.1), with what it has been converted into since.
        VitaElement captured = null;
        VitaElement capturedAs = null;
        SpellAction capture = null;
        boolean capturedForSpell = false;

        for (int index = 0; index < actions.size(); index++) {
            SpellAction action = actions.get(index);
            if (action == null) {
                continue;
            }
            if (action.type() == SpellActionType.SOURCE && action.element() != null) {
                currentElement = action.element();
                currentRuneId = resolveElementRuneId(action, action.runeId(), currentRuneId);
                context.setElementRuneId(currentRuneId);
                continue;
            }
            if (action.type() != SpellActionType.FUNCTION) {
                continue;
            }
            context.setElementRuneId(resolveElementRuneId(action, currentRuneId, currentRuneId));
            context.setCurrentAction(action);
            // igni surgit vertere aqua converts only the look of the fire nearby: nothing is taken from the Vita.
            if (isVertere(action.runeId()) && action.image()) {
                if (vertereIndex < vertereQueue.size()) {
                    vertereIndex++;
                }
                if (context.player() != null) {
                    com.elderlexicon.mod.spell.function.ImageSpells.disguise(context, action);
                }
                continue;
            }
            // igni exsugat iactare: exsugat does not pull at once; it captures what the functions after it spend, which is
            // only known once they have run. Written last, it pulls into the body, as its handler does.
            if (is(EXSUGAT_RUNE_ID, action.runeId()) && !action.image() && action.subjectMark().isEmpty()
                    && hasFunctionAfter(actions, index)) {
                captured = currentElement;
                capturedAs = currentElement;
                capture = action;
                capturedForSpell = hasConsumerAfter(actions, index);
                continue;
            }
            // igni exsugat vertere aqua converts the fire being pulled, not the mage's own: the Vita is left alone.
            if (isVertere(action.runeId()) && action.subjectMark().isEmpty() && captured != null) {
                if (vertereIndex < vertereQueue.size()) {
                    VitaElement target = vertereQueue.get(vertereIndex++).target();
                    capturedAs = target;
                    currentElement = target;
                    context.setPrimaryElement(target);
                }
                continue;
            }
            // Vertere on a marked thing converts its matter; its handler does that, not the caster's Vita.
            if (isVertere(action.runeId()) && action.subjectMark().isEmpty()) {
                if (vertereIndex < vertereQueue.size()) {
                    VertereRequest written = vertereQueue.get(vertereIndex++);
                    // igni quantum 5 vertere aqua converts five UMU of the body's fire.
                    VertereRequest request = new VertereRequest(written.source(), written.target(),
                            action.quantity().orElse(written.amount()));
                    VitaElement updated = handleVertere(context, request);
                    if (updated != null) {
                        currentElement = updated;
                    }
                }
                continue;
            }
            if (captured != null && capturedForSpell && action.quantityAll() && context.player() != null) {
                // igni exsugat quantum iactare: everything in reach is pulled now and spent by this function at once.
                double total = ExsugatFunctionHandler.captureAll(context, captured, capturedAs);
                action = action.toBuilder().putMetadata(SpellAction.QUANTITY, total > EPSILON ? total : null).build();
                context.setCurrentAction(action);
            }
            SpellFunctionHandler handler = resolveHandler(action.runeId());
            if (handler != null) {
                handler.execute(context, currentElement);
            }
        }

        if (captured != null && context.player() != null) {
            context.setCurrentAction(capture);
            if (capturedForSpell) {
                ExsugatFunctionHandler.capture(context, captured, capturedAs);
            } else {
                // Converted and never spent (firmo exsugat vertere igni): the portion captured becomes the other element
                // where it is, in the world.
                ExsugatFunctionHandler.convertInPlace(context, captured, capturedAs,
                        capture.quantity().orElse(ExsugatFunctionHandler.DEFAULT_ABSORBED_UMU));
            }
        }
    }

    private static boolean hasFunctionAfter(List<SpellAction> actions, int index) {
        for (int next = index + 1; next < actions.size(); next++) {
            if (actions.get(next) != null && actions.get(next).type() == SpellActionType.FUNCTION) {
                return true;
            }
        }
        return false;
    }

    /** Whether a function after {@code index} spends the source; a vertere only converts it. */
    private boolean hasConsumerAfter(List<SpellAction> actions, int index) {
        for (int next = index + 1; next < actions.size(); next++) {
            SpellAction later = actions.get(next);
            if (later != null && later.type() == SpellActionType.FUNCTION && !isVertere(later.runeId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean is(String runeId, String candidate) {
        return candidate != null && runeId.equalsIgnoreCase(candidate.trim());
    }

    private String resolveElementRuneId(SpellAction action, String preferred, String fallback) {
        if (action != null && action.metadata() != null) {
            Object candidate = action.metadata().get("elementRuneId");
            if (candidate instanceof String runeId && !runeId.isBlank()) {
                return runeId.trim().toLowerCase(Locale.ROOT);
            }
        }
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim().toLowerCase(Locale.ROOT);
        }
        return fallback == null ? null : fallback.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isVertere(String runeId) {
        return runeId != null && VERTERE_RUNE_ID.equalsIgnoreCase(runeId.trim());
    }

    private VitaElement handleVertere(SpellContext context, VertereRequest request) {
        // With a focus the conversion happens in the focus, so the caster's Vita is left alone.
        double transferred = context.focusActive() ? request.amount() : vertereGateway.transfer(context, request);
        if (transferred <= EPSILON) {
            return null;
        }
        context.setPrimaryElement(request.target());
        context.addTotalCost(transferred);
        return request.target();
    }

    private SpellFunctionHandler resolveHandler(String runeId) {
        if (runeId == null) {
            return null;
        }
        SpellFunctionHandler handler = handlerResolver.apply(runeId.toLowerCase(Locale.ROOT));
        if (handler == null) {
            handler = handlerResolver.apply(runeId);
        }
        return handler;
    }

    interface VertereGateway {
        double transfer(SpellContext context, VertereRequest request);
    }

    private static final class VitaVertereGateway implements VertereGateway {

        @Override
        public double transfer(SpellContext context, VertereRequest request) {
            if (context == null || request == null) {
                return 0.0D;
            }
            ServerPlayer player = context.player();
            VitaSystem.ElementTransferResult result = VitaSystem.transferElement(
                    player,
                    request.source(),
                    request.target(),
                    request.amount(),
                    true);
            if (!result.succeeded()) {
                sendMessage(player, Component.literal("Vertere falhou: fonte insuficiente."));
                return 0.0D;
            }
            if (result.clamped()) {
                sendMessage(player, Component.literal("Vertere limitado: aplicado "
                        + SpellCostCalculator.formatCost(result.transferred()) + " UMU."));
            }
            return result.transferred();
        }

        private void sendMessage(ServerPlayer player, Component message) {
            if (player != null && message != null) {
                player.sendSystemMessage(message);
            }
        }
    }
}
