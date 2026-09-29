package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.Config;
import com.elderlexicon.mod.magic.matter.ImpactLaw;
import com.elderlexicon.mod.magic.matter.Qualities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where what flies strikes (docs/plano-rosa-dos-elementos.md, section 4): the law of impact acted out in the world. The
 * energy of the motion ({@link ImpactLaw}) hurts and pushes what it struck, breaks the blocks around the point that it
 * can afford (as far as {@link Config#magicBreaksBlocks} lets it), and is heard louder and deeper the more of it there
 * was; strong enough, it goes off like an explosion. What struck acts by its character too: the hot burns, the wet puts
 * fire out.
 */
public final class Impacts {

    /** How long something hot sets what it strikes on fire, in seconds at full heat. */
    private static final int BURN_SECONDS = 8;

    private Impacts() {
    }

    /**
     * {@code umu} of something of these {@code qualities}, moving at {@code velocity}, strikes at {@code at}: the
     * creature {@code struck}, or the block {@code block}, or nothing but the air. Returns the energy of the blow.
     */
    public static double strike(ServerLevel level, @Nullable Entity caster, Vec3 at, @Nullable Entity struck,
                                @Nullable BlockPos block, Qualities qualities, double umu, Vec3 velocity) {
        double energy = ImpactLaw.energy(qualities, umu, velocity.length());
        Vec3 heading = velocity.lengthSqr() > 1.0E-6D ? velocity.normalize() : Vec3.ZERO;
        BlockPos centre = block != null ? block : BlockPos.containing(at);
        BlockState hit = level.getBlockState(centre);

        character(level, at, struck, block, qualities);
        if (energy >= ImpactLaw.FELT) {
            if (struck != null) {
                hurt(level, caster, struck, ImpactLaw.damage(energy), heading.scale(ImpactLaw.knockback(energy)));
            }
            if (ImpactLaw.blast(energy)) {
                blast(level, caster, at, struck, energy);
            }
            if (breaksBlocks(level)) {
                breakAround(level, caster, at, centre, ImpactLaw.breaking(energy));
            }
        }
        heard(level, at, hit, qualities, energy);
        return energy;
    }

    static double strike(ServerLevel level, @Nullable Entity caster, SpellEffects.SpellImpact impact,
                         Qualities qualities, double umu, Vec3 velocity) {
        return strike(level, caster, impact.location(), impact.entity(), impact.blockPos(), qualities, umu, velocity);
    }

    /** Whether the blows of magic break blocks here, by the server's configuration and the world's rules. */
    public static boolean breaksBlocks(ServerLevel level) {
        return switch (Config.magicBreaksBlocks) {
            case ALWAYS -> true;
            case NEVER -> false;
            case MOB_GRIEFING -> level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        };
    }

    /** How the explosions of magic treat blocks, by the same configuration. */
    public static Level.ExplosionInteraction explosions() {
        return switch (Config.magicBreaksBlocks) {
            case ALWAYS -> Level.ExplosionInteraction.BLOCK;
            case NEVER -> Level.ExplosionInteraction.NONE;
            case MOB_GRIEFING -> Level.ExplosionInteraction.MOB;
        };
    }

    private static void hurt(ServerLevel level, @Nullable Entity caster, Entity struck, double damage, Vec3 push) {
        if (damage > 0.0D && struck instanceof LivingEntity) {
            struck.hurt(caster != null ? level.damageSources().thrown(caster, caster)
                    : level.damageSources().generic(), (float) damage);
        }
        if (push.lengthSqr() > 1.0E-6D) {
            struck.push(push.x, push.y + Math.min(0.4D, push.length() * 0.2D), push.z);
            struck.hurtMarked = true;
        }
    }

    /** A blow strong enough goes off: everyone near is hurt and thrown back, less the farther they are. */
    private static void blast(ServerLevel level, @Nullable Entity caster, Vec3 at, @Nullable Entity struck,
                              double energy) {
        double reach = 1.0D + Math.cbrt(energy) / 2.0D;
        for (Entity near : level.getEntities((Entity) null, new AABB(at, at).inflate(reach),
                entity -> entity != struck && entity.isAlive() && !entity.isSpectator())) {
            double distance = near.position().distanceTo(at);
            if (distance > reach) {
                continue;
            }
            double falloff = 1.0D - distance / reach;
            Vec3 away = near.position().subtract(at);
            away = away.lengthSqr() > 1.0E-6D ? away.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
            hurt(level, caster, near, ImpactLaw.damage(energy) * falloff * 0.5D,
                    away.scale(ImpactLaw.knockback(energy) * falloff));
        }
        level.sendParticles(energy >= 4.0D * ImpactLaw.BLAST ? ParticleTypes.EXPLOSION_EMITTER
                : ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * The blocks around the point, nearest first, each breaking when what is left of the energy pays its hardness;
     * what the mage could not break by hand (a protected spot) stays.
     */
    private static void breakAround(ServerLevel level, @Nullable Entity caster, Vec3 at, BlockPos centre,
                                    double budget) {
        int radius = ImpactLaw.radius(budget / ImpactLaw.BREAKING_SHARE);
        if (radius < 0) {
            return;
        }
        List<BlockPos> around = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius),
                centre.offset(radius, radius, radius))) {
            if (pos.distSqr(centre) <= (radius + 0.5D) * (radius + 0.5D)) {
                around.add(pos.immutable());
            }
        }
        around.sort(Comparator.comparingDouble((BlockPos pos) -> Vec3.atCenterOf(pos).distanceToSqr(at))
                .thenComparingLong(BlockPos::asLong));
        double left = budget;
        for (BlockPos pos : around) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (caster instanceof ServerPlayer player && !level.mayInteract(player, pos)) {
                continue;
            }
            double cost = ImpactLaw.cost(state.getDestroySpeed(level, pos));
            if (cost > left) {
                continue;
            }
            left -= cost;
            level.destroyBlock(pos, true, caster);
        }
    }

    /** The hot burns and kindles where it strikes; the wet puts out what burns. */
    private static void character(ServerLevel level, Vec3 at, @Nullable Entity struck, @Nullable BlockPos block,
                                  Qualities qualities) {
        if (qualities.hot()) {
            if (struck != null) {
                struck.setSecondsOnFire(Math.max(1, (int) Math.round(BURN_SECONDS * qualities.heat())));
            }
            BlockPos above = block != null ? block.above() : BlockPos.containing(at);
            if (level.getBlockState(above).isAir() && BaseFireBlock.canBePlacedAt(level, above, Direction.UP)) {
                level.setBlockAndUpdate(above, BaseFireBlock.getState(level, above));
            }
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 12, 0.3D, 0.3D, 0.3D, 0.05D);
        }
        if (qualities.wet()) {
            if (struck != null) {
                struck.clearFire();
            }
            BlockPos centre = BlockPos.containing(at);
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
                if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 16, 0.4D, 0.2D, 0.4D, 0.1D);
        }
    }

    /** The blow is heard, louder and deeper the more energy it had, with the sound of what struck and what was hit. */
    private static void heard(ServerLevel level, Vec3 at, BlockState hit, Qualities qualities, double energy) {
        float volume = ImpactLaw.volume(energy);
        float pitch = ImpactLaw.pitch(energy);
        if (!hit.isAir()) {
            level.playSound(null, at.x, at.y, at.z, hit.getSoundType().getBreakSound(), SoundSource.BLOCKS, volume,
                    pitch);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, hit), at.x, at.y, at.z,
                    (int) Math.min(80.0D, 4.0D + energy), 0.3D, 0.3D, 0.3D, 0.15D);
        }
        if (ImpactLaw.blast(energy)) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, volume, pitch);
        } else if (qualities.heavy() && energy >= ImpactLaw.FELT) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, volume, pitch);
        }
        if (qualities.wet()) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, volume * 0.8F,
                    pitch);
        }
        if (qualities.hot()) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, volume * 0.8F,
                    pitch);
        }
        if (qualities.light()) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, volume * 0.8F,
                    pitch);
        }
    }
}
