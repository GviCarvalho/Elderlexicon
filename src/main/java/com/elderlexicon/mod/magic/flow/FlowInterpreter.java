package com.elderlexicon.mod.magic.flow;

import com.elderlexicon.mod.magic.lexicon.Flow;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.VerbSpec;
import com.elderlexicon.mod.spell.Charge;
import com.elderlexicon.mod.spell.Conversion;
import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionType;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Runs the steps of a spell as one flow of energy (docs/exsugat-vertere-design.md, "Um fluxo só para todo feitiço"; with tenet in exsugat's place,
 * docs/plano-materia-e-forca.md):
 * <ol>
 *   <li><b>Where it comes from:</b> the world, when an origin filter says so ({@code firmo tenet iactare}, R5 of
 *       docs/plano-materia-e-forca.md); the body, when a quantity on a conversion draws it out (all of it with a bare
 *       quantity), or when a verb asks for all of it; otherwise the spell simply costs the body what its verbs
 *       spend.</li>
 *   <li><b>What it becomes on the way:</b> every conversion ({@link Flow#CONVERT}) turns the energy in hand into its
 *       target, and adds the rungs it crossed; the spirit's work of unmaking and remaking is taken out of the energy
 *       itself. With nothing in hand, a conversion turns the body's own Vita.</li>
 *   <li><b>What spends it:</b> every other verb. Released at once ({@code chronos 0}) what it spends is gathered into one
 *       point first, as intense as all of it ({@link IntensityLaws}).</li>
 * </ol>
 * What no verb spent is not lost: energy captured and never spent becomes what it was converted into where it is, and
 * energy drawn out of the body and never spent goes back into it.
 * <p>
 * The flow knows verbs only by the role the lexicon gives them, and the world only through {@link FlowWorld}.
 */
public final class FlowInterpreter {

    /** What a verb spends when no quantity says otherwise, in a window of two seconds (book 4.3.2). */
    public static final double DEFAULT_UMU = 10.0D;
    private static final double EPSILON = 1.0E-4D;

    private final Lexicon lexicon;

    public FlowInterpreter(Lexicon lexicon) {
        this.lexicon = Objects.requireNonNull(lexicon, "lexicon");
    }

    public void run(SpellLedger ledger, List<SpellAction> actions, FlowWorld world) {
        if (ledger == null || actions == null || actions.isEmpty()) {
            return;
        }

        VitaElement currentElement = ledger.primarySource()
                .map(primary -> lexicon.elementOf(primary.definition().id()))
                .orElse(ledger.primaryElement());
        String currentRuneId = ledger.elementRuneId();
        List<VertereRequest> vertereQueue = ledger.vertereRequests();
        int vertereIndex = 0;

        // A source taken from the world (book 8.2.1), with what it has been converted into since: where it is taken
        // from, and the verb it was written for.
        VitaElement captured = null;
        VitaElement capturedAs = null;
        SpellAction capture = null;
        int captureIndex = -1;
        boolean capturedForSpell = false;
        // A bare quantity already took everything in reach for its verb.
        boolean capturedAll = false;
        // Energy taken out of the body and held for the verb after (vis quantum vertere igni chronos 0 iactare): what it
        // is now, and how much.
        VitaElement held = null;
        double heldUmu = 0.0D;
        // The rungs crossed by the conversions along the way (each adds its own), for their work and time.
        int steps = 0;
        // Every element the energy has been along the way, in order, so its orb can show each conversion.
        List<VitaElement> chain = new ArrayList<>();

        for (int index = 0; index < actions.size(); index++) {
            SpellAction action = actions.get(index);
            if (action == null) {
                continue;
            }
            if (action.type() == SpellActionType.SOURCE && action.element() != null) {
                currentElement = action.element();
                currentRuneId = resolveElementRuneId(action, action.runeId(), currentRuneId);
                ledger.setElementRuneId(currentRuneId);
                continue;
            }
            if (action.type() != SpellActionType.FUNCTION) {
                continue;
            }
            ledger.setElementRuneId(resolveElementRuneId(action, currentRuneId, currentRuneId));
            ledger.setCurrentAction(action);
            Flow flow = lexicon.flowOf(action.runeId());
            boolean marked = action.subjectMark().isPresent();
            // The image converted (igni surgit vertere aqua) changes only the look of the source nearby: nothing is taken
            // from the Vita.
            if (flow == Flow.CONVERT && action.image()) {
                if (vertereIndex < vertereQueue.size()) {
                    vertereIndex++;
                }
                if (world.hasCaster()) {
                    world.disguise(action);
                }
                continue;
            }
            // aqua quantum 16 vertere m1: the marked thing's core changes, from what it holds; nothing of the mage's own
            // energy is converted or taken.
            if (flow == Flow.CONVERT && !marked && action.targetMark().isPresent()) {
                if (world.hasCaster()) {
                    world.perform(action, currentElement);
                }
                continue;
            }
            // igni tenet iactare: the source comes from the world. Nothing is taken at once: the world pays what this verb
            // and the ones after it spend, which is only known once they have run. A verb turned around (vocant with a
            // negative quantity) brings from the world by itself, and a marked thing or what the verb before produced
            // is no source to take.
            // A verb that moves matter (vocant) carries what it takes as it is: there is nothing to capture as energy,
            // unless it gathers it all first, or condenses it (firmo tenet quantum chronos 0 vocant).
            if (action.fromWorld() && !action.image() && !marked && !action.chained() && !action.reversed()
                    && !(transfers(action) && !action.gathers())) {
                captured = currentElement;
                capturedAs = currentElement;
                capture = originOf(action);
                captureIndex = index;
                capturedAll = false;
                chain.clear();
                chain.add(currentElement);
                capturedForSpell = flow != Flow.CONVERT || hasSpenderAfter(actions, index);
            }
            // igni tenet vertere aqua converts the fire being taken, not the mage's own: the Vita is left alone.
            if (flow == Flow.CONVERT && !marked && captured != null) {
                if (vertereIndex < vertereQueue.size()) {
                    VitaElement target = vertereQueue.get(vertereIndex++).target();
                    steps += Conversion.steps(capturedAs, target);
                    chain.add(target);
                    capturedAs = target;
                    currentElement = target;
                    ledger.setPrimaryElement(target);
                }
                continue;
            }
            // Held energy converted again (vis quantum vertere aqua vertere aura …): only what it is changes.
            if (flow == Flow.CONVERT && !marked && held != null) {
                if (vertereIndex < vertereQueue.size()) {
                    VitaElement target = vertereQueue.get(vertereIndex++).target();
                    steps += Conversion.steps(held, target);
                    chain.add(target);
                    held = target;
                    currentElement = target;
                    ledger.setPrimaryElement(target);
                }
                continue;
            }
            // vis quantum vertere igni … iactare: a quantity on the conversion takes that much of the body (all of it,
            // with no number) and holds it, converted, for the verb after, instead of only shifting the Vita.
            if (flow == Flow.CONVERT && !marked && world.hasCaster()
                    && (action.quantityAll() || action.potency().isPresent()) && hasSpenderAfter(actions, index)) {
                if (vertereIndex < vertereQueue.size()) {
                    VitaElement target = vertereQueue.get(vertereIndex++).target();
                    heldUmu = action.quantityAll() ? world.drawAll(currentElement)
                            : world.draw(currentElement, action.potency().getAsDouble());
                    if (heldUmu <= EPSILON) {
                        world.nothingInBody(currentElement);
                        continue;
                    }
                    steps += Conversion.steps(currentElement, target);
                    chain.clear();
                    chain.add(currentElement);
                    chain.add(target);
                    held = target;
                    currentElement = target;
                    ledger.setPrimaryElement(target);
                }
                continue;
            }
            // A conversion with nothing in hand converts the body's own Vita (igni vertere aqua); on a marked thing, its
            // operation converts that thing's matter instead.
            if (flow == Flow.CONVERT && !marked) {
                if (vertereIndex < vertereQueue.size()) {
                    VertereRequest written = vertereQueue.get(vertereIndex++);
                    // igni quantum 5 vertere aqua converts five UMU of the body's fire.
                    VertereRequest request = new VertereRequest(written.source(), written.target(),
                            action.quantity().orElse(written.amount()));
                    VitaElement updated = convertVita(ledger, world, request);
                    if (updated != null) {
                        currentElement = updated;
                    }
                }
                continue;
            }
            FlowWorld.Captured all = null;
            if (captured != null && capturedForSpell && action.gathers() && world.hasCaster()) {
                // firmo tenet quantum iactare: everything in reach is taken now and spent by this verb (quantum 20
                // chronos 0: twenty of it). With chronos 0 it is released in one instant, as intense as all of it
                // together (docs/condensacao-design.md).
                double limit = action.quantityAll() ? Double.MAX_VALUE : action.potency().getAsDouble();
                all = world.captureAll(List.copyOf(chain), limit, action, capture);
                boolean atOnce = action.atOnce();
                // The spirit's work (merging all the sources into one, unmaking and remaking them as another element) is
                // done with the energy in hand: it is lost from it, and what is left is what the verb releases.
                double worked = all.total() - (atOnce && all.sources() > 1 ? Heat.work(all.total(), all.sources()) : 0.0D);
                worked = Math.max(0.0D, worked * (1.0D - Conversion.workShare(steps)));
                // The UMU is kept through a conversion: the intensity is that of what the source became (forty UMU of
                // earth turned to fire and pressed into one point are fire as hot as forty).
                final double released = worked;
                final int sources = all.sources();
                Double intensity = IntensityLaws.of(capturedAs)
                        .map(law -> law.intensity(released, sources, atOnce)).orElse(null);
                int charge = atOnce && all.total() > EPSILON
                        ? Charge.ticks(all.total()) + Conversion.chainTicks(all.total(), chain) : 0;
                action = action.toBuilder()
                        .putMetadata(SpellAction.QUANTITY, worked > EPSILON ? worked : null)
                        .putMetadata(SpellAction.INTENSITY, intensity)
                        .putMetadata(SpellAction.CARBON, all.coal() > 0 && atOnce ? all.coal() : null)
                        .putMetadata(SpellAction.CHARGE, charge > 0 ? charge : null)
                        .putMetadata(SpellAction.ORB, all.orb() >= 0 ? all.orb() : null)
                        .build();
                ledger.setCurrentAction(action);
                capturedAll = true;
            }
            double fromBody = 0.0D;
            if (captured == null && world.hasCaster() && (held != null || action.quantityAll())) {
                // From the body: what a conversion already took and converted, or, with a bare quantity here, all of the
                // source there is in the body (firmo quantum chronos 0 iactare; vis quantum …: all the mana). With
                // chronos 0 it is condensed into one point, as intense as all of it.
                if (held != null) {
                    fromBody = heldUmu;
                } else {
                    fromBody = world.drawAll(currentElement);
                    chain.clear();
                    chain.add(currentElement);
                    if (fromBody <= EPSILON) {
                        world.nothingInBody(currentElement);
                    }
                }
                held = null;
                heldUmu = 0.0D;
                boolean atOnce = action.atOnce() && fromBody > EPSILON;
                int charge = atOnce ? Charge.ticks(fromBody) + Conversion.chainTicks(fromBody, chain) : 0;
                // Converting it on the way is the spirit's work, done with the energy in hand: lost from it.
                double worked = fromBody * (1.0D - Conversion.workShare(steps));
                Integer orb = null;
                if (atOnce) {
                    // Gathered where it will be released, the energy seen streaming out of the mage into it.
                    int gathered = world.gatherFromBody(List.copyOf(chain), worked, action, Charge.ticks(fromBody));
                    orb = gathered >= 0 ? gathered : null;
                }
                action = action.toBuilder()
                        .putMetadata(SpellAction.QUANTITY, worked > EPSILON ? worked : null)
                        .putMetadata(SpellAction.INTENSITY, atOnce ? worked : null)
                        .putMetadata(SpellAction.CHARGE, charge > 0 ? charge : null)
                        .putMetadata(SpellAction.ORB, orb)
                        .build();
                ledger.setCurrentAction(action);
            }
            if (nextTakesResult(actions, index)) {
                // The verb after acts on what this one produces (R2): it is made to be handed on.
                action = action.toBuilder().putMetadata(SpellAction.HANDS_ON, Boolean.TRUE).build();
                ledger.setCurrentAction(action);
            }
            world.perform(action, currentElement);
            if (fromBody > EPSILON) {
                // Taken out of the body already: it pays what the verb spent (the work was taken out of it).
                ledger.addAmbientEnergy(currentElement, Math.min(fromBody, ledger.payableCost()));
                steps = 0;
            }
            if (all != null && all.total() > EPSILON) {
                // The captured source pays what the verb spent; the work of condensing it is paid by the body.
                ledger.addAmbientEnergy(capturedAs, Math.min(all.total(), ledger.payableCost()));
            }
        }

        if (captured != null && !capturedAll && world.hasCaster()) {
            ledger.setCurrentAction(capture);
            if (capturedForSpell) {
                world.capture(captured, capturedAs, spenderFrom(actions, captureIndex), Conversion.workShare(steps));
            } else {
                // Converted and never spent (firmo tenet vertere igni): the portion taken becomes the other element where
                // it is, in the world.
                world.convertInPlace(captured, capturedAs, capture.quantity().orElse(DEFAULT_UMU),
                        Conversion.workShare(steps));
            }
        }
        if (held != null && heldUmu > EPSILON && world.hasCaster()) {
            // Taken from the body and converted, and no verb spent it: it goes back into the body as what it became.
            ledger.absorbIntoBody(held, heldUmu * (1.0D - Conversion.workShare(steps)));
        }
    }

    /** Converts the mage's own Vita; with a focus the conversion happens in the focus, and the Vita is left alone. */
    private static VitaElement convertVita(SpellLedger ledger, FlowWorld world, VertereRequest request) {
        double transferred = ledger.focusActive() ? request.amount() : world.transferVita(request);
        if (transferred <= EPSILON) {
            return null;
        }
        ledger.setPrimaryElement(request.target());
        ledger.addTotalCost(transferred);
        return request.target();
    }

    /**
     * The first verb, from the one the origin was written for, that spends what is taken (a conversion only converts
     * it).
     */
    private SpellAction spenderFrom(List<SpellAction> actions, int from) {
        for (int index = Math.max(0, from); index < actions.size(); index++) {
            SpellAction later = actions.get(index);
            if (later != null && later.type() == SpellActionType.FUNCTION
                    && lexicon.flowOf(later.runeId()) != Flow.CONVERT) {
                return later;
            }
        }
        return null;
    }

    /**
     * What the world reads of a source taken from it: the verb, with its place being where the source is taken from (the
     * origin written, or none: within the mage's reach) instead of where the verb acts.
     */
    private static SpellAction originOf(SpellAction action) {
        SpellAction.Builder origin = action.toBuilder().removeMetadata(SpellAction.PLACE);
        action.originPlace().ifPresent(place -> origin.putMetadata(SpellAction.PLACE, place));
        return origin.build();
    }

    /** Whether the verb moves matter rather than spending energy (the lexicon's {@code transfers}). */
    private boolean transfers(SpellAction action) {
        return lexicon.verb(action.runeId()).map(VerbSpec::transfers).orElse(false);
    }

    /** Whether the next verb after {@code index} acts on what the verb at {@code index} produces (R2). */
    private static boolean nextTakesResult(List<SpellAction> actions, int index) {
        for (int next = index + 1; next < actions.size(); next++) {
            SpellAction later = actions.get(next);
            if (later != null && later.type() == SpellActionType.FUNCTION) {
                return later.chained();
            }
        }
        return false;
    }

    /** Whether a verb after {@code index} spends the source; a conversion only converts it. */
    private boolean hasSpenderAfter(List<SpellAction> actions, int index) {
        for (int next = index + 1; next < actions.size(); next++) {
            SpellAction later = actions.get(next);
            if (later != null && later.type() == SpellActionType.FUNCTION
                    && lexicon.flowOf(later.runeId()) != Flow.CONVERT) {
                return true;
            }
        }
        return false;
    }

    private static String resolveElementRuneId(SpellAction action, String preferred, String fallback) {
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
}
