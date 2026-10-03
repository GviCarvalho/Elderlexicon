package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.magic.flow.FlowInterpreter;
import com.elderlexicon.mod.magic.flow.FlowWorld;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Template;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.function.BodyEnergy;
import com.elderlexicon.mod.spell.function.ElementOrb;
import com.elderlexicon.mod.spell.function.WorldSources;
import com.elderlexicon.mod.spell.function.Gatherings;
import com.elderlexicon.mod.spell.function.ImageSpells;
import com.elderlexicon.mod.spell.function.SpellFunctionHandler;
import com.elderlexicon.mod.spell.function.StateChange;
import com.elderlexicon.mod.spell.registry.SpellFunctionHandlerRegistry;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Applies {@link SpellAction}s to the world without consulting the parser.
 * <p>
 * What happens when is the {@link FlowInterpreter}'s: it runs every spell as one flow of energy and knows the verbs only
 * by the role the lexicon gives them. This class is the world it runs in: the mage's body, the sources around, and the
 * operation each verb names ({@link SpellFunctionHandlerRegistry}).
 */
public final class SpellActionExecutor {

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
        new FlowInterpreter(Lexicons.get()).run(context, actions, new World(context));
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

    /** The world a spell's flow runs in: the caster, the body it draws from and the sources around it. */
    private final class World implements FlowWorld {

        private final SpellContext context;

        World(SpellContext context) {
            this.context = context;
        }

        @Override
        public boolean hasCaster() {
            return context.player() != null;
        }

        @Override
        public void perform(SpellAction action, VitaElement element) {
            SpellFunctionHandler handler = resolveHandler(action.runeId());
            if (handler != null) {
                handler.execute(context, element);
                return;
            }
            // A verb with no operation to run: say so, rather than let the spell fall silent.
            ServerPlayer player = context.player();
            if (player != null) {
                String text = Lexicons.get().note("issue.nooperation").orElse("'{rune}' nao faz nada ainda.");
                player.displayClientMessage(Component.literal(Template.fill(text, Map.of("rune", action.runeId()))),
                        true);
            }
        }

        @Override
        public void disguise(SpellAction action) {
            ImageSpells.disguise(context, action);
        }

        @Override
        public double drawAll(VitaElement element) {
            return BodyEnergy.drawAll(context.player(), element);
        }

        @Override
        public double draw(VitaElement element, double amount) {
            return BodyEnergy.draw(context.player(), element, amount);
        }

        @Override
        public void nothingInBody(VitaElement element) {
            context.player().displayClientMessage(Component.literal(
                    "Nao ha " + element.runeId() + " no teu corpo para tirar."), true);
        }

        @Override
        public Captured captureAll(List<VitaElement> chain, double limit, SpellAction spender, SpellAction capture) {
            WorldSources.Pulled pulled = WorldSources.captureAll(context, chain, limit, spender,
                    capture);
            return new Captured(pulled.sources(), pulled.total(), pulled.coal(), pulled.orb());
        }

        @Override
        public int gatherFromBody(List<VitaElement> chain, double worked, SpellAction spender, int chargeTicks) {
            ServerPlayer player = context.player();
            // Gathered where it will be released: before the hand for what is thrown, at the point for what is made to
            // appear; the energy is seen streaming out of the mage into it.
            Supplier<Vec3> point = WorldSources.orbPoint(context, spender);
            ElementOrb gathering = ElementOrb.gathering(player.serverLevel(), player, List.copyOf(chain), worked, 0,
                    point, chargeTicks);
            player.serverLevel().addFreshEntity(gathering);
            Gatherings.fromBody(player.serverLevel(), player, chain.get(0), point, chargeTicks);
            return gathering.getId();
        }

        @Override
        public double capture(VitaElement from, VitaElement as, SpellAction spender, double workShare) {
            return WorldSources.capture(context, from, as, spender, workShare);
        }

        @Override
        public double convertInPlace(VitaElement from, VitaElement as, double amount, double workShare) {
            // Matter in the world changes state where it is (L2): the work is paid as each block changes.
            return StateChange.inPlace(context, from, as, amount);
        }

        @Override
        public double transferVita(VertereRequest request) {
            return vertereGateway.transfer(context, request);
        }
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
