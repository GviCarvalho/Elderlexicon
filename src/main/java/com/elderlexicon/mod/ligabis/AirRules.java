package com.elderlexicon.mod.ligabis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;

/**
 * Aqua for the living: breath. Members share their lungs, so what one loses or gains in air, the ones it
 * is linked to lose or gain too.
 * <ul>
 *   <li><b>Mirror.</b> Any member's change in air reaches all the others. Two members both holding their
 *       breath each get the other's loss on top of their own.</li>
 *   <li><b>Hierarchy.</b> Only a parent's change reaches its children.</li>
 *   <li><b>Chains.</b> A change passes down the chain.</li>
 *   <li><b>Shared amounts add up.</b> A member that receives changes from several sources gets their sum.
 *       The result is kept between 0 and the member's own maximum air.</li>
 *   <li><b>No echo.</b> The air a link sets is remembered, so seeing it again is not a new change.</li>
 *   <li><b>Cost.</b> Per tick of air moved, paid by the link's owner.</li>
 * </ul>
 */
final class AirRules {

    private final LinkGraph graph;
    private final Members members;
    private final Owners owners;
    private final CostPolicy costs;
    /** Last known (or, for members the engine changed, expected) air of every tracked member. */
    private final Map<MemberId, Integer> air = new HashMap<>();

    AirRules(LinkGraph graph, Members members, Owners owners, CostPolicy costs) {
        this.graph = graph;
        this.members = members;
        this.owners = owners;
        this.costs = costs;
    }

    List<Effect> observe(Map<MemberId, Integer> observations) {
        Map<MemberId, Integer> origins = new TreeMap<>();
        for (Map.Entry<MemberId, Integer> entry : new TreeMap<>(observations).entrySet()) {
            Integer previous = air.put(entry.getKey(), entry.getValue());
            if (previous == null || previous.equals(entry.getValue())) {
                continue;
            }
            origins.put(entry.getKey(), entry.getValue() - previous);
        }
        if (origins.isEmpty()) {
            return List.of();
        }

        List<Effect> effects = new ArrayList<>();
        Map<MemberId, Integer> incoming = new TreeMap<>();
        for (Map.Entry<MemberId, Integer> origin : origins.entrySet()) {
            spread(origin.getKey(), origin.getValue(), incoming, effects);
        }

        for (Map.Entry<MemberId, Integer> entry : incoming.entrySet()) {
            MemberId target = entry.getKey();
            int current = air.get(target);
            int next = Math.max(0, Math.min(Math.max(0, members.maxAir(target)), current + entry.getValue()));
            if (next == current) {
                continue;
            }
            air.put(target, next);
            effects.add(new Effect.SetAir(target, next));
        }
        return effects;
    }

    void sync(MemberId member, int currentAir) {
        air.put(member, currentAir);
    }

    void forget(MemberId member) {
        air.remove(member);
    }

    /** Carries one member's change in air to everyone it reaches, each at most once. */
    private void spread(MemberId origin, int delta, Map<MemberId, Integer> incoming, List<Effect> effects) {
        Set<MemberId> reached = new TreeSet<>();
        reached.add(origin);
        Deque<MemberId> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty()) {
            MemberId source = queue.poll();
            for (Link link : graph.links()) {
                if (link.aspect() != Aspect.AQUA || !owners.isOnline(link.owner()) || !graph.hasMark(source, link.first())) {
                    continue;
                }
                String targetsMark = link.isMirror() ? link.first() : link.second();
                List<MemberId> targets = new ArrayList<>();
                for (MemberId candidate : graph.members(targetsMark)) {
                    if (!reached.contains(candidate) && air.containsKey(candidate)) {
                        targets.add(candidate);
                    }
                }
                if (targets.isEmpty()) {
                    continue;
                }
                double umu = costs.air((double) Math.abs(delta) * targets.size());
                if (umu > 0.0D && !owners.pay(link.owner(), umu)) {
                    graph.removeLink(link);
                    effects.add(new Effect.LinkBroken(link));
                    continue;
                }
                if (umu > 0.0D) {
                    effects.add(new Effect.Charged(link.owner(), umu));
                }
                for (MemberId target : targets) {
                    incoming.merge(target, delta, Integer::sum);
                    reached.add(target);
                    queue.add(target);
                }
            }
        }
    }
}
