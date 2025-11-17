package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class VocantFunctionHandler implements SpellFunctionHandler {

    private static final double RANGE = 12.0D;
    private static final int SUMMON_DELAY_TICKS = 20; // 1 second delay

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();

        SpellEffects.schedule(level, SUMMON_DELAY_TICKS, () -> {
            if (!SpellEffects.isPlayerValid(player)) {
                return;
            }
            SpellEffects.SpellImpact impact = SpellEffects.findImpact(player, RANGE);
            SpellEffects.spawnSummonEffect(player, element, impact);
            SpellEffects.applyElementEffect(player, element, impact);
        });
    }
}
