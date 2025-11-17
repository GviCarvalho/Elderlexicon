package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

final class SpellEffects {

    private static final Map<VitaElement, ParticleOptions> PARTICLES = createParticleMap();

    private SpellEffects() {
    }

    static void schedule(ServerLevel level, int delayTicks, Runnable action) {
        if (level == null || action == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null || server.isStopped()) {
            return;
        }
        if (delayTicks <= 0) {
            server.execute(action);
        } else {
            server.tell(new TickTask(server.getTickCount() + delayTicks, action));
        }
    }

    static boolean isPlayerValid(ServerPlayer player) {
        return player != null && !player.isRemoved() && player.isAlive();
    }

    static void spawnProjectile(ServerPlayer player, VitaElement element) {
        Optional<ParticleOptions> particle = resolveParticle(element);
        if (particle.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eyePosition = player.getEyePosition();
        Vec3 lookVector = player.getLookAngle().normalize();
        double step = 0.5D;
        int segments = 16;
        double offsetScale = 0.08D;
        for (int i = 1; i <= segments; i++) {
            Vec3 point = eyePosition.add(lookVector.scale(i * step));
            level.sendParticles(particle.get(), point.x, point.y, point.z, 3,
                    lookVector.x * offsetScale, lookVector.y * offsetScale, lookVector.z * offsetScale, 0.01D);
        }
    }

    static void spawnSummonEffect(ServerPlayer player, VitaElement element, SpellImpact impact) {
        if (impact.blockPos() == null) {
            return;
        }
        Optional<ParticleOptions> particle = resolveParticle(element);
        if (particle.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 target = Vec3.atCenterOf(impact.blockPos()).add(0.0D, 0.5D, 0.0D);
        level.sendParticles(particle.get(), target.x, target.y, target.z, 12, 0.25D, 0.15D, 0.25D, 0.02D);
    }

    static SpellImpact findImpact(ServerPlayer player, double range) {
        ServerLevel level = player.serverLevel();
        Vec3 eyePosition = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() == 0.0D) {
            return new SpellImpact(eyePosition, null, null, null);
        }

        Vec3 end = eyePosition.add(look.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        Predicate<Entity> predicate = entity -> !entity.isSpectator() && entity.isPickable() && entity != player;
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eyePosition, end, searchBox, predicate);
        if (entityHit != null) {
            return new SpellImpact(entityHit.getLocation(), entityHit.getEntity(), null, null);
        }

        HitResult hitResult = player.pick(range, 0.0F, false);
        if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
            Vec3 location = blockHit.getLocation();
            return new SpellImpact(location, null, blockHit.getBlockPos(), blockHit.getDirection());
        }

        return new SpellImpact(end, null, null, null);
    }

    static void applyElementEffect(ServerPlayer player, VitaElement element, SpellImpact impact) {
        if (impact == null) {
            return;
        }
        switch (element) {
            case IGNI -> applyIgniEffect(player, impact);
            case FIRMO -> applyFirmoEffect(player, impact);
            case AURA -> applyAuraEffect(player, impact);
            case AQUA -> applyAquaEffect(player, impact);
            case BALANCED -> {
            }
        }
    }

    private static void applyIgniEffect(ServerPlayer player, SpellImpact impact) {
        Entity entity = impact.entity();
        if (entity instanceof LivingEntity living) {
            living.setSecondsOnFire(4);
            living.hurt(player.damageSources().playerAttack(player), 4.0F);
            return;
        }
        if (impact.blockPos() != null) {
            ServerLevel level = player.serverLevel();
            BlockPos firePos = impact.blockPos().above();
            if (level.isEmptyBlock(firePos)) {
                level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void applyFirmoEffect(ServerPlayer player, SpellImpact impact) {
        ServerLevel level = player.serverLevel();
        Entity entity = impact.entity();
        Vec3 look = player.getLookAngle().normalize();
        if (entity != null) {
            entity.push(look.x * 0.6D, 0.3D, look.z * 0.6D);
            entity.hurt(player.damageSources().playerAttack(player), 4.0F);
            placeDirtBlock(level, entity.blockPosition());
            return;
        }
        BlockPos targetPos = impact.blockPos();
        if (targetPos != null) {
            BlockPos placePos = impact.face() != null ? targetPos.relative(impact.face()) : targetPos;
            placeDirtBlock(level, placePos);
        }
    }

    private static void applyAuraEffect(ServerPlayer player, SpellImpact impact) {
        Entity entity = impact.entity();
        if (entity == null) {
            return;
        }
        Vec3 look = player.getLookAngle().normalize();
        entity.hurt(player.damageSources().playerAttack(player), 3.0F);
        entity.push(look.x * 0.5D, 0.2D, look.z * 0.5D);
    }

    private static void applyAquaEffect(ServerPlayer player, SpellImpact impact) {
        Entity entity = impact.entity();
        if (entity instanceof LivingEntity living) {
            living.hurt(player.damageSources().drown(), 4.0F);
            living.setAirSupply(Math.min(living.getAirSupply(), 20));
        }
    }

    private static void placeDirtBlock(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (!level.isLoaded(pos) || !level.isEmptyBlock(pos)) {
            return;
        }
        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static Optional<ParticleOptions> resolveParticle(VitaElement element) {
        return Optional.ofNullable(PARTICLES.get(element));
    }

    private static Map<VitaElement, ParticleOptions> createParticleMap() {
        EnumMap<VitaElement, ParticleOptions> map = new EnumMap<>(VitaElement.class);
        map.put(VitaElement.IGNI, ParticleTypes.FLAME);
        map.put(VitaElement.AQUA, ParticleTypes.BUBBLE);
        map.put(VitaElement.FIRMO, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()));
        map.put(VitaElement.AURA, ParticleTypes.CLOUD);
        map.put(VitaElement.BALANCED, new ShriekParticleOption(20));
        return map;
    }

    static void spawnDrainParticles(ServerPlayer player, VitaElement element, BlockPos source, double amount) {
        if (player == null || element == null || source == null) {
            return;
        }
        Optional<ParticleOptions> particle = resolveParticle(element);
        if (particle.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 start = Vec3.atCenterOf(source);
        Vec3 end = player.getEyePosition();
        Vec3 delta = end.subtract(start);
        int steps = Math.max(4, (int) Math.ceil(amount * 12));
        for (int i = 0; i < steps; i++) {
            double t = (double) i / (double) steps;
            Vec3 point = start.add(delta.scale(t));
            level.sendParticles(particle.get(), point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    static final class SpellImpact {
        private final Vec3 location;
        private final Entity entity;
        private final BlockPos blockPos;
        private final Direction face;

        SpellImpact(Vec3 location, Entity entity, BlockPos blockPos, Direction face) {
            this.location = location;
            this.entity = entity;
            this.blockPos = blockPos;
            this.face = face;
        }

        Vec3 location() {
            return location;
        }

        Entity entity() {
            return entity;
        }

        BlockPos blockPos() {
            return blockPos;
        }

        Direction face() {
            return face;
        }
    }
}
