package com.elderlexicon.mod.spell.scene;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Runs the {@link SceneLaws} of one cast every server tick and turns their {@link Outcome}s into
 * things that happen in the world.
 */
public final class SceneRunner {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double SCALD_RADIUS = 1.5D;
    private static final float MAX_SCALD_DAMAGE = 1.0F;
    private static final double SCALD_PER_UMU = 0.5D;
    private static final double BURST_POWER_PER_SQRT_UMU = 0.6D;
    private static final float MIN_BURST_POWER = 1.0F;
    private static final float MAX_BURST_POWER = 3.0F;

    private final ServerPlayer player;
    private final SpellScene scene;
    private final SceneLaws laws = new SceneLaws();
    private final long endTick;

    private SceneRunner(ServerPlayer player, SpellScene scene, long endTick) {
        this.player = player;
        this.scene = scene;
        this.endTick = endTick;
    }

    /** Starts watching the scene now and stops {@code durationTicks} later. */
    public static void start(ServerPlayer player, SpellScene scene, int durationTicks) {
        MinecraftServer server = player.server;
        if (server == null || server.isStopped()) {
            return;
        }
        SceneRunner runner = new SceneRunner(player, scene, server.getTickCount() + Math.max(1, durationTicks));
        server.tell(new TickTask(server.getTickCount() + 1, runner::step));
    }

    private void step() {
        MinecraftServer server = player.server;
        if (server == null || server.isStopped() || player.isRemoved()) {
            return;
        }
        long tick = server.getTickCount();
        try {
            for (Outcome outcome : laws.tick(scene, tick)) {
                apply(player.serverLevel(), outcome);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Spell scene step failed", exception);
            return;
        }
        if (tick < endTick) {
            server.tell(new TickTask(server.getTickCount() + 1, this::step));
        }
    }

    private void apply(ServerLevel level, Outcome outcome) {
        Vec3 at = new Vec3(outcome.at().x(), outcome.at().y(), outcome.at().z());
        LOGGER.debug("Spell scene outcome {} at {} worth {} UMU", outcome.type(), at, outcome.energy());
        switch (outcome.type()) {
            case DISCHARGE -> strike(level, at);
            case STEAM -> steam(level, at, outcome.energy());
            case STEAM_BURST -> burst(level, at, outcome.energy());
        }
    }

    private void strike(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(at);
        bolt.setCause(player);
        level.addFreshEntity(bolt);
    }

    private void steam(ServerLevel level, Vec3 at, double energy) {
        int count = Mth.clamp((int) Math.ceil(energy * 6.0D), 2, 24);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, count, 0.3D, 0.3D, 0.3D, 0.02D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.2F);
        float damage = (float) Math.min(MAX_SCALD_DAMAGE, energy * SCALD_PER_UMU);
        AABB area = new AABB(at, at).inflate(SCALD_RADIUS);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area)) {
            living.hurt(level.damageSources().generic(), damage);
        }
    }

    private void burst(ServerLevel level, Vec3 at, double energy) {
        float power = (float) Mth.clamp(Math.sqrt(energy) * BURST_POWER_PER_SQRT_UMU, MIN_BURST_POWER, MAX_BURST_POWER);
        level.explode(player, at.x, at.y, at.z, power, Level.ExplosionInteraction.MOB);
    }
}
