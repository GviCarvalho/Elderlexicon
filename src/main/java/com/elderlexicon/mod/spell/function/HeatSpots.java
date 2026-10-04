package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.nature.NatureWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Condensed heat in the world (docs/condensacao-design.md): where a condensed fire strikes or is invoked, all the fire it
 * gathered is let into the air there at once, as agitation, and the drives do the rest (docs/particulas-design.md,
 * stage 9): a flame, stone melting into lava, water boiling away, lightning where the air is plasma. A strike hot
 * enough to be plasma also bursts where it strikes.
 */
public final class HeatSpots {

    private HeatSpots() {
    }

    /** A condensed fire of {@code heat} (the UMU of fire it gathered) strikes at {@code at}. */
    static void strike(ServerLevel level, ServerPlayer caster, Vec3 at, double heat) {
        if (heat <= 0.0D) {
            return;
        }
        if (Heat.band(heat) == Heat.Band.PLASMA) {
            level.explode(caster, at.x, at.y, at.z, Heat.burst(heat), true, Impacts.explosions());
        }
        BlockPos pos = BlockPos.containing(at);
        if (!NatureWorld.fire(level, pos, heat)) {
            NatureWorld.fire(level, pos.above(), heat); // struck into a wall: into the air before it
        }
    }
}
