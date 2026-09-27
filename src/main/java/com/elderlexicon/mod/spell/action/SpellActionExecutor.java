package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.spell.Charge;
import com.elderlexicon.mod.spell.Conversion;
import com.elderlexicon.mod.spell.Density;
import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.function.BodyEnergy;
import com.elderlexicon.mod.spell.function.ElementOrb;
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

        // Every spell is one flow of energy: where it comes from (the world, by exsugat, or the mage's body), what it is
        // turned into on the way (each vertere), and the function that spends it (docs/exsugat-vertere-design.md).
        // A source captured by exsugat (book 8.2.1), with what it has been converted into since.
        VitaElement captured = null;
        VitaElement capturedAs = null;
        SpellAction capture = null;
        boolean capturedForSpell = false;
        // A bare quantum already took everything in reach for its function.
        boolean capturedAll = false;
        // Energy taken out of the body and held for the function after (vis quantum vertere igni chronos 0 iactare):
        // what it was, what it is now, and how much.
        VitaElement heldFrom = null;
        VitaElement held = null;
        double heldUmu = 0.0D;
        // The qualities changed by the conversions along the way (each vertere adds its own), for their work and time.
        int qualities = 0;
        // Every element the energy has been along the way, in order, so its orb can show each conversion.
        java.util.List<VitaElement> chain = new java.util.ArrayList<>();

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
                chain.clear();
                chain.add(currentElement);
                capturedForSpell = hasConsumerAfter(actions, index);
                continue;
            }
            // igni exsugat vertere aqua converts the fire being pulled, not the mage's own: the Vita is left alone.
            if (isVertere(action.runeId()) && action.subjectMark().isEmpty() && captured != null) {
                if (vertereIndex < vertereQueue.size()) {
                    VitaElement target = vertereQueue.get(vertereIndex++).target();
                    qualities += Conversion.qualities(capturedAs, target);
                    chain.add(target);
                    capturedAs = target;
                    currentElement = target;
                    context.setPrimaryElement(target);
                }
                continue;
            }
            // Held energy converted again (vis quantum vertere aqua vertere aura …): only what it is changes.
            if (isVertere(action.runeId()) && action.subjectMark().isEmpty() && held != null) {
                if (vertereIndex < vertereQueue.size()) {
                    VitaElement target = vertereQueue.get(vertereIndex++).target();
                    qualities += Conversion.qualities(held, target);
                    chain.add(target);
                    held = target;
                    currentElement = target;
                    context.setPrimaryElement(target);
                }
                continue;
            }
            // vis quantum vertere igni … iactare: a quantum on the vertere takes that much of the body (all of it, with
            // no number) and holds it, converted, for the function after, instead of only shifting the Vita.
            if (isVertere(action.runeId()) && action.subjectMark().isEmpty() && context.player() != null
                    && (action.quantityAll() || action.potency().isPresent()) && hasConsumerAfter(actions, index)) {
                if (vertereIndex < vertereQueue.size()) {
                    VitaElement target = vertereQueue.get(vertereIndex++).target();
                    heldFrom = currentElement;
                    heldUmu = action.quantityAll() ? BodyEnergy.drawAll(context.player(), currentElement)
                            : BodyEnergy.draw(context.player(), currentElement, action.potency().getAsDouble());
                    if (heldUmu <= EPSILON) {
                        nothingInBody(context, currentElement);
                        heldFrom = null;
                        continue;
                    }
                    qualities += Conversion.qualities(currentElement, target);
                    chain.clear();
                    chain.add(currentElement);
                    chain.add(target);
                    held = target;
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
            ExsugatFunctionHandler.Pulled all = null;
            boolean condensing = action.atOnce() && action.potency().isPresent();
            if (captured != null && capturedForSpell && (action.quantityAll() || condensing)
                    && context.player() != null) {
                // igni exsugat quantum iactare: everything in reach is pulled now and spent by this function
                // (quantum 20 chronos 0: twenty of it). With chronos 0 it is released in one instant, as intense as
                // all of it together (docs/condensacao-design.md).
                double limit = action.quantityAll() ? Double.MAX_VALUE : action.potency().getAsDouble();
                all = ExsugatFunctionHandler.captureAll(context, java.util.List.copyOf(chain), limit, action, capture);
                boolean atOnce = action.atOnce();
                // The spirit's work (merging all the sources into one, unmaking and remaking them as another element)
                // is done with the energy in hand: it is lost from it, and what is left is what the function releases.
                double worked = all.total() - (atOnce && all.sources() > 1 ? Heat.work(all.total(), all.sources()) : 0.0D);
                worked = Math.max(0.0D, worked * (1.0D - Conversion.workShare(qualities)));
                // The UMU is kept through a conversion: the intensity is that of what the source became (forty UMU of
                // earth turned to fire and pressed into one point are fire as hot as forty).
                Double intensity = null;
                if (capturedAs == VitaElement.IGNI) {
                    double heat = Heat.of(worked, all.sources(), atOnce);
                    intensity = heat > Heat.COMMON + EPSILON ? heat : null;
                } else if (atOnce && worked > EPSILON && (capturedAs == VitaElement.FIRMO
                        || capturedAs == VitaElement.AQUA || capturedAs == VitaElement.AURA)) {
                    // density of earth, pressure of water or air: all of it in one point
                    intensity = capturedAs == VitaElement.FIRMO ? Density.of(worked, all.sources(), true) : worked;
                }
                int charge = atOnce && all.total() > EPSILON
                        ? Charge.ticks(all.total()) + Conversion.chainTicks(all.total(), chain) : 0;
                action = action.toBuilder()
                        .putMetadata(SpellAction.QUANTITY, worked > EPSILON ? worked : null)
                        .putMetadata(SpellAction.INTENSITY, intensity)
                        .putMetadata(SpellAction.CARBON, all.coal() > 0 && atOnce ? all.coal() : null)
                        .putMetadata(SpellAction.CHARGE, charge > 0 ? charge : null)
                        .putMetadata(SpellAction.ORB, all.orb() >= 0 ? all.orb() : null)
                        .build();
                context.setCurrentAction(action);
                capturedAll = true;
            }
            double fromBody = 0.0D;
            VitaElement bodyFrom = null;
            if (captured == null && context.player() != null && (held != null || action.quantityAll())) {
                // From the body: what a vertere already took and converted, or, with a bare quantum here, all of the
                // source there is in the body (firmo quantum chronos 0 iactare; vis quantum …: all the mana). With
                // chronos 0 it is condensed into one point, as intense as all of it.
                if (held != null) {
                    fromBody = heldUmu;
                    bodyFrom = heldFrom;
                } else {
                    fromBody = BodyEnergy.drawAll(context.player(), currentElement);
                    bodyFrom = currentElement;
                    chain.clear();
                    chain.add(currentElement);
                    if (fromBody <= EPSILON) {
                        nothingInBody(context, currentElement);
                    }
                }
                held = null;
                heldFrom = null;
                heldUmu = 0.0D;
                boolean atOnce = action.atOnce() && fromBody > EPSILON;
                int charge = atOnce ? Charge.ticks(fromBody) + Conversion.chainTicks(fromBody, chain) : 0;
                // Converting it on the way is the spirit's work, done with the energy in hand: lost from it.
                double worked = fromBody * (1.0D - Conversion.workShare(qualities));
                Integer orb = null;
                if (atOnce) {
                    net.minecraft.server.level.ServerPlayer player = context.player();
                    // Gathered where it will be released: before the hand for an iactare, at the point for a vocant;
                    // the energy is seen streaming out of the mage into it.
                    java.util.function.Supplier<net.minecraft.world.phys.Vec3> point =
                            ExsugatFunctionHandler.orbPoint(context, action);
                    ElementOrb gathering = ElementOrb.gathering(player.serverLevel(), player,
                            java.util.List.copyOf(chain), worked, 0, point, Charge.ticks(fromBody));
                    player.serverLevel().addFreshEntity(gathering);
                    com.elderlexicon.mod.spell.function.Gatherings.fromBody(player.serverLevel(), player, chain.get(0),
                            point, Charge.ticks(fromBody));
                    orb = gathering.getId();
                }
                action = action.toBuilder()
                        .putMetadata(SpellAction.QUANTITY, worked > EPSILON ? worked : null)
                        .putMetadata(SpellAction.INTENSITY, atOnce ? worked : null)
                        .putMetadata(SpellAction.CHARGE, charge > 0 ? charge : null)
                        .putMetadata(SpellAction.ORB, orb)
                        .build();
                context.setCurrentAction(action);
            }
            SpellFunctionHandler handler = resolveHandler(action.runeId());
            if (handler != null) {
                handler.execute(context, currentElement);
            }
            if (fromBody > EPSILON) {
                // Taken out of the body already: it pays what the function spent (the work was taken out of it).
                context.addAmbientEnergy(currentElement, Math.min(fromBody, context.payableCost()));
                qualities = 0;
            }
            if (all != null && all.total() > EPSILON) {
                // The captured source pays what the function spent; the work of condensing it (merging all its
                // sources into one) is paid by the body.
                context.addAmbientEnergy(capturedAs, Math.min(all.total(), context.payableCost()));
            }
        }

        if (captured != null && !capturedAll && context.player() != null) {
            context.setCurrentAction(capture);
            double taken;
            if (capturedForSpell) {
                taken = ExsugatFunctionHandler.capture(context, captured, capturedAs, spenderAfter(actions, capture),
                        Conversion.workShare(qualities));
            } else {
                // Converted and never spent (firmo exsugat vertere igni): the portion captured becomes the other element
                // where it is, in the world.
                taken = ExsugatFunctionHandler.convertInPlace(context, captured, capturedAs,
                        capture.quantity().orElse(ExsugatFunctionHandler.DEFAULT_ABSORBED_UMU),
                        Conversion.workShare(qualities));
            }
        }
        if (held != null && heldUmu > EPSILON && context.player() != null) {
            // Taken from the body and converted, and no function spent it: it goes back into the body as what it became.
            context.absorbIntoBody(held, heldUmu * (1.0D - Conversion.workShare(qualities)));
        }
    }

    private static void nothingInBody(SpellContext context, VitaElement element) {
        context.player().displayClientMessage(Component.literal(
                "Nao ha " + element.runeId() + " no teu corpo para tirar."), true);
    }

    /** The first function after {@code exsugat} that spends what it captures (a vertere only converts it). */
    private SpellAction spenderAfter(List<SpellAction> actions, SpellAction exsugat) {
        boolean after = false;
        for (SpellAction later : actions) {
            if (later == exsugat) {
                after = true;
            } else if (after && later != null && later.type() == SpellActionType.FUNCTION && !isVertere(later.runeId())) {
                return later;
            }
        }
        return null;
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
