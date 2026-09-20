package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.spell.SpellContext;
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

        for (SpellAction action : actions) {
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
            if (isVertere(action.runeId())) {
                if (vertereIndex < vertereQueue.size()) {
                    VertereRequest request = vertereQueue.get(vertereIndex++);
                    VitaElement updated = handleVertere(context, request);
                    if (updated != null) {
                        currentElement = updated;
                    }
                }
                continue;
            }
            SpellFunctionHandler handler = resolveHandler(action.runeId());
            if (handler != null) {
                handler.execute(context, currentElement);
            }
        }
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
        double transferred = vertereGateway.transfer(context, request);
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
