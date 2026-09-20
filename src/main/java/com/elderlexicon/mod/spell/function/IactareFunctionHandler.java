package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class IactareFunctionHandler implements SpellFunctionHandler {

    private static final double RANGE = 20.0D;
    private static final int CAST_DURATION_TICKS = 40; // 2 seconds default channel
    private static final int CAST_STEP_TICKS = 5;
    private static final int PULSES = CAST_DURATION_TICKS / CAST_STEP_TICKS + 1;
    private static final double BEAM_LENGTH = 8.0D; // matches the drawn projectile (16 segments x 0.5)

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();

        for (int elapsed = 0; elapsed <= CAST_DURATION_TICKS; elapsed += CAST_STEP_TICKS) {
            final int delay = elapsed;
                SpellEffects.schedule(level, delay, () -> {
                    if (!SpellEffects.isPlayerValid(player)) {
                        return;
                    }
                    SpellEffects.spawnProjectile(player, element, context.elementRuneId());
                    EmissionRecorder.beamFromCaster(context, element, BEAM_LENGTH,
                            EmissionRecorder.DEFAULT_QUANTITY_UMU / PULSES, CAST_STEP_TICKS);
                });
            }

        SpellEffects.schedule(level, CAST_DURATION_TICKS, () -> {
            if (!SpellEffects.isPlayerValid(player)) {
                return;
            }
            SpellEffects.SpellImpact impact = SpellEffects.findImpact(player, RANGE);
            SpellEffects.applyElementEffect(player, element, context.elementRuneId(), impact);
        });
    }
}
