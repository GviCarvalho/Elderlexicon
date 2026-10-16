package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;

/**
 * Broken bones (docs/vita-sintomas-design.md, "A fratura"): the one sign of the body that stays. A fall or a crush can
 * break a leg, a hard blow an arm; the chance grows with the hurt and with how brittle the bones are (whole bones almost
 * never break). A bone mends only while the bones are full: a day of the game, three times faster asleep, ten times with
 * earth put in past full. It is kept with the player and ends with their death.
 */
public final class Fractures {

    private static final String STORAGE_KEY = ElderLexicon.MODID + "_bones";
    private static final String TAG_LEG = "leg";
    private static final String TAG_ARM = "arm";
    /** A day of the game, in ticks of mending. */
    public static final int MEND_TICKS = 24000;
    /** The bones must hold this share of their full to mend at all. */
    private static final double MENDING_LEVEL = 0.9D;

    /** Which bones are broken. */
    public record Bones(boolean leg, boolean arm) {
        public static final Bones WHOLE = new Bones(false, false);
    }

    private Fractures() {
    }

    public static Bones of(Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(STORAGE_KEY);
        return new Bones(tag.getInt(TAG_LEG) > 0, tag.getInt(TAG_ARM) > 0);
    }

    /** How many ticks of mending the leg ({@code leg}) or the arm of {@code player} still needs (0: whole). */
    public static int mendingLeft(Player player, boolean leg) {
        return player.getPersistentData().getCompound(STORAGE_KEY).getInt(leg ? TAG_LEG : TAG_ARM);
    }

    /** A tick of the bones: the broken ones mend, as fast as the body lets them. */
    static Bones tick(Player player, VitaData data) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(STORAGE_KEY)) {
            return Bones.WHOLE;
        }
        CompoundTag tag = persistent.getCompound(STORAGE_KEY);
        double full = data.full(VitaElement.FIRMO);
        double level = full <= 1.0E-6D ? 1.0D : data.get(VitaElement.FIRMO) / full;
        int rate = 0;
        if (level >= MENDING_LEVEL) {
            rate = (player.isSleeping() ? 3 : 1) * (level > 1.0D + 1.0E-3D ? 10 : 1);
        }
        int leg = Math.max(0, tag.getInt(TAG_LEG) - rate);
        int arm = Math.max(0, tag.getInt(TAG_ARM) - rate);
        if (leg == 0 && arm == 0) {
            persistent.remove(STORAGE_KEY);
            return Bones.WHOLE;
        }
        tag.putInt(TAG_LEG, leg);
        tag.putInt(TAG_ARM, arm);
        persistent.put(STORAGE_KEY, tag);
        return new Bones(leg > 0, arm > 0);
    }

    /**
     * A hurt of {@code amount} reached the bones of {@code player}: it may break one. Returns whether it did.
     */
    static boolean strike(Player player, DamageSource source, float amount) {
        double fragility = Symptoms.fragility(player);
        boolean leg;
        double chance;
        if (crushes(source)) {
            // A fall of ten blocks breaks a whole leg about once in eighty; the most brittle, two times in five.
            leg = true;
            chance = Mth.clamp((amount - 3.0D) / 10.0D, 0.0D, 1.0D) * (0.03D + 0.97D * fragility);
        } else if (strikes(source)) {
            // A blow breaks only a brittle arm.
            leg = false;
            chance = Mth.clamp((amount - 4.0D) / 12.0D, 0.0D, 1.0D) * fragility;
        } else {
            return false;
        }
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return false;
        }
        breakBone(player, leg);
        return true;
    }

    /** Breaks a leg ({@code leg}) or an arm of {@code player}; one already broken starts its mending over. */
    public static void breakBone(Player player, boolean leg) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag tag = persistent.getCompound(STORAGE_KEY);
        tag.putInt(leg ? TAG_LEG : TAG_ARM, MEND_TICKS);
        persistent.put(STORAGE_KEY, tag);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BONE_BLOCK_BREAK,
                SoundSource.PLAYERS, 1.0F, 0.6F + player.getRandom().nextFloat() * 0.2F);
        Symptoms.pain(player);
    }

    /** Every bone whole again (a command, a test). */
    public static void mendAll(Player player) {
        player.getPersistentData().remove(STORAGE_KEY);
    }

    /** The bones carried over to the same player in a new body that was not born of a death (back from the End). */
    static void carry(Player from, Player to) {
        CompoundTag tag = from.getPersistentData().getCompound(STORAGE_KEY);
        if (!tag.isEmpty()) {
            to.getPersistentData().put(STORAGE_KEY, tag.copy());
        }
    }

    private static boolean crushes(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FALLING_ANVIL)
                || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_STALACTITE)
                || source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.FLY_INTO_WALL);
    }

    private static boolean strikes(DamageSource source) {
        return source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.PLAYER_ATTACK);
    }
}
