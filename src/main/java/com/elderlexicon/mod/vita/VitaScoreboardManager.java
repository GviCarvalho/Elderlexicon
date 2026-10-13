package com.elderlexicon.mod.vita;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public final class VitaScoreboardManager {

    private static final String OBJECTIVE_ID = "vita_status";

    private VitaScoreboardManager() {
    }

    public static void update(LivingEntity living, VitaProfile profile) {
        if (!(living instanceof ServerPlayer player) || profile == null) {
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective != null) {
            if (scoreboard.getDisplayObjective(1) == objective) {
                scoreboard.setDisplayObjective(1, null);
            }
            scoreboard.removeObjective(objective);
        }
    }

}
