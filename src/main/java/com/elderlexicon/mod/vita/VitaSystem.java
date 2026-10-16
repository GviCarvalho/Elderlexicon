package com.elderlexicon.mod.vita;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Objects;

/**
 * Centralizes Vita-related calculations and conversions between HP and UMU.
 */
public final class VitaSystem {

    public static final double UMU_PER_HP = 5.0D;
    public static final double XP_PER_UMU = 10.0D;
    public static final double AQUA_RATIO = 0.55D;
    public static final double AURA_RATIO = 0.38D;
    public static final double IGNI_RATIO = 0.02D;
    public static final double FIRMO_RATIO = 0.05D;
    public static final double DEFAULT_AQUA = 55.0D;
    public static final double DEFAULT_AURA = 38.0D;
    public static final double DEFAULT_IGNI = 2.0D;
    public static final double DEFAULT_FIRMO = 5.0D;
    public static final double DEFAULT_TOTAL = DEFAULT_AQUA + DEFAULT_AURA + DEFAULT_IGNI + DEFAULT_FIRMO;
    private static final ImbalanceProfile AQUA_PROFILE = new ImbalanceProfile(
            DEFAULT_AQUA * 0.60D,
            DEFAULT_AQUA * 0.90D,
            DEFAULT_AQUA * 1.10D,
            DEFAULT_AQUA * 1.40D,
            1.0D
    );
    private static final ImbalanceProfile AURA_PROFILE = new ImbalanceProfile(
            DEFAULT_AURA * 0.60D,
            DEFAULT_AURA * 0.90D,
            DEFAULT_AURA * 1.10D,
            DEFAULT_AURA * 1.40D,
            1.0D
    );
    private static final ImbalanceProfile FIRMO_PROFILE = new ImbalanceProfile(
            DEFAULT_FIRMO * 0.60D,
            DEFAULT_FIRMO * 0.90D,
            DEFAULT_FIRMO * 1.10D,
            DEFAULT_FIRMO * 1.40D,
            0.1D
    );
    private static final ImbalanceProfile IGNI_PROFILE = new ImbalanceProfile(
            DEFAULT_IGNI * 0.60D,
            DEFAULT_IGNI * 0.90D,
            DEFAULT_IGNI * 1.10D,
            DEFAULT_IGNI * 1.40D,
            0.05D
    );

    /** Share of an imbalance the body takes back each second: about 13 s from severe to balanced. */
    public static final double RELAX_PER_SECOND = 0.10D;
    private static final int TICKS_PER_SECOND = 20;
    private static final double HEALTH_EPSILON = 1.0E-4D;
    private static final double EPSILON = 1.0E-4D;

    private VitaSystem() {
    }

    public static VitaProfile fromPlayer(LivingEntity player) {
        if (player == null) {
            return VitaProfile.empty();
        }
        VitaData data = VitaData.get(player);
        return data.toProfile();
    }

    public static double getLifeEnergy(LivingEntity player) {
        return getLifeEnergy(player, VitaElement.BALANCED);
    }

    public static double getLifeEnergy(LivingEntity player, VitaElement element) {
        if (player == null) {
            return 0.0D;
        }
        VitaData data = VitaData.get(player);
        VitaElement target = element == null ? VitaElement.BALANCED : element;
        return data.get(target);
    }

    /**
     * A tick of a living body. A player's is always kept; a creature's only while it is out of whole (docs/feiticeiro-design.md,
     * 7.4): a whole creature with no body written down has nothing to tick, and one that comes back whole is forgotten.
     */
    public static void tick(LivingEntity player) {
        if (player == null) {
            return;
        }
        boolean creature = !(player instanceof Player);
        if (creature && !VitaData.kept(player)) {
            NATURES.remove(player.getUUID());
            return;
        }

        VitaData data = VitaData.get(player);
        float currentHealth = Math.max(0.0F, player.getHealth());
        float lastHealth = data.lastHealth();
        float maxHealth = data.maxHealth();
        boolean dirty = false;
        List<Nature> natures = NATURES.remove(player.getUUID());

        if (!data.chalice()) {
            // An older body is read as a chalice from now on: it is filled up to the health it has, so nobody loses
            // life to the change.
            data.healTo(currentHealth / maxHealth);
            data.setChalice();
            dirty = true;
        }

        if (Math.abs(currentHealth - lastHealth) > HEALTH_EPSILON) {
            if (currentHealth < lastHealth) {
                hurt(data, lastHealth, currentHealth, natures);
            } else {
                // Food, rest, a potion: what lacks is filled first, up to the health now.
                data.healTo(currentHealth / maxHealth);
            }
            data.setLastHealth(currentHealth);
            dirty = true;
        }

        if (player.tickCount % TICKS_PER_SECOND == 0 && data.expelExcess(RELAX_PER_SECOND)) {
            dirty = true;
        }

        // The life follows the element most lacking (docs/vita-design.md): a spell that fills it heals, one that takes
        // it hurts.
        if (player.isAlive() && !player.isDeadOrDying()) {
            float derived = (float) (maxHealth * Math.min(1.0D, data.wholeness()));
            if (Math.abs(derived - currentHealth) > 0.01F) {
                if (derived <= 0.05F) {
                    data.setLastHealth(0.0F);
                    data.save(player);
                    player.hurt(lackOf(player), Float.MAX_VALUE);
                    DRAWERS.remove(player.getUUID());
                    return;
                }
                boolean lost = derived < currentHealth;
                player.setHealth(derived);
                data.setLastHealth(player.getHealth());
                dirty = true;
                java.util.Optional<Entity> drawer = lost ? drawerOf(player) : java.util.Optional.empty();
                drawer.ifPresent(by -> {
                    // Who drew it out is who hurt it: the creature turns on them, and a death after is their kill.
                    if (by instanceof Player mage) {
                        player.setLastHurtByPlayer(mage);
                    }
                    if (by instanceof LivingEntity hurter) {
                        player.setLastHurtByMob(hurter);
                    }
                });
                if (lost && (creature || drawer.isPresent())
                        && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                    // A creature has no life bar to show it, and a mage should feel whose hand it was.
                    level.broadcastDamageEvent(player, lackOf(player));
                }
            }
        }

        if (refreshAquaTier(data)) {
            dirty = true;
        }
        if (refreshAuraTier(data)) {
            dirty = true;
        }
        if (refreshFirmoTier(data)) {
            dirty = true;
        }
        if (refreshIgniTier(data)) {
            dirty = true;
        }

        if (creature && data.whole()) {
            VitaData.forget(player);
            DRAWERS.remove(player.getUUID());
            Symptoms.clear(player);
            return;
        }

        if (dirty) {
            data.save(player);
        }

        VitaScoreboardManager.update(player, data.toProfile());
        // What the imbalance feels like, as a body feels it (docs/vita-sintomas-design.md).
        Symptoms.tick(player, data);
    }

    public static VitaImbalanceTier getAquaTier(LivingEntity player) {
        if (player == null) {
            return VitaImbalanceTier.BALANCED;
        }
        return VitaData.get(player).aquaTier();
    }

    public static VitaImbalanceTier getAuraTier(LivingEntity player) {
        if (player == null) {
            return VitaImbalanceTier.BALANCED;
        }
        return VitaData.get(player).auraTier();
    }

    public static VitaImbalanceTier getFirmoTier(LivingEntity player) {
        if (player == null) {
            return VitaImbalanceTier.BALANCED;
        }
        return VitaData.get(player).firmoTier();
    }

    public static VitaImbalanceTier getIgniTier(LivingEntity player) {
        if (player == null) {
            return VitaImbalanceTier.BALANCED;
        }
        return VitaData.get(player).igniTier();
    }

    public static void initialize(LivingEntity player) {
        VitaData data = VitaData.get(player);
        data.setBalancedValues(Math.max(0.0F, player.getHealth()) * UMU_PER_HP);
        data.setLastHealth(player.getHealth());
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void consumeLifeEnergy(LivingEntity player, float hpDamage) {
        consumeLifeEnergy(player, hpDamage, VitaElement.BALANCED);
    }

    public static void consumeLifeEnergy(LivingEntity player, float hpDamage, VitaElement element) {
        if (player == null) {
            return;
        }
        float clamped = Math.max(0.0F, Math.min(hpDamage, player.getHealth()));
        if (clamped <= 1.0E-4F) {
            return;
        }
        // The flesh tribute (book 3.2.1) is a hurt of the element it is taken from: the next tick takes it from there.
        VitaElement target = element == null ? VitaElement.BALANCED : element;
        hurtBy(player, clamped, target.isBalanced() ? new VitaElement[0] : new VitaElement[] {target});
        player.hurt(Objects.requireNonNull(player.damageSources().magic()), clamped);
    }

    /** Hunger from an imbalance: only a player has hunger to feel. */
    static void causeExhaustion(LivingEntity living, float amount) {
        if (living instanceof Player player) {
            player.causeFoodExhaustion(amount);
        }
    }

    static void addExhaustion(LivingEntity living, float amount) {
        if (living instanceof Player player) {
            player.getFoodData().addExhaustion(amount);
        }
    }

    /**
     * Takes up to {@code amount} UMU of {@code element} out of a living body (docs/feiticeiro-design.md, 7.4): what leaves
     * it is a lack, and the life follows the element most lacking, so the body is hurt on its next tick. Returns how much
     * was taken. Vis is no part of the body: nothing is taken of it.
     */
    public static double drain(LivingEntity living, VitaElement element, double amount) {
        return drain(living, element, amount, null).umu();
    }

    /**
     * How much of its life (points of health) drawing {@code amount} UMU of {@code element} out of {@code living} would
     * take, foreseen without taking anything (docs/feiticeiro-design.md, 3.3).
     */
    public static double lifeTakenBy(LivingEntity living, VitaElement element, double amount) {
        if (living == null || element == null || element.isBalanced() || amount <= EPSILON) {
            return 0.0D;
        }
        VitaData data = VitaData.peek(living);
        double before = Math.min(living.getHealth(), data.maxHealth() * Math.min(1.0D, data.wholeness()));
        data.consumeElement(element, Math.min(amount, Math.max(0.0D, data.get(element))));
        return Math.max(0.0D, before - data.maxHealth() * Math.min(1.0D, data.wholeness()));
    }

    /** What the body of {@code living} lacks of {@code element} to be whole, in UMU (nothing written down: nothing). */
    /**
     * The life (points of health) {@code living} would get back if {@code given} were put into its body, element by
     * element: the life follows the element most lacking, so filling one heals only as far as the next one lacking
     * lets it (docs/vita-design.md).
     */
    public static double lifeGivenBy(LivingEntity living, java.util.Map<VitaElement, Double> given) {
        if (living == null || given.isEmpty()) {
            return 0.0D;
        }
        VitaData data = VitaData.peek(living);
        double before = Double.MAX_VALUE;
        double after = Double.MAX_VALUE;
        for (VitaElement element : VitaElement.values()) {
            if (element.isBalanced()) {
                continue;
            }
            double full = data.full(element);
            if (full <= 1.0E-6D) {
                continue;
            }
            double now = data.get(element);
            before = Math.min(before, now / full);
            after = Math.min(after, Math.min(full, now + given.getOrDefault(element, 0.0D)) / full);
        }
        if (before == Double.MAX_VALUE) {
            return 0.0D;
        }
        double max = living.getMaxHealth();
        return Math.max(0.0D, (Math.min(1.0D, after) - Math.min(1.0D, before)) * max);
    }

    /**
     * What putting {@code given} into the body of {@code living} does to its life (points of health), as the body takes
     * it: an element filled past its full pushes the others out, and the life follows the one most lacking, so it may
     * be less than nothing (docs/feiticeiro-design.md, 3.19). {@link #lifeGivenBy} stops at full.
     */
    public static double lifeChangedBy(LivingEntity living, java.util.Map<VitaElement, Double> given) {
        if (living == null || given.isEmpty()) {
            return 0.0D;
        }
        VitaData data = VitaData.peek(living);
        double before = data.maxHealth() * Math.min(1.0D, data.wholeness());
        given.forEach((element, amount) -> {
            if (element != null && !element.isBalanced() && amount > EPSILON) {
                data.addElementEnergy(element, amount);
            }
        });
        return data.maxHealth() * Math.min(1.0D, data.wholeness()) - before;
    }

    /** How far past its full {@code element} would stand with {@code amount} more of it, as a share of its full. */
    public static double overfullBy(LivingEntity living, VitaElement element, double amount) {
        if (living == null || element == null || element.isBalanced()) {
            return 0.0D;
        }
        VitaData data = VitaData.peek(living);
        data.addElementEnergy(element, Math.max(0.0D, amount));
        double full = data.full(element);
        return full <= EPSILON ? 0.0D : Math.max(0.0D, (data.get(element) - full) / full);
    }

    public static double lackOf(LivingEntity living, VitaElement element) {
        if (living == null || element == null || element.isBalanced()) {
            return 0.0D;
        }
        VitaData data = VitaData.peek(living);
        return Math.max(0.0D, data.full(element) - data.get(element));
    }

    /** What was drawn out of a body: how many UMU, and how much of its life the lack takes (points of health). */
    public record Drawn(double umu, double life) {
        public static final Drawn NOTHING = new Drawn(0.0D, 0.0D);
    }

    /**
     * The same, by {@code by}: the hurt the lack makes is theirs for a few seconds (the creature turns on them, and a
     * death by it is a kill of theirs).
     */
    public static Drawn drain(LivingEntity living, VitaElement element, double amount,
                              @org.jetbrains.annotations.Nullable Entity by) {
        if (living == null || element == null || element.isBalanced() || amount <= EPSILON || !living.isAlive()) {
            return Drawn.NOTHING;
        }
        if (by != null && by != living) {
            DRAWERS.put(living.getUUID(), new Drawer(by, living.level().getGameTime()));
        }
        VitaData data = VitaData.get(living);
        double taken = Math.min(amount, Math.max(0.0D, data.get(element)));
        if (taken <= EPSILON) {
            return Drawn.NOTHING;
        }
        // The life follows the element most lacking: what the lack takes is the life before less the life after.
        double before = Math.min(living.getHealth(), data.maxHealth() * Math.min(1.0D, data.wholeness()));
        data.consumeElement(element, taken);
        double after = data.maxHealth() * Math.min(1.0D, data.wholeness());
        data.save(living);
        VitaScoreboardManager.update(living, data.toProfile());
        return new Drawn(taken, Math.max(0.0D, before - after));
    }

    // ------------------------------------------------------------------ the chalice (docs/vita-design.md)

    /** A hurt and the elements it takes the life from (none: all of them, in their shares). */
    private record Nature(VitaElement[] elements, double amount) {
    }

    /** Who last drew something out of a body, and when: the lack it leaves is their hurt. */
    private record Drawer(Entity by, long at) {
    }

    /** How long a draw is still the drawer's hurt: five seconds. */
    private static final long DRAWER_TICKS = 100L;
    private static final java.util.Map<java.util.UUID, Drawer> DRAWERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Who drew out of {@code living} in the last seconds, still there; empty when no one did. */
    private static java.util.Optional<Entity> drawerOf(LivingEntity living) {
        Drawer drawer = DRAWERS.get(living.getUUID());
        if (drawer == null) {
            return java.util.Optional.empty();
        }
        if (living.level().getGameTime() - drawer.at() > DRAWER_TICKS || drawer.by().isRemoved()) {
            DRAWERS.remove(living.getUUID());
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(drawer.by());
    }

    /**
     * The hurt a lack makes, named for the element most lacking (docs/vita-sintomas-design.md): the cold, the blood, the
     * breath, the bones. The drawer's, when someone drew it out, or else the body's own.
     */
    private static DamageSource lackOf(LivingEntity living) {
        VitaData data = VitaData.peek(living);
        VitaElement most = null;
        double least = Double.MAX_VALUE;
        for (VitaElement element : new VitaElement[] {VitaElement.AQUA, VitaElement.AURA, VitaElement.IGNI,
                VitaElement.FIRMO}) {
            double full = data.full(element);
            if (full > EPSILON && data.get(element) / full < least) {
                least = data.get(element) / full;
                most = element;
            }
        }
        String name = most == null ? null : switch (most) {
            case IGNI -> "hypothermia";
            case AQUA -> "exsanguination";
            case AURA -> "asphyxia";
            case FIRMO -> "shattering";
            case BALANCED -> null;
        };
        java.util.Optional<Entity> drawer = drawerOf(living);
        if (name == null) {
            return drawer.map(by -> living.damageSources().indirectMagic(by, by))
                    .orElseGet(() -> living.damageSources().magic());
        }
        net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> type = living.level().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                com.elderlexicon.mod.ElderLexicon.MODID, name)));
        return drawer.map(by -> new DamageSource(type, by, by)).orElseGet(() -> new DamageSource(type));
    }

    /** The natures of the hurts each player took since the last tick, in order. */
    private static final java.util.Map<java.util.UUID, List<Nature>> NATURES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Says {@code amount} of health is about to be lost from {@code elements} (none: from all of them). */
    public static void hurtBy(LivingEntity player, double amount, VitaElement... elements) {
        if (player == null || amount <= EPSILON) {
            return;
        }
        if (!(player instanceof Player)) {
            // A creature's body is written down from the health it had before the hurt, for the tick to take it from.
            VitaData.get(player);
        }
        NATURES.computeIfAbsent(player.getUUID(), id -> new java.util.ArrayList<>())
                .add(new Nature(elements == null ? new VitaElement[0] : elements, amount));
    }

    /**
     * The health lost since the last tick, taken from the elements: each hurt of a nature from its elements, down to
     * the health left after it; what no nature claims, from all of them in their shares.
     */
    private static void hurt(VitaData data, float from, float to, @org.jetbrains.annotations.Nullable List<Nature> natures) {
        float maxHealth = data.maxHealth();
        double remaining = from - to;
        double level = from;
        if (natures != null) {
            for (Nature nature : natures) {
                double amount = Math.min(nature.amount(), remaining);
                if (amount <= EPSILON) {
                    continue;
                }
                level -= amount;
                remaining -= amount;
                if (nature.elements().length == 0) {
                    data.loseShare(amount / maxHealth);
                } else {
                    for (VitaElement element : nature.elements()) {
                        data.dropTo(element, level / maxHealth);
                    }
                }
            }
        }
        if (remaining > EPSILON) {
            data.loseShare(remaining / maxHealth);
        }
    }

    public static void restoreLifeEnergy(LivingEntity player, double umuAmount) {
        if (player == null || umuAmount <= 1.0E-4D) {
            return;
        }
        VitaData data = VitaData.get(player);
        data.restoreAndStabilize(umuAmount);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static VitaImbalanceTier evaluateAquaTier(double current, VitaImbalanceTier previous) {
        return evaluateTier(current, previous, AQUA_PROFILE);
    }

    private static VitaImbalanceTier stepToward(VitaImbalanceTier from, VitaImbalanceTier to) {
        if (from == to) {
            return from;
        }
        int step = from.severityIndex() < to.severityIndex() ? 1 : -1;
        int nextIndex = from.severityIndex() + step;
        VitaImbalanceTier[] values = VitaImbalanceTier.values();
        nextIndex = Math.max(0, Math.min(values.length - 1, nextIndex));
        return values[nextIndex];
    }

    public static VitaImbalanceTier evaluateAuraTier(double current, VitaImbalanceTier previous) {
        return evaluateTier(current, previous, AURA_PROFILE);
    }

    private static boolean refreshAquaTier(VitaData data) {
        VitaImbalanceTier previous = data.aquaTier();
        VitaImbalanceTier next = evaluateTier(data.get(VitaElement.AQUA), previous,
                AQUA_PROFILE.scaled(data.share(VitaElement.AQUA) / AQUA_RATIO));
        return data.setAquaTier(next);
    }

    private static boolean refreshAuraTier(VitaData data) {
        VitaImbalanceTier previous = data.auraTier();
        VitaImbalanceTier next = evaluateTier(data.get(VitaElement.AURA), previous,
                AURA_PROFILE.scaled(data.share(VitaElement.AURA) / AURA_RATIO));
        return data.setAuraTier(next);
    }

    public static VitaImbalanceTier evaluateFirmoTier(double current, VitaImbalanceTier previous) {
        return evaluateTier(current, previous, FIRMO_PROFILE);
    }

    private static boolean refreshFirmoTier(VitaData data) {
        VitaImbalanceTier previous = data.firmoTier();
        VitaImbalanceTier next = evaluateTier(data.get(VitaElement.FIRMO), previous,
                FIRMO_PROFILE.scaled(data.share(VitaElement.FIRMO) / FIRMO_RATIO));
        return data.setFirmoTier(next);
    }

    public static VitaImbalanceTier evaluateIgniTier(double current, VitaImbalanceTier previous) {
        return evaluateTier(current, previous, IGNI_PROFILE);
    }

    private static VitaImbalanceTier evaluateTier(double current,
                                                  VitaImbalanceTier previous,
                                                  ImbalanceProfile profile) {
        VitaImbalanceTier fallback = previous == null ? VitaImbalanceTier.BALANCED : previous;
        VitaImbalanceTier candidate = profile.classify(current);
        if (candidate == fallback) {
            return candidate;
        }
        VitaImbalanceTier cursor = fallback;
        while (cursor != candidate) {
            VitaImbalanceTier next = stepToward(cursor, candidate);
            double boundary = profile.boundary(cursor, next);
            if (next.severityIndex() > cursor.severityIndex()) {
                if (current >= boundary + profile.hysteresis()) {
                    cursor = next;
                    continue;
                }
                return cursor;
            }
            if (current <= boundary - profile.hysteresis()) {
                cursor = next;
                continue;
            }
            return cursor;
        }
        return cursor;
    }

    private static boolean refreshIgniTier(VitaData data) {
        VitaImbalanceTier previous = data.igniTier();
        VitaImbalanceTier next = evaluateTier(data.get(VitaElement.IGNI), previous,
                IGNI_PROFILE.scaled(data.share(VitaElement.IGNI) / IGNI_RATIO));
        return data.setIgniTier(next);
    }

    // ------------------------------------------------------------------ the core (docs/particulas-design.md, stage 6)

    /** The share of the life each element is in the player's core: a person's 55/38/2/5 until a vertere changes it. */
    public static java.util.Map<VitaElement, Double> core(LivingEntity player) {
        VitaData data = VitaData.get(player);
        java.util.Map<VitaElement, Double> core = new java.util.EnumMap<>(VitaElement.class);
        for (VitaElement element : new VitaElement[] {VitaElement.FIRMO, VitaElement.AQUA, VitaElement.AURA,
                VitaElement.IGNI}) {
            core.put(element, data.share(element));
        }
        return core;
    }

    /** What the player's body holds of each element now, in UMU. */
    public static java.util.Map<VitaElement, Double> body(LivingEntity player) {
        VitaData data = VitaData.get(player);
        java.util.Map<VitaElement, Double> body = new java.util.EnumMap<>(VitaElement.class);
        for (VitaElement element : new VitaElement[] {VitaElement.FIRMO, VitaElement.AQUA, VitaElement.AURA,
                VitaElement.IGNI}) {
            body.put(element, data.get(element));
        }
        return body;
    }

    /**
     * A vertere changed the player's core (docs/particulas-design.md, stage 6): the body is converted into the new
     * proportion, as much in all as before, and from then on heals, hurts and settles back in it.
     */
    public static void reshape(LivingEntity player, java.util.Map<VitaElement, Double> core) {
        if (player == null || core == null) {
            return;
        }
        VitaData data = VitaData.get(player);
        data.reshape(core.getOrDefault(VitaElement.AQUA, 0.0D), core.getOrDefault(VitaElement.AURA, 0.0D),
                core.getOrDefault(VitaElement.IGNI, 0.0D), core.getOrDefault(VitaElement.FIRMO, 0.0D));
        refreshAquaTier(data);
        refreshAuraTier(data);
        refreshFirmoTier(data);
        refreshIgniTier(data);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void restoreElementEnergy(LivingEntity player, VitaElement element, double umuAmount) {
        if (player == null || umuAmount <= 1.0E-4D) {
            return;
        }
        VitaData data = VitaData.get(player);
        data.addElementEnergy(element, umuAmount);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void setElementEnergy(LivingEntity player, VitaElement element, double umuAmount) {
        if (player == null || element == null || umuAmount < 0.0D) {
            return;
        }
        VitaData data = VitaData.get(player);
        double current = data.get(element);
        data.adjustElement(element, umuAmount - current);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

public static void forceSetElement(LivingEntity player, VitaElement element, double umuAmount) {
        if (player == null || element == null) {
            return;
        }
        VitaData data = VitaData.get(player);
        data.setElementDirect(element, umuAmount);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static void resetStatus(LivingEntity player) {
        if (player == null) {
            return;
        }
        // Balanced for the health the player has now: resetting to a full 100 UMU while hurt made the next tick read
        // the missing health as fresh damage and unbalance everything again.
        VitaData data = VitaData.get(player);
        data.reset(player.getHealth());
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    public static ElementTransferResult transferElement(LivingEntity player,
                                                         VitaElement source,
                                                         VitaElement target,
                                                         double umuAmount,
                                                         boolean clampToAvailable) {
        double requested = Math.max(0.0D, umuAmount);
        if (player == null || requested <= EPSILON) {
            return new ElementTransferResult(requested, 0.0D, false);
        }

        VitaElement sanitizedSource = source == null ? VitaElement.BALANCED : source;
        VitaElement sanitizedTarget = target == null ? VitaElement.BALANCED : target;
        if (sanitizedSource == sanitizedTarget) {
            return new ElementTransferResult(requested, requested, false);
        }

        VitaData data = VitaData.get(player);
        double available = Math.max(0.0D, data.get(sanitizedSource));
        double transferable = Math.min(requested, available);
        if (transferable <= EPSILON) {
            return new ElementTransferResult(requested, 0.0D, false);
        }
        if (!clampToAvailable && transferable + EPSILON < requested) {
            return new ElementTransferResult(requested, 0.0D, false);
        }

        double applied = clampToAvailable ? transferable : requested;
        if (!clampToAvailable && applied > transferable) {
            applied = transferable;
        }

        data.consumeElement(sanitizedSource, applied);
        data.addElementEnergy(sanitizedTarget, applied);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());

        boolean clamped = applied + EPSILON < requested;
        return new ElementTransferResult(requested, applied, clamped);
    }

    public static double getElementExcess(LivingEntity player, VitaElement element) {
        if (player == null || element == null || element.isBalanced()) {
            return 0.0D;
        }
        VitaData data = VitaData.get(player);
        return data.elementExcess(element);
    }

    public static double consumeElementExcess(LivingEntity player, VitaElement element, double umuAmount) {
        if (player == null || element == null || element.isBalanced() || umuAmount <= 1.0E-4D) {
            return umuAmount;
        }
        VitaData data = VitaData.get(player);
        double remaining = data.consumeElementExcess(element, umuAmount);
        if (remaining + 1.0E-4D < umuAmount) {
            data.save(player);
            VitaScoreboardManager.update(player, data.toProfile());
        }
        return remaining;
    }

    public static void consumeElementReserve(LivingEntity player, VitaElement element, double umuAmount) {
        if (player == null || umuAmount <= 1.0E-4D) {
            return;
        }
        VitaData data = VitaData.get(player);
        data.consumeElement(element, umuAmount);
        data.save(player);
        VitaScoreboardManager.update(player, data.toProfile());
    }

    private record ImbalanceProfile(double severeLowThreshold,
                                    double slightLowThreshold,
                                    double slightHighThreshold,
                                    double severeHighThreshold,
                                    double hysteresis) {

        VitaImbalanceTier classify(double value) {
            if (value < severeLowThreshold) {
                return VitaImbalanceTier.SEVERELY_LOW;
            }
            if (value < slightLowThreshold) {
                return VitaImbalanceTier.SLIGHTLY_LOW;
            }
            if (value <= slightHighThreshold) {
                return VitaImbalanceTier.BALANCED;
            }
            if (value <= severeHighThreshold) {
                return VitaImbalanceTier.SLIGHTLY_HIGH;
            }
            return VitaImbalanceTier.SEVERELY_HIGH;
        }

        /** The same profile for a body whose core holds {@code factor} times a person's share of the element. */
        ImbalanceProfile scaled(double factor) {
            double f = Math.max(0.0D, factor);
            return new ImbalanceProfile(severeLowThreshold * f, slightLowThreshold * f, slightHighThreshold * f,
                    severeHighThreshold * f, hysteresis * Math.min(1.0D, f));
        }

        double boundary(VitaImbalanceTier from, VitaImbalanceTier to) {
            int lower = Math.min(from.severityIndex(), to.severityIndex());
            return switch (lower) {
                case 0 -> severeLowThreshold;
                case 1 -> slightLowThreshold;
                case 2 -> slightHighThreshold;
                default -> severeHighThreshold;
            };
        }
    }

    public record ElementTransferResult(double requested,
                                        double transferred,
                                        boolean clamped) {

        public double deficit() {
            return Math.max(0.0D, requested - transferred);
        }

        public boolean succeeded() {
            return transferred > EPSILON;
        }
    }

}
