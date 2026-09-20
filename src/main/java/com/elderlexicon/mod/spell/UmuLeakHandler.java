package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionType;
import com.elderlexicon.mod.spelling.item.SpellConduitItem;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Detects sources that never bind to a function and applies UMU leak penalties.
 */
final class UmuLeakHandler {

    private static final String VERTERE_RUNE_ID = "vertere";
    private static final int NAUSEA_PER_LEAK_TICKS = 5 * 20;
    private static final int FIRE_SECONDS = 4;

    private UmuLeakHandler() {
    }

    static void handleLeaks(SpellContext context, List<SpellAction> actions) {
        Objects.requireNonNull(context, "context");
        ServerPlayer player = context.player();
        if (player == null || actions == null || actions.isEmpty()) {
            return;
        }
        List<VitaElement> leakedElements = detectLeaks(actions);
        if (leakedElements.isEmpty()) {
            return;
        }
        leakedElements.forEach(element -> applyElementalPenalty(player, element));
        applyNausea(player, leakedElements.size());
    }

    private static List<VitaElement> detectLeaks(List<SpellAction> actions) {
        Deque<LeakCandidate> pendingSources = new ArrayDeque<>();
        boolean protectNextSource = false;
        for (SpellAction action : actions) {
            if (action == null) {
                continue;
            }
            if (action.type() == SpellActionType.SOURCE) {
                VitaElement element = action.element() == null ? VitaElement.BALANCED : action.element();
                pendingSources.addLast(new LeakCandidate(element, protectNextSource));
                protectNextSource = false;
                continue;
            }
            if (action.type() != SpellActionType.FUNCTION) {
                continue;
            }
            boolean isVertere = isVertere(action.runeId());
            if (!pendingSources.isEmpty()) {
                pendingSources.removeLast();
            }
            protectNextSource = isVertere;
        }

        List<VitaElement> leaks = new ArrayList<>();
        for (LeakCandidate candidate : pendingSources) {
            if (!candidate.protectedTarget()) {
                leaks.add(candidate.element());
            }
        }
        return leaks;
    }

    private static boolean isVertere(String runeId) {
        if (runeId == null) {
            return false;
        }
        return VERTERE_RUNE_ID.equals(runeId.toLowerCase(Locale.ROOT));
    }

    private static void applyElementalPenalty(ServerPlayer player, VitaElement element) {
        if (element == null) {
            return;
        }
        switch (element) {
            case IGNI -> ignitePlayer(player);
            case AQUA -> floodFeet(player);
            case FIRMO -> dropDirt(player);
            case AURA -> burstAir(player);
            default -> {
            }
        }
    }

    private static void ignitePlayer(ServerPlayer player) {
        player.setSecondsOnFire(FIRE_SECONDS);
    }

    private static void floodFeet(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        placeBlockIfPossible(level, pos, Blocks.WATER.defaultBlockState());
    }

    private static void dropDirt(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos headPos = player.blockPosition().above();
        placeBlockIfPossible(level, headPos, Blocks.DIRT.defaultBlockState());
    }

    private static void burstAir(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double dx = (player.getRandom().nextDouble() - 0.5D) * 0.6D;
        double dz = (player.getRandom().nextDouble() - 0.5D) * 0.6D;
        player.push(dx, 0.6D, dz);
        player.hurtMarked = true;
        level.sendParticles(
                ParticleTypes.CLOUD,
                player.getX(),
                player.getY(),
                player.getZ(),
                8,
                0.2D,
                0.1D,
                0.2D,
                0.0D
        );
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS,
                0.3F,
                1.5F
        );
    }

    private static void placeBlockIfPossible(ServerLevel level, BlockPos pos, BlockState newState) {
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && !current.canBeReplaced()) {
            return;
        }
        level.setBlock(pos, newState, Block.UPDATE_ALL);
    }

    private static void applyNausea(ServerPlayer player, int leakCount) {
        if (leakCount <= 0) {
            return;
        }
        if (hasActiveConduit(player)) {
            return;
        }
        int duration = leakCount * NAUSEA_PER_LEAK_TICKS;
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0));
    }

    private static boolean hasActiveConduit(ServerPlayer player) {
        return conduitReady(player.getMainHandItem()) || conduitReady(player.getOffhandItem());
    }

    private static boolean conduitReady(net.minecraft.world.item.ItemStack stack) {
        if (!(stack.getItem() instanceof SpellConduitItem conduit)) {
            return false;
        }
        return conduit.remainingCapacity(stack) > 1.0E-4D;
    }

    private record LeakCandidate(VitaElement element, boolean protectedTarget) {
    }
}
