package com.elderlexicon.mod.ligabis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aura: movement. The world reports where each member is once per tick; how far it moved since the last
 * report is its own displacement. A link then decides how far it <i>should</i> have moved, and the engine
 * answers with the difference for the world to apply.
 * <ul>
 *   <li><b>Hierarchy.</b> A child's displacement is forced to equal its parents' (the sum of theirs).
 *       When the parent moves, the child is moved the same; when the parent stands still, whatever the
 *       child tries is cancelled. Parents are never touched by their children. Chains are resolved from
 *       the top down, so a grandchild follows what its parent was made to do.</li>
 *   <li><b>Mirror.</b> Each member's displacement is added to everybody else's. Two members walking the
 *       same way each go twice as far; two walking opposite ways lock each other in place. Chaos is the
 *       point.</li>
 *   <li><b>Cost.</b> Per block of correction the engine has to apply. A child that already moves exactly
 *       like its parent costs nothing.</li>
 *   <li><b>No echo.</b> The position a link asks for is remembered, so seeing it is not a new movement.</li>
 * </ul>
 */
final class MotionRules {

    /** Movements shorter than this (in blocks) are noise and are ignored. */
    static final double EPSILON = 1.0E-3D;

    private final LinkGraph graph;
    private final Owners owners;
    private final CostPolicy costs;
    /** Last known (or, for members the engine moved, expected) position of every tracked member. */
    private final Map<MemberId, Vec> position = new HashMap<>();

    MotionRules(LinkGraph graph, Owners owners, CostPolicy costs) {
        this.graph = graph;
        this.owners = owners;
        this.costs = costs;
    }

    List<Effect> observe(Map<MemberId, Vec> observations) {
        Map<MemberId, Vec> observed = new TreeMap<>(observations);
        Map<MemberId, Vec> own = new TreeMap<>();
        for (Map.Entry<MemberId, Vec> entry : observed.entrySet()) {
            Vec previous = position.put(entry.getKey(), entry.getValue());
            Vec moved = previous == null ? Vec.ZERO : entry.getValue().minus(previous);
            own.put(entry.getKey(), moved.length() < EPSILON ? Vec.ZERO : moved);
        }

        List<Effect> effects = new ArrayList<>();
        Map<MemberId, Vec> target = new TreeMap<>(own);
        applyMirrors(own, target, effects);
        applyHierarchies(target, effects);

        for (Map.Entry<MemberId, Vec> entry : target.entrySet()) {
            Vec correction = entry.getValue().minus(own.get(entry.getKey()));
            if (correction.length() < EPSILON) {
                continue;
            }
            effects.add(new Effect.Displace(entry.getKey(), correction));
            position.put(entry.getKey(), observed.get(entry.getKey()).plus(correction));
        }
        return effects;
    }

    void sync(MemberId member, Vec real) {
        position.put(member, real);
    }

    void forget(MemberId member) {
        position.remove(member);
    }

    private void applyMirrors(Map<MemberId, Vec> own, Map<MemberId, Vec> target, List<Effect> effects) {
        for (Link link : graph.links()) {
            if (!isLiveAura(link) || !link.isMirror()) {
                continue;
            }
            List<MemberId> group = tracked(link.first(), own);
            if (group.size() < 2) {
                continue;
            }
            Vec total = Vec.ZERO;
            for (MemberId member : group) {
                total = total.plus(own.get(member));
            }
            Map<MemberId, Vec> additions = new TreeMap<>();
            double blocks = 0.0D;
            for (MemberId member : group) {
                Vec added = total.minus(own.get(member));
                additions.put(member, added);
                blocks += added.length();
            }
            if (!charge(link, blocks, effects)) {
                continue;
            }
            additions.forEach((member, added) -> target.merge(member, added, Vec::plus));
        }
    }

    private void applyHierarchies(Map<MemberId, Vec> target, List<Effect> effects) {
        List<Link> hierarchies = new ArrayList<>();
        for (Link link : graph.links()) {
            if (isLiveAura(link) && !link.isMirror()) {
                hierarchies.add(link);
            }
        }
        // Parents are settled before their children, so a chain works from the top down.
        hierarchies.sort(Comparator.<Link>comparingInt(link -> depth(link.first(), 0)).thenComparing(Comparator.naturalOrder()));

        for (Link link : hierarchies) {
            List<MemberId> parents = tracked(link.first(), target);
            List<MemberId> children = tracked(link.second(), target);
            if (parents.isEmpty() || children.isEmpty()) {
                continue;
            }
            Map<MemberId, Vec> forcedByChild = new TreeMap<>();
            double blocks = 0.0D;
            for (MemberId child : children) {
                Vec forced = Vec.ZERO;
                boolean hasParent = false;
                for (MemberId parent : parents) {
                    if (!parent.equals(child)) {
                        forced = forced.plus(target.get(parent));
                        hasParent = true;
                    }
                }
                if (!hasParent) {
                    continue;
                }
                forcedByChild.put(child, forced);
                blocks += forced.minus(target.get(child)).length();
            }
            if (forcedByChild.isEmpty() || !charge(link, blocks, effects)) {
                continue;
            }
            forcedByChild.forEach(target::put);
        }
    }

    /** How many hierarchy links lie above a mark; parents have to be settled before their children. */
    private int depth(String mark, int guard) {
        if (guard > 64) {
            return 0;
        }
        int deepest = -1;
        for (Link link : graph.links()) {
            if (link.aspect() == Aspect.AURA && !link.isMirror() && link.second().equals(mark)) {
                deepest = Math.max(deepest, depth(link.first(), guard + 1));
            }
        }
        return deepest + 1;
    }

    private List<MemberId> tracked(String mark, Map<MemberId, Vec> reported) {
        List<MemberId> result = new ArrayList<>();
        for (MemberId member : graph.members(mark)) {
            if (reported.containsKey(member)) {
                result.add(member);
            }
        }
        return result;
    }

    private boolean isLiveAura(Link link) {
        return link.aspect() == Aspect.AURA && owners.isOnline(link.owner());
    }

    /** Charges the owner for correcting {@code blocks} blocks of movement; a link that cannot be paid breaks. */
    private boolean charge(Link link, double blocks, List<Effect> effects) {
        double umu = costs.move(blocks);
        if (umu <= 1.0E-9D) {
            return true;
        }
        if (!owners.pay(link.owner(), umu)) {
            graph.removeLink(link);
            effects.add(new Effect.LinkBroken(link));
            return false;
        }
        effects.add(new Effect.Charged(link.owner(), umu));
        return true;
    }
}
