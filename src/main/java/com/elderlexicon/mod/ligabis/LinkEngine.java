package com.elderlexicon.mod.ligabis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Decides what a Ligabis link does when something happens. It has no idea what a player or a block is:
 * it reads {@link LinkEvent}s and answers with {@link Effect}s, so all of it can be tested without the game.
 * <p>
 * Firmo (integrity) works like this:
 * <ul>
 *   <li><b>Hierarchy.</b> Damage a child takes is cancelled on the child and moved to its parents (split
 *       equally); a parent passes it on to its own parents, and so on up the chain, so only the top takes
 *       it. A parent's own damage does not touch the child. When a parent dies or is destroyed, its
 *       children die. A child dying does nothing to the parent.</li>
 *   <li><b>Mirror.</b> Damage one member takes is repeated on every other member. If one dies, all die.</li>
 *   <li><b>No echo.</b> Damage the engine itself sent is never reflected again.</li>
 *   <li><b>Cost.</b> Every trigger is paid by the link's owner. If they cannot pay, the link breaks and
 *       the effect does not happen. An offline owner suspends the link.</li>
 * </ul>
 */
public final class LinkEngine {

    private static final double EPSILON = 1.0E-9D;
    /** How many links a piece of damage may climb before it stops. */
    private static final int MAX_DEPTH = 64;

    private final LinkGraph graph;
    private final Members members;
    private final Owners owners;
    private final CostPolicy costs;
    /** Damage absorbed so far by members without health of their own. */
    private final Map<MemberId, Double> wear = new HashMap<>();
    private final SwitchRules heat;
    private final SwitchRules wet;
    private final AirRules breath;
    private final MotionRules motion;

    public LinkEngine(LinkGraph graph, Members members, Owners owners, CostPolicy costs) {
        this.graph = graph;
        this.members = members;
        this.owners = owners;
        this.costs = costs;
        this.heat = new SwitchRules(Aspect.IGNI, graph, owners, Effect.SetHeat::new, costs::heatChange);
        this.wet = new SwitchRules(Aspect.AQUA, graph, owners, Effect.SetWet::new, costs::wetChange);
        this.breath = new AirRules(graph, members, owners, costs);
        this.motion = new MotionRules(graph, owners, costs);
    }

    public List<Effect> handle(LinkEvent event) {
        if (event instanceof LinkEvent.Damaged damaged) {
            return onDamaged(damaged);
        }
        if (event instanceof LinkEvent.Died died) {
            return onDied(died);
        }
        return List.of();
    }

    /**
     * Igni: the world tells the engine, once per tick, whether each member it tracks is burning or lit.
     * A change since the last observation is an action, and it is reflected along the links. Members seen
     * for the first time only set a baseline. Effects the engine asked for are remembered, so seeing them
     * again is not a new action.
     */
    public List<Effect> observeHeat(Map<MemberId, Boolean> observations) {
        return heat.observe(observations);
    }

    /** Tells the engine the real heat of a member (for instance when a request could not be carried out). */
    public void syncHeat(MemberId member, boolean hot) {
        heat.sync(member, hot);
    }

    /** Aqua for blocks: like {@link #observeHeat}, for waterlogged (true) or dry (false) blocks. */
    public List<Effect> observeWet(Map<MemberId, Boolean> observations) {
        return wet.observe(observations);
    }

    public void syncWet(MemberId member, boolean isWet) {
        wet.sync(member, isWet);
    }

    /**
     * Aqua for the living: the world reports each member's air supply, in ticks, once per tick. A change
     * since the last report is shared along the links, so linked members breathe from the same lungs.
     */
    public List<Effect> observeAir(Map<MemberId, Integer> observations) {
        return breath.observe(observations);
    }

    public void syncAir(MemberId member, int air) {
        breath.sync(member, air);
    }

    /**
     * Aura: the world reports where each member is, once per tick. The engine compares that with the last
     * position to know how far each one moved by itself, and answers with the {@link Effect.Displace} needed
     * to make the linked members move as their links demand.
     */
    public List<Effect> observeMotion(Map<MemberId, Vec> observations) {
        return motion.observe(observations);
    }

    public void syncMotion(MemberId member, Vec position) {
        motion.sync(member, position);
    }

    /** Damage a member has absorbed on behalf of its children (objects only). */
    public double wearOf(MemberId member) {
        return wear.getOrDefault(member, 0.0D);
    }

    /** Forgets the wear of an object whose mark was removed. */
    public void clearWear(MemberId member) {
        wear.remove(member);
    }

    /** Puts back the wear an object had before a restart. */
    public void restoreWear(MemberId member, double worn) {
        if (worn > 0.0D) {
            wear.put(member, worn);
        }
    }

    private List<Effect> onDamaged(LinkEvent.Damaged event) {
        if (event.fromLink() || event.amount() <= EPSILON) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        Set<MemberId> dead = new TreeSet<>();
        Deque<MemberId> pending = new ArrayDeque<>();
        boolean cancelled = climb(event.member(), event.amount(), effects, dead, pending, new TreeSet<>(), 0);

        if (!cancelled) {
            for (Link link : graph.links()) {
                if (!isLiveFirmo(link) || !link.isMirror() || !graph.hasMark(event.member(), link.first())) {
                    continue;
                }
                List<MemberId> others = others(graph.members(link.first()), event.member());
                if (others.isEmpty()) {
                    continue;
                }
                if (!charge(link, costs.damage(event.amount()) * others.size(), effects)) {
                    continue;
                }
                for (MemberId other : others) {
                    absorb(other, event.amount(), effects, dead, pending);
                }
            }
        }

        processDeaths(pending, dead, effects);
        return effects;
    }

    private List<Effect> onDied(LinkEvent.Died event) {
        if (!graph.hasAnyMark(event.member())) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        Set<MemberId> dead = new TreeSet<>();
        Deque<MemberId> pending = new ArrayDeque<>();
        dead.add(event.member());
        pending.add(event.member());
        processDeaths(pending, dead, effects);
        return effects;
    }

    /**
     * Passes damage a member would take up its hierarchy: to its parents, and from them to their own
     * parents, and so on. Only the member at the top of the chain takes it. Each step is a trigger and is
     * paid for; if a step cannot be paid the damage stays where it got to.
     *
     * @return true when the damage was moved to at least one parent
     */
    private boolean climb(MemberId member, double amount, List<Effect> effects, Set<MemberId> dead,
                          Deque<MemberId> pending, Set<MemberId> route, int depth) {
        if (depth >= MAX_DEPTH || !route.add(member)) {
            return false;
        }
        try {
            for (Link link : graph.links()) {
                if (!isLiveFirmo(link) || link.isMirror() || !graph.hasMark(member, link.second())) {
                    continue;
                }
                List<MemberId> parents = new ArrayList<>();
                for (MemberId parent : others(graph.members(link.first()), member)) {
                    if (!route.contains(parent)) {
                        parents.add(parent);
                    }
                }
                if (parents.isEmpty() || !charge(link, costs.damage(amount), effects)) {
                    continue;
                }
                if (depth == 0) {
                    effects.add(new Effect.CancelDamage());
                }
                double share = amount / parents.size();
                for (MemberId parent : parents) {
                    if (!climb(parent, share, effects, dead, pending, route, depth + 1)) {
                        absorb(parent, share, effects, dead, pending);
                    }
                }
                return true;
            }
            return false;
        } finally {
            route.remove(member);
        }
    }

    /** Puts damage on a member: applied by the world for the living, worn down by the engine for objects. */
    private void absorb(MemberId target, double amount, List<Effect> effects, Set<MemberId> dead, Deque<MemberId> pending) {
        if (dead.contains(target)) {
            return;
        }
        MemberProfile profile = members.profile(target);
        if (profile.kind() == MemberProfile.Kind.LIVING) {
            effects.add(new Effect.DealDamage(target, amount));
            return;
        }
        double total = wear.merge(target, amount, Double::sum);
        if (total >= profile.capacity() - EPSILON) {
            effects.add(new Effect.Kill(target));
            dead.add(target);
            pending.add(target);
        } else {
            effects.add(new Effect.Worn(target, total));
        }
    }

    /**
     * Spreads deaths: in a mirror everyone dies with the one who died, in a hierarchy the children die
     * with the parent. Each death can cause more, so this runs until nothing new dies.
     */
    private void processDeaths(Deque<MemberId> pending, Set<MemberId> dead, List<Effect> effects) {
        while (!pending.isEmpty()) {
            MemberId gone = pending.poll();
            for (Link link : graph.links()) {
                if (!isLiveFirmo(link) || !graph.hasMark(gone, link.first())) {
                    continue;
                }
                String victimsMark = link.isMirror() ? link.first() : link.second();
                List<MemberId> victims = new ArrayList<>();
                for (MemberId candidate : graph.members(victimsMark)) {
                    if (!dead.contains(candidate)) {
                        victims.add(candidate);
                    }
                }
                if (victims.isEmpty()) {
                    continue;
                }
                double cost = 0.0D;
                for (MemberId victim : victims) {
                    cost += costs.death(remaining(victim));
                }
                if (!charge(link, cost, effects)) {
                    continue;
                }
                for (MemberId victim : victims) {
                    effects.add(new Effect.Kill(victim));
                    dead.add(victim);
                    pending.add(victim);
                }
            }
            graph.forget(gone);
            wear.remove(gone);
            heat.forget(gone);
            wet.forget(gone);
            breath.forget(gone);
            motion.forget(gone);
            effects.add(new Effect.MemberGone(gone));
        }
    }

    private double remaining(MemberId member) {
        MemberProfile profile = members.profile(member);
        if (profile.kind() == MemberProfile.Kind.LIVING) {
            return Math.max(0.0D, members.health(member));
        }
        return Math.max(0.0D, profile.capacity() - wearOf(member));
    }

    private boolean isLiveFirmo(Link link) {
        return link.aspect() == Aspect.FIRMO && owners.isOnline(link.owner());
    }

    /** Charges the owner; when they cannot pay the link is broken and false is returned. */
    private boolean charge(Link link, double umu, List<Effect> effects) {
        if (umu <= EPSILON) {
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

    private static List<MemberId> others(List<MemberId> all, MemberId except) {
        List<MemberId> result = new ArrayList<>(all.size());
        for (MemberId member : all) {
            if (!member.equals(except)) {
                result.add(member);
            }
        }
        return result;
    }
}
