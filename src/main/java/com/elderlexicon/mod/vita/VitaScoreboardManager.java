package com.elderlexicon.mod.vita;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Locale;
import java.util.Objects;

/**
 * Handles the presentation of Vita metrics through the vanilla scoreboard UI.
 */
public final class VitaScoreboardManager {

    private static final String OBJECTIVE_ID = "vita_status";
    private static final Component TITLE = Component.literal("Vita Status");

    private VitaScoreboardManager() {
    }

    public static void update(ServerPlayer player, VitaProfile profile) {
        if (player == null || profile == null) {
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE_ID,
                    Objects.requireNonNull(ObjectiveCriteria.DUMMY),
                    Objects.requireNonNull(TITLE),
                    ObjectiveCriteria.RenderType.INTEGER);
        } else if (!Objects.requireNonNull(objective.getDisplayName()).equals(TITLE)) {
            scoreboard.removeObjective(objective);
            objective = scoreboard.addObjective(OBJECTIVE_ID,
                    Objects.requireNonNull(ObjectiveCriteria.DUMMY),
                    Objects.requireNonNull(TITLE),
                    ObjectiveCriteria.RenderType.INTEGER);
        }

        setScore(scoreboard, objective, "Total", profile.totalUmu());
        setScore(scoreboard, objective, "Aqua", profile.aqua());
        setScore(scoreboard, objective, "Aura", profile.aura());
        setScore(scoreboard, objective, "Igni", profile.igni());
        setScore(scoreboard, objective, "Firmo", profile.firmo());

        scoreboard.setDisplayObjective(1, objective);
    }

    private static void setScore(Scoreboard scoreboard, Objective objective, String label, double value) {
        String entry = String.format(Locale.ROOT, "%s", label);
        Score score = scoreboard.getOrCreatePlayerScore(Objects.requireNonNull(entry), Objects.requireNonNull(objective));
        score.setScore((int) Math.round(value));
    }
}
