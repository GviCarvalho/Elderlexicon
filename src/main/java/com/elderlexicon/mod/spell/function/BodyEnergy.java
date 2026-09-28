package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.Mana;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaProfile;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.server.level.ServerPlayer;

/**
 * All of one energy the mage's own body holds, for a bare quantum written with no tenet ({@code firmo quantum
 * chronos 0 iactare}: all the earth of the body; {@code vis quantum …}: all its mana). The spirit is literal: it takes
 * all of it, and the body lives with what is left (the Vita's imbalance takes its course until it settles again).
 */
public final class BodyEnergy {

    private BodyEnergy() {
    }

    /** Takes all of {@code element} out of {@code player}'s body and returns how many UMU it was. */
    public static double drawAll(ServerPlayer player, VitaElement element) {
        return draw(player, element, Double.MAX_VALUE);
    }

    /** Takes up to {@code wanted} UMU of {@code element} out of {@code player}'s body; returns how much it took. */
    public static double draw(ServerPlayer player, VitaElement element, double wanted) {
        if (wanted <= 0.0D) {
            return 0.0D;
        }
        if (element == null || element.isBalanced()) {
            return drawMana(player, wanted);
        }
        VitaProfile profile = VitaSystem.fromPlayer(player);
        double held = switch (element) {
            case AQUA -> profile.aqua();
            case AURA -> profile.aura();
            case IGNI -> profile.igni();
            case FIRMO -> profile.firmo();
            default -> 0.0D;
        };
        double taken = Math.min(Math.max(0.0D, held), wanted);
        if (taken > 1.0E-4D) {
            VitaSystem.setElementEnergy(player, element, held - taken);
        }
        return taken;
    }

    /** The mage's mana, its experience, taken (up to {@code wanted} UMU) and turned into UMU of Vis. */
    private static double drawMana(ServerPlayer player, double wanted) {
        double umu = Mana.umuOf(Mana.points(player.experienceLevel, player.experienceProgress));
        if (umu <= wanted) {
            player.setExperienceLevels(0);
            player.setExperiencePoints(0);
            player.totalExperience = 0;
            return umu;
        }
        // Only a part: that many points of experience are spent.
        player.giveExperiencePoints(-(int) Math.min(Integer.MAX_VALUE, Math.ceil(wanted * Mana.XP_PER_UMU)));
        return wanted;
    }
}
