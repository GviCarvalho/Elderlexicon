package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Whoever casts a spell (docs/feiticeiro-design.md, 8): any living being, not only a player. The language is the same for
 * all of them; what differs is what the caster is made of:
 * <ul>
 *   <li>its <b>Vis</b>: a player's is their experience (ten points a UMU); any other being has the Anchor of the
 *       Hundred (book 3.2), a reserve of 100 UMU that the body fills back slowly once spent;</li>
 *   <li>what it is <b>told</b>: the spirit speaks to a player; a creature hears nothing;</li>
 *   <li>what only a player has: food to pay with, a creative mode, a client to draw things for.</li>
 * </ul>
 */
public final class Casters {

    /** The Anchor of the Hundred (book 3.2): every living being's reserve of Vis, whole. */
    public static final double ANCHOR_UMU = 100.0D;
    /**
     * How fast a creature's body fills its reserve back, in UMU a second (book 3.2: "lentamente", through breath, food
     * and rest): a spent anchor is whole again in a little over three minutes.
     */
    public static final double VIS_REGEN_PER_SECOND = 0.5D;
    private static final String VIS_TAG = ElderLexicon.MODID + ":vis";
    private static final String VIS_VALUE = "umu";
    private static final String VIS_TIME = "at";
    private static final double EPSILON = 1.0E-4D;

    private Casters() {
    }

    /** The level the caster is in (a caster is always on the server). */
    public static ServerLevel level(LivingEntity caster) {
        return (ServerLevel) caster.level();
    }

    /** The caster as a player, when it is one. */
    public static Optional<ServerPlayer> player(LivingEntity caster) {
        return caster instanceof ServerPlayer player ? Optional.of(player) : Optional.empty();
    }

    /** Whether the caster is still there to cast through: in the world and alive. */
    public static boolean present(LivingEntity caster) {
        return caster != null && !caster.isRemoved() && caster.isAlive();
    }

    /** Whether the caster pays nothing (a player in creative mode). */
    public static boolean creative(LivingEntity caster) {
        return caster instanceof ServerPlayer player && player.isCreative();
    }

    /** The spirit says something to the caster, in the chat; a creature hears nothing. */
    public static void tell(LivingEntity caster, Component message) {
        if (caster instanceof ServerPlayer player) {
            player.sendSystemMessage(message);
        }
    }

    /** The spirit says something to the caster, above the hotbar; a creature hears nothing. */
    public static void status(LivingEntity caster, Component message) {
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(message, true);
        }
    }

    /** The hurt a caster's spell does when it strikes: a player's attack, or a creature's. */
    public static DamageSource strike(LivingEntity caster) {
        return caster instanceof Player player ? caster.damageSources().playerAttack(player)
                : caster.damageSources().mobAttack(caster);
    }

    // ------------------------------------------------------------------ Vis

    /** How much Vis the caster holds, in UMU: a player's experience, or a creature's anchor less what it spent. */
    public static double vis(LivingEntity caster) {
        if (caster instanceof Player player) {
            return Mana.umuOf(Mana.points(player.experienceLevel, player.experienceProgress));
        }
        CompoundTag tag = caster.getPersistentData().getCompound(VIS_TAG);
        if (!tag.contains(VIS_VALUE)) {
            return ANCHOR_UMU;
        }
        double seconds = Math.max(0L, caster.level().getGameTime() - tag.getLong(VIS_TIME)) / 20.0D;
        return Math.min(ANCHOR_UMU, tag.getDouble(VIS_VALUE) + seconds * VIS_REGEN_PER_SECOND);
    }

    /** Takes up to {@code wanted} UMU of the caster's Vis; returns how much it took. */
    public static double takeVis(LivingEntity caster, double wanted) {
        if (wanted <= EPSILON) {
            return 0.0D;
        }
        double held = vis(caster);
        if (caster instanceof ServerPlayer player) {
            if (held <= wanted) {
                player.setExperienceLevels(0);
                player.setExperiencePoints(0);
                player.totalExperience = 0;
                return held;
            }
            // Only a part: that many points of experience are spent.
            player.giveExperiencePoints(-(int) Math.min(Integer.MAX_VALUE, Math.ceil(wanted * Mana.XP_PER_UMU)));
            return wanted;
        }
        double taken = Math.min(held, wanted);
        setVis(caster, held - taken);
        return taken;
    }

    /** Gives the caster Vis: a player as experience, a creature into its anchor, never past it. */
    public static void giveVis(LivingEntity caster, double umu) {
        if (umu <= EPSILON) {
            return;
        }
        if (caster instanceof Player player) {
            player.giveExperiencePoints((int) Math.round(umu * Mana.XP_PER_UMU));
            return;
        }
        setVis(caster, Math.min(ANCHOR_UMU, vis(caster) + umu));
    }

    private static void setVis(LivingEntity creature, double umu) {
        if (umu >= ANCHOR_UMU - EPSILON) {
            creature.getPersistentData().remove(VIS_TAG); // whole again: nothing to keep
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putDouble(VIS_VALUE, Math.max(0.0D, umu));
        tag.putLong(VIS_TIME, creature.level().getGameTime());
        creature.getPersistentData().put(VIS_TAG, tag);
    }
}
