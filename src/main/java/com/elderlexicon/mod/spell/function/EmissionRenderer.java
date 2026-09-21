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
            for (Emission.Point point : emission.samples()) {
                if (level.random.nextDouble() < fraction * DRAW_CHANCE) {
                    level.sendParticles(particle.get(), point.x(), point.y(), point.z(), 2, SPREAD, SPREAD, SPREAD, 0.01D);
                }
            }
        }
    }
}
