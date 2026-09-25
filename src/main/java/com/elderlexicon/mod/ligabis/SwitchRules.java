package com.elderlexicon.mod.ligabis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntToDoubleFunction;

/**
 * Rules for an aspect whose state is on or off: heat (burning or lit) for igni, wetness (a waterlogged
 * block) for aqua. What a link reflects is a <b>change</b>, not a level: when a member turns on, the ones
 * it is linked to turn on; when it turns off, they turn off.
 * <ul>
 *   <li><b>Mirror.</b> Any member's change reaches all the others.</li>
 *   <li><b>Hierarchy.</b> Only a parent's change reaches its children. A child changing does nothing to
 *       the parent.</li>
 *   <li><b>Chains.</b> A member changed by a link passes the change on to the members below it.</li>
 *   <li><b>Same tick.</b> If some members turn on while others turn off, turning on wins.</li>
 *   <li><b>No echo.</b> The state a link applies is remembered, so seeing it again is not an action.</li>
 *   <li><b>Tracked members only.</b> A member the world has never reported is not part of this channel.</li>
 * </ul>
 * A link that breaks does not undo anything: every member stays in the last state it had.
 */
final class SwitchRules {

    @FunctionalInterface
    interface EffectFactory {
        Effect create(MemberId target, boolean on);
    }

    private final Aspect aspect;
    private final LinkGraph graph;
    private final Owners owners;
    private final EffectFactory effects;
    private final IntToDoubleFunction cost;
    /** Last known (or, for members the engine changed, expected) state of every tracked member. */
    private final Map<MemberId, Boolean> state = new HashMap<>();

    SwitchRules(Aspect aspect, LinkGraph graph, Owners owners, EffectFactory effects, IntToDoubleFunction cost) {
        this.aspect = aspect;
        this.graph = graph;
        this.owners = owners;
        this.effects = effects;
        this.cost = cost;
    }

    List<Effect> observe(Map<MemberId, Boolean> observations) {
        List<MemberId> turnedOn = new ArrayList<>();
        List<MemberId> turnedOff = new ArrayList<>();
        for (Map.Entry<MemberId, Boolean> entry : new TreeMap<>(observations).entrySet()) {
            Boolean previous = state.put(entry.getKey(), entry.getValue());
            if (previous == null || previous.equals(entry.getValue())) {
                continue;
            }
            (entry.getValue() ? turnedOn : turnedOff).add(entry.getKey());
        }

        List<Effect> result = new ArrayList<>();
        Set<MemberId> on = new TreeSet<>();
        propagate(turnedOn, true, Set.of(), on, result);

        List<MemberId> stillOff = new ArrayList<>();
        for (MemberId member : turnedOff) {
            if (!on.contains(member)) {
                stillOff.add(member);
            }
        }
        propagate(stillOff, false, on, on, result);
        return result;
    }

    void sync(MemberId member, boolean on) {
        state.put(member, on);
    }

    void forget(MemberId member) {
        state.remove(member);
    }

    /**
     * Spreads a change from the members that made it. {@code protectedOn} are members that turned on this
     * tick and must not be turned off by a wave of turning off; {@code onCollector} gathers the members that
     * end up on.
     */
    private void propagate(List<MemberId> seeds, boolean on, Set<MemberId> protectedOn,
                           Set<MemberId> onCollector, List<Effect> result) {
        Set<MemberId> changed = new TreeSet<>(seeds);
        Deque<MemberId> queue = new ArrayDeque<>(seeds);
        if (on) {
            onCollector.addAll(seeds);
        }
        while (!queue.isEmpty()) {
            MemberId source = queue.poll();
            for (Link link : graph.links()) {
                if (link.aspect() != aspect || !owners.isOnline(link.owner()) || !graph.hasMark(source, link.first())) {
                    continue;
                }
                String targetsMark = link.isMirror() ? link.first() : link.second();
                List<MemberId> targets = new ArrayList<>();
                for (MemberId candidate : graph.members(targetsMark)) {
                    if (changed.contains(candidate) || protectedOn.contains(candidate)) {
                        continue;
                    }
                    Boolean current = state.get(candidate);
                    if (current == null || current == on) {
                        continue;
                    }
                    targets.add(candidate);
                }
                if (targets.isEmpty()) {
                    continue;
                }
                double umu = cost.applyAsDouble(targets.size());
                if (!owners.pay(link.owner(), umu)) {
                    graph.removeLink(link);
                    result.add(new Effect.LinkBroken(link));
                    continue;
                }
                result.add(new Effect.Charged(link.owner(), umu));
                for (MemberId target : targets) {
                    result.add(effects.create(target, on));
                    state.put(target, on);
                    changed.add(target);
                    queue.add(target);
                    if (on) {
                        onCollector.add(target);
                    }
                }
            }
        }
    }
}
