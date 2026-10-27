package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.spelling.server.ServerSpellingController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What the invocation bench made, sent off by the line engraved in it (docs/invocacao-design.md, section 5.1): it is set
 * on the bench, bearing for a moment a mark no one wrote, and the line is read with that mark as the subject already
 * named, its own words as they were written (a mark put before them would join a place written first). So
 * {@code ego ubis vocant} brings it to whoever reads, and {@code ferit ille ubis iactare} throws it at the one they
 * struck, as they would any marked thing.
 */
final class BenchDelivery {

    /** How long what was sent bears the bench's mark, for the line to find it: five seconds. */
    static final int MARKED_TICKS = 100;
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";

    private BenchDelivery() {
    }

    /** Sends {@code sent} off; returns what was set on the bench, to have the mark taken off later ({@link #unmark}). */
    static List<ItemEntity> send(ServerLevel level, BlockPos bench, List<ItemStack> sent, String engraving,
                                 ServerPlayer reader) {
        String mark = freshMark();
        List<ItemEntity> set = new ArrayList<>();
        double y = bench.getY() + 1.05D;
        for (ItemStack thing : sent) {
            ItemEntity lying = new ItemEntity(level, bench.getX() + 0.5D, y, bench.getZ() + 0.5D, thing);
            lying.setDeltaMovement(0.0D, 0.0D, 0.0D);
            lying.setDefaultPickUpDelay();
            level.addFreshEntity(lying);
            MarkHelper.applyMark(lying, mark);
            set.add(lying);
        }
        level.sendParticles(ParticleTypes.ENCHANT, bench.getX() + 0.5D, y, bench.getZ() + 0.5D, 24, 0.3D, 0.2D, 0.3D,
                0.6D);
        level.playSound(null, bench, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.3F);
        ServerSpellingController.getInstance().castBenchEngraving(reader, engraving, mark);
        return set;
    }

    /** What was sent no longer bears the mark it was sent with. */
    static void unmark(List<ItemEntity> sent) {
        for (ItemEntity lying : sent) {
            if (lying.isAlive() && MarkHelper.markForEntity(lying).filter(mark -> mark.startsWith("obra")).isPresent()) {
                MarkHelper.applyMark(lying, null);
            }
        }
    }

    /** A mark no one wrote and no one will guess: {@code obra} and eight letters. */
    private static String freshMark() {
        StringBuilder mark = new StringBuilder("obra");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            mark.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return mark.toString().toLowerCase(Locale.ROOT);
    }
}
