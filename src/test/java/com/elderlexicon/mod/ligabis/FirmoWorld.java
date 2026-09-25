package com.elderlexicon.mod.ligabis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A tiny fake world for the Ligabis engine tests. */
final class FirmoWorld implements Members, Owners {

    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    final LinkGraph graph = new LinkGraph();
    final Map<MemberId, MemberProfile> profiles = new HashMap<>();
    final Map<MemberId, Double> health = new HashMap<>();
    final Set<UUID> online = new HashSet<>(Set.of(OWNER));
    final LinkEngine engine = new LinkEngine(graph, this, this, CostPolicy.DEFAULT);
    double balance = 1_000_000.0D;
    double paid;

    MemberId living(String key, double hp, String mark) {
        MemberId id = new MemberId(key);
        profiles.put(id, MemberProfile.living());
        health.put(id, hp);
        graph.mark(id, mark);
        return id;
    }

    MemberId object(String key, double capacity, String mark) {
        MemberId id = new MemberId(key);
        profiles.put(id, MemberProfile.object(capacity));
        graph.mark(id, mark);
        return id;
    }

    void firmoHierarchy(String parentMark, String childMark) {
        graph.addLink(new Link(OWNER, Aspect.FIRMO, parentMark, childMark));
    }

    void firmoMirror(String mark) {
        graph.addLink(new Link(OWNER, Aspect.FIRMO, mark, null));
    }

    void igniMirror(String mark) {
        graph.addLink(new Link(OWNER, Aspect.IGNI, mark, null));
    }

    void igniHierarchy(String parentMark, String childMark) {
        graph.addLink(new Link(OWNER, Aspect.IGNI, parentMark, childMark));
    }

    /** Reports heat once per tick. "a+ b-" means a is hot and b is not. */
    List<Effect> observe(String spec) {
        return engine.observeHeat(heat(spec));
    }

    static Map<MemberId, Boolean> heat(String spec) {
        Map<MemberId, Boolean> observations = new java.util.LinkedHashMap<>();
        for (String token : spec.trim().split("\\s+")) {
            observations.put(new MemberId(token.substring(0, token.length() - 1)), token.endsWith("+"));
        }
        return observations;
    }

    static List<Effect.SetHeat> heated(List<Effect> effects) {
        return of(effects, Effect.SetHeat.class);
    }

    void aquaMirror(String mark) {
        graph.addLink(new Link(OWNER, Aspect.AQUA, mark, null));
    }

    void aquaHierarchy(String parentMark, String childMark) {
        graph.addLink(new Link(OWNER, Aspect.AQUA, parentMark, childMark));
    }

    /** Reports air once per tick. "a=299 b=300" means a holds 299 ticks of air and b holds 300. */
    List<Effect> observeAir(String spec) {
        Map<MemberId, Integer> observations = new java.util.LinkedHashMap<>();
        for (String token : spec.trim().split("\\s+")) {
            String[] parts = token.split("=");
            observations.put(new MemberId(parts[0]), Integer.parseInt(parts[1]));
        }
        return engine.observeAir(observations);
    }

    /** Reports waterlogged blocks once per tick. "a+ b-" means a is wet and b is dry. */
    List<Effect> observeWet(String spec) {
        return engine.observeWet(heat(spec));
    }

    static List<Effect.SetAir> aired(List<Effect> effects) {
        return of(effects, Effect.SetAir.class);
    }

    static List<Effect.SetWet> wetted(List<Effect> effects) {
        return of(effects, Effect.SetWet.class);
    }

    void auraMirror(String mark) {
        graph.addLink(new Link(OWNER, Aspect.AURA, mark, null));
    }

    void auraHierarchy(String parentMark, String childMark) {
        graph.addLink(new Link(OWNER, Aspect.AURA, parentMark, childMark));
    }

    /** Reports positions once per tick. "a=1,0,0 b=0,2,0" places a at x=1 and b at y=2. */
    List<Effect> observeMotion(String spec) {
        return engine.observeMotion(positions(spec));
    }

    static Map<MemberId, Vec> positions(String spec) {
        Map<MemberId, Vec> observations = new java.util.LinkedHashMap<>();
        for (String token : spec.trim().split("\\s+")) {
            String[] parts = token.split("=");
            String[] xyz = parts[1].split(",");
            observations.put(new MemberId(parts[0]),
                    new Vec(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2])));
        }
        return observations;
    }

    static List<Effect.Displace> displaced(List<Effect> effects) {
        return of(effects, Effect.Displace.class);
    }

    List<Effect> damaged(MemberId member, double amount) {
        return engine.handle(new LinkEvent.Damaged(member, amount, false));
    }

    List<Effect> died(MemberId member) {
        return engine.handle(new LinkEvent.Died(member));
    }

    static <T extends Effect> List<T> of(List<Effect> effects, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Effect effect : effects) {
            if (type.isInstance(effect)) {
                result.add(type.cast(effect));
            }
        }
        return result;
    }

    static List<MemberId> killed(List<Effect> effects) {
        return of(effects, Effect.Kill.class).stream().map(Effect.Kill::target).toList();
    }

    @Override
    public MemberProfile profile(MemberId member) {
        return profiles.get(member);
    }

    @Override
    public double health(MemberId member) {
        return health.getOrDefault(member, 0.0D);
    }

    @Override
    public boolean isOnline(UUID owner) {
        return online.contains(owner);
    }

    @Override
    public boolean pay(UUID owner, double umu) {
        if (balance < umu) {
            return false;
        }
        balance -= umu;
        paid += umu;
        return true;
    }
}
