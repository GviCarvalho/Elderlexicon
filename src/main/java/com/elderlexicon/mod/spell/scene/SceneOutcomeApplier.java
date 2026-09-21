package com.elderlexicon.mod.spell.scene;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Turns the {@link Outcome}s of the scene laws into things that happen in the world. Blame and
 * credit go to one of the casters who paid for the outcome, picked at random in proportion to the
 * energy each put in, so damage respects the server's PvP setting and kills are credited.
 */
final class SceneOutcomeApplier {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double SCALD_RADIUS = 1.5D;
    private static final float MAX_SCALD_DAMAGE = 1.0F;
    private static final double SCALD_PER_UMU = 0.5D;
    private static final double BURST_POWER_PER_SQRT_UMU = 0.6D;
    private static final float MIN_BURST_POWER = 1.0F;
    private static final float MAX_BURST_POWER = 3.0F;
    /** Discharges shorter than this strike from the sky like vanilla lightning; longer ones are an arc. */
    private static final double MIN_ARC_LENGTH = 1.5D;
    private static final double ARC_HIT_RADIUS = 1.0D;
    private static final double ARC_DAMAGE_PER_UMU = 1.5D;
    private static final float MAX_ARC_DAMAGE = 10.0F;
    /** Draw arcs with {@link ArcBoltEntity}; set to false to fall back to the particle bolt. */
    private static final boolean USE_ARC_ENTITY = true;
    private static final double ARC_START_OFFSET = 0.8D;
    private static final double ARC_START_DROP = 0.3D;
    private static final double ARC_BEND_SPACING = 1.0D;
    private static final double ARC_JITTER = 0.3D;
    private static final double ARC_CORE_SPACING = 0.15D;

    private SceneOutcomeApplier() {
    }

    static void apply(ServerLevel level, Outcome outcome) {
        Vec3 from = toVec(outcome.from());
        Vec3 to = toVec(outcome.to());
        Vec3 center = toVec(outcome.center());
        LOGGER.debug("Spell scene outcome {} from {} to {} worth {} UMU shares {}",
                outcome.type(), from, to, outcome.energy(), outcome.shares());
        ServerPlayer attacker = pickAttacker(level, outcome.shares());
        switch (outcome.type()) {
            case DISCHARGE -> {
                if (outcome.length() < MIN_ARC_LENGTH) {
                    strike(level, center, attacker);
                } else {
                    arc(level, from, to, outcome, attacker);
                }
            }
            case STEAM -> steam(level, center, outcome, attacker);
            case STEAM_BURST -> burst(level, center, outcome.energy(), attacker);
        }
    }

    private static Vec3 toVec(Emission.Point point) {
        return new Vec3(point.x(), point.y(), point.z());
    }

    /**
     * Picks one online caster at random, weighted by the energy they put into the outcome. Over many
     * outcomes everyone is blamed in proportion to what they contributed.
     */
    @Nullable
    private static ServerPlayer pickAttacker(ServerLevel level, Map<UUID, Double> shares) {
        Map<UUID, Double> online = new TreeMap<>();
        double total = 0.0D;
        for (Map.Entry<UUID, Double> entry : shares.entrySet()) {
            if (entry.getKey().equals(Emission.NO_CASTER) || entry.getValue() <= 0.0D) {
                continue;
            }
            if (level.getServer().getPlayerList().getPlayer(entry.getKey()) == null) {
                continue;
            }
            online.put(entry.getKey(), entry.getValue());
            total += entry.getValue();
        }
        if (total <= 0.0D) {
            return null;
        }
        double roll = level.random.nextDouble() * total;
        UUID chosen = null;
        for (Map.Entry<UUID, Double> entry : online.entrySet()) {
            chosen = entry.getKey();
            roll -= entry.getValue();
            if (roll <= 0.0D) {
                break;
            }
        }
        return chosen == null ? null : level.getServer().getPlayerList().getPlayer(chosen);
    }

    private static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable ServerPlayer attacker) {
        Holder<DamageType> holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type);
        return attacker == null ? new DamageSource(holder) : new DamageSource(holder, attacker, attacker);
    }

    /** An electric arc that leaves the casters and follows the beams, hurting whatever it crosses. */
    private static void arc(ServerLevel level, Vec3 from, Vec3 to, Outcome outcome, @Nullable ServerPlayer attacker) {
        Vec3 direction = to.subtract(from).normalize();
        // Start a little ahead of and below the eyes, so the bolt is not hidden inside the camera.
        Vec3 start = from.add(direction.scale(ARC_START_OFFSET)).add(0.0D, -ARC_START_DROP, 0.0D);
        if (USE_ARC_ENTITY) {
            level.addFreshEntity(ArcBoltEntity.create(level, start, to));
        } else {
            drawBolt(level, start, to);
        }
        level.playSound(null, start.x, start.y, start.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.8F);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.3F);

        float damage = (float) Math.min(MAX_ARC_DAMAGE, outcome.energy() * ARC_DAMAGE_PER_UMU);
        AABB area = new AABB(start, to).inflate(ARC_HIT_RADIUS);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (outcome.shares().containsKey(living.getUUID())) {
                continue; // whoever fed the arc is not hurt by it
            }
            Vec3 body = living.position().add(0.0D, living.getBbHeight() / 2.0D, 0.0D);
            if (distanceToSegment(body, start, to) <= ARC_HIT_RADIUS) {
                living.hurt(source(level, DamageTypes.LIGHTNING_BOLT, attacker), damage);
            }
        }
    }

    /** Draws a jagged, glowing bolt: bright core along a zigzag path plus sparks at every bend. */
    private static void drawBolt(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 path = end.subtract(start);
        double length = path.length();
        int bends = Math.max(1, (int) Math.ceil(length / ARC_BEND_SPACING));
        Vec3 previous = start;
        for (int i = 1; i <= bends; i++) {
            Vec3 point = start.add(path.scale((double) i / bends));
            if (i < bends) {
                point = point.add(
                        (level.random.nextDouble() - 0.5D) * 2.0D * ARC_JITTER,
                        (level.random.nextDouble() - 0.5D) * 2.0D * ARC_JITTER,
                        (level.random.nextDouble() - 0.5D) * 2.0D * ARC_JITTER);
            }
            Vec3 leg = point.subtract(previous);
            int dots = Math.max(1, (int) Math.ceil(leg.length() / ARC_CORE_SPACING));
            for (int d = 0; d <= dots; d++) {
                Vec3 dot = previous.add(leg.scale((double) d / dots));
                level.sendParticles(ParticleTypes.END_ROD, dot.x, dot.y, dot.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 4, 0.15D, 0.15D, 0.15D, 0.1D);
            previous = point;
        }
        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-9D) {
            return point.distanceTo(from);
        }
        double t = Mth.clamp(point.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return point.distanceTo(from.add(segment.scale(t)));
    }

    private static void strike(ServerLevel level, Vec3 at, @Nullable ServerPlayer attacker) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(at);
        bolt.setCause(attacker);
        level.addFreshEntity(bolt);
    }

    private static void steam(ServerLevel level, Vec3 at, Outcome outcome, @Nullable ServerPlayer attacker) {
        double energy = outcome.energy();
        int count = Mth.clamp((int) Math.ceil(energy * 6.0D), 2, 24);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, count, 0.3D, 0.3D, 0.3D, 0.02D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.2F);
        float damage = (float) Math.min(MAX_SCALD_DAMAGE, energy * SCALD_PER_UMU);
        AABB area = new AABB(at, at).inflate(SCALD_RADIUS);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area)) {
            // Casters scald themselves without PvP rules getting in the way; everyone else is blamed on a caster.
            ServerPlayer blamed = outcome.shares().containsKey(living.getUUID()) ? null : attacker;
            living.hurt(source(level, DamageTypes.GENERIC, blamed), damage);
        }
    }

    private static void burst(ServerLevel level, Vec3 at, double energy, @Nullable ServerPlayer attacker) {
        float power = (float) Mth.clamp(Math.sqrt(energy) * BURST_POWER_PER_SQRT_UMU, MIN_BURST_POWER, MAX_BURST_POWER);
        level.explode(attacker, at.x, at.y, at.z, power, Level.ExplosionInteraction.MOB);
    }
}
