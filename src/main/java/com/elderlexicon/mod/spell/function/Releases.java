package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.spell.AirPressure;
import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.Pressure;
import com.elderlexicon.mod.spell.nature.NatureWorld;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Matter held in a form it does not keep by itself (a blade of fire, a spear of water), let go where it strikes
 * (docs/invocacao-design.md, "O golpe mostra a sua força"): every particle of it goes into the world there, fire as heat,
 * water and air into the air of the block, earth as dust, so nothing is lost and the drives do the rest. How hard it
 * goes off is how tightly it was pressed into its room: one UMU in a square is the common element, and the bands are
 * the condensation's (fire past ten melts, past thirty is plasma; water past thirty is ice that bursts; air past thirty
 * goes off as a bomb).
 */
public final class Releases {

    private Releases() {
    }

    /**
     * Lets {@code matter}, pressed into {@code room} squares, go at {@code at}, struck by what {@code caster} threw.
     */
    public static void matter(ServerLevel level, @Nullable LivingEntity caster, Vec3 at, Particles matter, double room) {
        Particles present = matter.present();
        if (present.total() <= 0L) {
            return;
        }
        double squares = Math.max(1.0E-3D, room);
        BlockPos pos = BlockPos.containing(at);
        long fire = present.igni();
        if (fire > 0L) {
            double heat = fire / (double) Particles.PER_UMU / squares;
            if (Heat.band(heat) == Heat.Band.PLASMA) {
                level.explode(caster, at.x, at.y, at.z, Heat.burst(heat), true, Impacts.explosions());
            }
            if (!NatureWorld.heat(level, pos, fire)) {
                NatureWorld.heat(level, pos.above(), fire);
            }
            show(level, at, heat, heat >= 30.0D ? ParticleTypes.ELECTRIC_SPARK
                    : heat >= 10.0D ? ParticleTypes.LAVA : heat >= 3.0D ? ParticleTypes.SOUL_FIRE_FLAME
                    : ParticleTypes.FLAME);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                    loudness(heat), pitch(heat));
        }
        long water = present.aqua();
        if (water > 0L) {
            double pressure = water / (double) Particles.PER_UMU / squares * Pressure.SOURCE;
            if (caster != null && pressure > Pressure.SOURCE) {
                AirSpots.throwAway(level, caster, at, Pressure.burstReach(pressure), Pressure.push(pressure), true);
            }
            NatureWorld.blow(level, pos, Particles.of(VitaElement.AQUA, water));
            show(level, at, pressure / Pressure.SOURCE, pressure >= Pressure.ICE ? ParticleTypes.SNOWFLAKE
                    : ParticleTypes.SPLASH);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS,
                    loudness(pressure), pitch(pressure));
        }
        long air = present.aura();
        if (air > 0L) {
            double pressure = air / (double) Particles.PER_UMU / squares * AirPressure.UMU_PER_AIR;
            if (caster != null && pressure >= AirPressure.BOMB) {
                AirSpots.bomb(level, caster, at, pressure);
            } else if (caster != null && pressure > AirPressure.UMU_PER_AIR) {
                AirSpots.throwAway(level, caster, at, AirPressure.reach(pressure), AirPressure.push(pressure), true);
            }
            NatureWorld.blow(level, pos, Particles.of(VitaElement.AURA, air));
            show(level, at, pressure / AirPressure.UMU_PER_AIR, ParticleTypes.CLOUD);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS,
                    loudness(pressure), pitch(pressure));
        }
        long earth = present.firmo();
        if (earth > 0L) {
            NatureWorld.blow(level, pos, Particles.of(VitaElement.FIRMO, earth));
        }
    }

    /**
     * What a thrown thing leaves behind it as it flies, as strong as {@code intensity} (one is the common element): more
     * of it, and of a hotter or harder kind, the more is pressed into it.
     */
    public static void trail(ServerLevel level, Vec3 at, VitaElement aspect, double intensity) {
        if (intensity <= 0.0D) {
            return;
        }
        ParticleOptions shown = switch (aspect) {
            case IGNI -> intensity >= 30.0D ? ParticleTypes.ELECTRIC_SPARK : intensity >= 10.0D ? ParticleTypes.LAVA
                    : intensity >= 3.0D ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
            case AQUA -> intensity >= 10.0D ? ParticleTypes.SNOWFLAKE : ParticleTypes.DRIPPING_WATER;
            case AURA -> ParticleTypes.CLOUD;
            default -> ParticleTypes.ASH;
        };
        int count = (int) Math.min(40L, Math.round(1.0D + 3.0D * Math.log(1.0D + intensity)));
        double spread = Math.min(1.0D, 0.05D + 0.08D * Math.log(1.0D + intensity));
        level.sendParticles(shown, at.x, at.y, at.z, count, spread, spread, spread, 0.01D);
        if (aspect == VitaElement.IGNI && intensity >= 10.0D) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, count / 4, spread, spread, spread, 0.01D);
        }
    }

    private static void show(ServerLevel level, Vec3 at, double intensity, ParticleOptions shown) {
        int count = (int) Math.min(200L, Math.round(8.0D + 12.0D * Math.log(1.0D + intensity)));
        double spread = Math.min(3.0D, 0.3D + 0.4D * Math.log(1.0D + intensity));
        level.sendParticles(shown, at.x, at.y, at.z, count, spread, spread, spread, 0.05D);
    }

    /** Louder the stronger, and never past what the game plays. */
    private static float loudness(double intensity) {
        return (float) Math.min(3.0D, 0.6D + 0.4D * Math.log(1.0D + intensity));
    }

    /** Deeper the stronger. */
    private static float pitch(double intensity) {
        return (float) Math.max(0.5D, 1.3D - 0.15D * Math.log(1.0D + intensity));
    }
}
