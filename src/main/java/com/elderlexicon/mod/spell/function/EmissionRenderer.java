package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.scene.Emission;
import com.elderlexicon.mod.spell.scene.SpellScene;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Draws the beams of a scene using the element particles. The fewer UMU an emission has left,
 * the fewer particles it gets, so matter that the laws turned into something else dissipates.
 */
public final class EmissionRenderer {

    /** Chance that a sample of a full-energy beam shows particles on a given tick. */
    private static final double DRAW_CHANCE = 0.3D;
    private static final double MIN_FRACTION = 0.05D;
    private static final double SPREAD = 0.08D;
    /** UMU of one pulse of a default Iactare (10 UMU over 9 pulses): drawn with {@link #BASE_PARTICLES}. */
    private static final double REFERENCE_ENERGY = 10.0D / 9.0D;
    private static final int BASE_PARTICLES = 2;
    private static final int MAX_PARTICLES = 24;

    private EmissionRenderer() {
    }

    public static void render(ServerLevel level, SpellScene scene, long tick) {
        for (Emission emission : scene.activeAt(tick)) {
            if (emission.kind() != Emission.Kind.BEAM || emission.energy() <= 0.0D) {
                continue;
            }
            double fraction = emission.remainingEnergy() / emission.energy();
            if (fraction < MIN_FRACTION) {
                continue;
            }
            Optional<ParticleOptions> particle = SpellEffects.resolveParticle(
                    VitaElement.fromRuneId(emission.elementRuneId()), emission.elementRuneId());
            if (particle.isEmpty()) {
                continue;
            }
            // Twice the UMU looks like twice the matter (book 4.3.2), until the particle budget runs out.
            int count = (int) Math.max(BASE_PARTICLES,
                    Math.min(MAX_PARTICLES, Math.round(BASE_PARTICLES * emission.energy() / REFERENCE_ENERGY)));
            double spread = SPREAD * Math.sqrt((double) count / BASE_PARTICLES);
            for (Emission.Point point : emission.samples()) {
                if (level.random.nextDouble() < fraction * DRAW_CHANCE) {
                    level.sendParticles(particle.get(), point.x(), point.y(), point.z(), count, spread, spread, spread, 0.01D);
                }
            }
        }
    }
}
