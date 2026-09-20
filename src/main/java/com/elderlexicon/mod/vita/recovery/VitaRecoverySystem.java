package com.elderlexicon.mod.vita.recovery;

import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import com.elderlexicon.mod.vita.VitaTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Applies recovery rules defined in the Vita recovery plan.
 */
public final class VitaRecoverySystem {

    private static final double EPSILON = 1.0E-4D;
    private static final double SNAP_THRESHOLD = 1.0D;

    private VitaRecoverySystem() {
    }

    public static void tick(ServerPlayer player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        LazyOptional<VitaRecoveryTracker> optional = VitaRecoveryCapability.get(player);
        optional.ifPresent(tracker -> tickInternal(player, tracker));
    }

    public static void handleWaterDrink(ServerPlayer player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        LazyOptional<VitaRecoveryTracker> optional = VitaRecoveryCapability.get(player);
        optional.ifPresent(tracker -> {
            long gameTime = player.serverLevel().getGameTime();
            VitaRecoveryConfig.RecoverySettings cfg = VitaRecoveryConfig.settings();
            if (gameTime - tracker.lastAquaLowTick() < cfg.drinkCooldownTicks()) {
                return;
            }
            if (applyAquaLowPulse(player, cfg)) {
                tracker.setLastAquaLowTick(gameTime);
            }
        });
    }

    public static void handleFirmoFood(ServerPlayer player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        LazyOptional<VitaRecoveryTracker> optional = VitaRecoveryCapability.get(player);
        optional.ifPresent(tracker -> {
            long gameTime = player.serverLevel().getGameTime();
            VitaRecoveryConfig.RecoverySettings cfg = VitaRecoveryConfig.settings();
            if (gameTime - tracker.lastFirmoFoodTick() < cfg.firmoFoodCooldownTicks()) {
                return;
            }
            if (applyFirmoFoodPulse(player, cfg)) {
                tracker.setLastFirmoFoodTick(gameTime);
            }
        });
    }

    public static void handleSleep(ServerPlayer player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        VitaRecoveryConfig.RecoverySettings cfg = VitaRecoveryConfig.settings();
        double baseline = baseline(VitaElement.FIRMO);
        double current = VitaSystem.getLifeEnergy(player, VitaElement.FIRMO);
        double target = baseline - ((baseline - current) * (1.0D - cfg.firmoSleepPercentage()));
        double delta = target - current;
        if (Math.abs(delta) <= EPSILON) {
            return;
        }
        if (delta > 0) {
            VitaSystem.restoreElementEnergy(player, VitaElement.FIRMO, delta);
        } else {
            VitaSystem.consumeElementReserve(player, VitaElement.FIRMO, -delta);
        }
        sendFeedback(player, "Repouso restaurou Firmo.");
    }

    private static void tickInternal(ServerPlayer player, VitaRecoveryTracker tracker) {
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        VitaRecoveryConfig.RecoverySettings cfg = VitaRecoveryConfig.settings();

        updateSunlight(player, tracker, cfg, gameTime);
        updateHeat(player, tracker, cfg, gameTime);
        updateWater(player, tracker, cfg, gameTime);
        updateStillness(player, tracker, cfg, gameTime);
        updateActionTokens(player, tracker, cfg, gameTime);
    }

    private static void updateSunlight(ServerPlayer player,
                                       VitaRecoveryTracker tracker,
                                       VitaRecoveryConfig.RecoverySettings cfg,
                                       long gameTime) {
        if (isInSunlight(player)) {
            tracker.setSunlightTimer(tracker.sunlightTimer() + 1);
            if (tracker.sunlightTimer() >= cfg.sunlightExposureTicks()
                    && gameTime - tracker.lastAquaHighTick() >= cfg.sunlightCadenceTicks()) {
                if (applyAquaHighPulse(player, cfg)) {
                    tracker.setLastAquaHighTick(gameTime);
                    tracker.setSunlightTimer(0);
                }
            }
        } else {
            tracker.setSunlightTimer(0);
        }
    }

    private static void updateHeat(ServerPlayer player,
                                   VitaRecoveryTracker tracker,
                                   VitaRecoveryConfig.RecoverySettings cfg,
                                   long gameTime) {
        if (isNearHeatSource(player, cfg.heatRadius())) {
            tracker.setHeatTimer(tracker.heatTimer() + 1);
            if (tracker.heatTimer() >= cfg.heatProximityTicks()
                    && gameTime - tracker.lastIgniLowTick() >= cfg.heatCadenceTicks()) {
                if (applyIgniLowPulse(player, cfg)) {
                    tracker.setLastIgniLowTick(gameTime);
                    tracker.setHeatTimer(0);
                }
            }
        } else {
            tracker.setHeatTimer(0);
        }
    }

    private static void updateWater(ServerPlayer player,
                                    VitaRecoveryTracker tracker,
                                    VitaRecoveryConfig.RecoverySettings cfg,
                                    long gameTime) {
        if (isCooling(player)) {
            tracker.setWaterTimer(tracker.waterTimer() + 1);
            if (tracker.waterTimer() >= cfg.waterExposureTicks()
                    && gameTime - tracker.lastIgniHighTick() >= cfg.waterCadenceTicks()) {
                if (applyIgniHighPulse(player, cfg)) {
                    tracker.setLastIgniHighTick(gameTime);
                    tracker.setWaterTimer(0);
                }
            }
        } else {
            tracker.setWaterTimer(0);
        }
    }

    private static void updateStillness(ServerPlayer player,
                                        VitaRecoveryTracker tracker,
                                        VitaRecoveryConfig.RecoverySettings cfg,
                                        long gameTime) {
        double distance = tracker.hasLastPosition()
                ? player.position().distanceToSqr(tracker.lastPosX(), tracker.lastPosY(), tracker.lastPosZ())
                : Double.MAX_VALUE;
        if (!tracker.hasLastPosition()) {
            tracker.setLastPosition(player.getX(), player.getY(), player.getZ());
        }
        if (distance <= 0.0001D && !player.isSprinting() && !player.isSwimming() && !player.isUsingItem()) {
            tracker.setStillnessTimer(tracker.stillnessTimer() + 1);
            if (tracker.stillnessTimer() >= cfg.stillnessTicksRequired()
                    && gameTime - tracker.lastAuraStillTick() >= cfg.stillnessCadenceTicks()) {
                if (applyAuraStillness(player, cfg)) {
                    tracker.setLastAuraStillTick(gameTime);
                }
            }
        } else {
            tracker.setStillnessTimer(0);
            tracker.setLastPosition(player.getX(), player.getY(), player.getZ());
        }
    }

    private static void updateActionTokens(ServerPlayer player,
                                           VitaRecoveryTracker tracker,
                                           VitaRecoveryConfig.RecoverySettings cfg,
                                           long gameTime) {
        boolean action = player.isSprinting() || player.isSwimming() || player.swinging;
        if (!action) {
            return;
        }
        if (gameTime - tracker.lastAuraActionTick() < cfg.actionCooldownTicks()) {
            return;
        }
        if (applyAuraActionDrain(player, cfg)) {
            tracker.setLastAuraActionTick(gameTime);
        }
    }

    private static boolean applyAquaLowPulse(ServerPlayer player, VitaRecoveryConfig.RecoverySettings cfg) {
        double deficit = deficit(player, VitaElement.AQUA);
        if (deficit <= EPSILON) {
            return false;
        }
        double base = deficit * cfg.aquaLowPercent();
        double delta = scaledPulse(base, cfg.aquaLowMultiplier());
        if (delta <= EPSILON) {
            return false;
        }
        delta = clampDelta(delta, deficit);
        VitaSystem.restoreElementEnergy(player, VitaElement.AQUA, delta);
        snapToBaselineIfClose(player, VitaElement.AQUA);
        sendFeedback(player, "Água equilibra Aqua.");
        return true;
    }

    private static boolean applyAquaHighPulse(ServerPlayer player, VitaRecoveryConfig.RecoverySettings cfg) {
        double excess = excess(player, VitaElement.AQUA);
        if (excess <= EPSILON) {
            return false;
        }
        double base = -excess * cfg.aquaHighPercent();
        double delta = scaledPulse(base, cfg.aquaHighMultiplier());
        if (Math.abs(delta) <= EPSILON) {
            return false;
        }
        delta = clampDelta(delta, excess);
        if (delta < 0) {
            VitaSystem.consumeElementReserve(player, VitaElement.AQUA, -delta);
        } else {
            VitaSystem.restoreElementEnergy(player, VitaElement.AQUA, delta);
        }
        snapToBaselineIfClose(player, VitaElement.AQUA);
        sendFeedback(player, "Sol evapora excesso de Aqua.");
        return true;
    }

    private static boolean applyIgniLowPulse(ServerPlayer player, VitaRecoveryConfig.RecoverySettings cfg) {
        double deficit = deficit(player, VitaElement.IGNI);
        if (deficit <= EPSILON) {
            return false;
        }
        double base = deficit * cfg.igniLowPercent();
        double delta = scaledPulse(base, cfg.igniLowMultiplier());
        if (delta <= EPSILON) {
            return false;
        }
        delta = clampDelta(delta, deficit);
        VitaSystem.restoreElementEnergy(player, VitaElement.IGNI, delta);
        snapToBaselineIfClose(player, VitaElement.IGNI);
        sendFeedback(player, "Calor reacende Igni.");
        return true;
    }

    private static boolean applyIgniHighPulse(ServerPlayer player, VitaRecoveryConfig.RecoverySettings cfg) {
        double excess = excess(player, VitaElement.IGNI);
        if (excess <= EPSILON) {
            return false;
        }
        double base = -excess * cfg.igniHighPercent();
        double delta = scaledPulse(base, cfg.igniHighMultiplier());
        if (Math.abs(delta) <= EPSILON) {
            return false;
        }
        delta = clampDelta(delta, excess);
        VitaSystem.consumeElementReserve(player, VitaElement.IGNI, -delta);
        snapToBaselineIfClose(player, VitaElement.IGNI);
        sendFeedback(player, "Água resfria Igni.");
        return true;
    }

    private static boolean applyFirmoFoodPulse(ServerPlayer player, VitaRecoveryConfig.RecoverySettings cfg) {
        double deficit = deficit(player, VitaElement.FIRMO);
        if (deficit <= EPSILON) {
            return false;
        }
        double base = deficit * cfg.firmoFoodPercent();
        double delta = scaledPulse(base, cfg.firmoFoodMultiplier());
        if (delta <= EPSILON) {
            return false;
        }
        delta = clampDelta(delta, deficit);
        VitaSystem.restoreElementEnergy(player, VitaElement.FIRMO, delta);
        snapToBaselineIfClose(player, VitaElement.FIRMO);
        sendFeedback(player, "Alimento repõe Firmo.");
        return true;
    }

    private static boolean applyAuraStillness(ServerPlayer player, VitaRecoveryConfig.RecoverySettings cfg) {
        double deficit = deficit(player, VitaElement.AURA);
        if (deficit <= EPSILON) {
            return false;
        }
        double base = deficit * cfg.auraStillPercent();
        double delta = scaledPulse(base, cfg.auraStillMultiplier());
        if (delta <= EPSILON) {
            return false;
        }
        delta = clampDelta(delta, deficit);
        VitaSystem.restoreElementEnergy(player, VitaElement.AURA, delta);
        snapToBaselineIfClose(player, VitaElement.AURA);
        sendFeedback(player, "Calma restaura Aura.");
        return true;
    }

    private static boolean applyAuraActionDrain(ServerPlayer player, VitaRecoveryConfig.RecoverySettings cfg) {
        double excess = excess(player, VitaElement.AURA);
        if (excess <= EPSILON) {
            return false;
        }
        double base = -excess * cfg.auraActionPercent();
        double delta = scaledPulse(base, cfg.auraActionMultiplier());
        if (Math.abs(delta) <= EPSILON) {
            return false;
        }
        delta = clampDelta(delta, excess);
        VitaSystem.consumeElementReserve(player, VitaElement.AURA, -delta);
        snapToBaselineIfClose(player, VitaElement.AURA);
        sendFeedback(player, "Esforço consome excesso de Aura.");
        return true;
    }

    private static double scaledPulse(double base, double multiplier) {
        return base * multiplier;
    }

    private static double baseline(VitaElement element) {
        return switch (element) {
            case AQUA -> VitaSystem.DEFAULT_AQUA;
            case AURA -> VitaSystem.DEFAULT_AURA;
            case IGNI -> VitaSystem.DEFAULT_IGNI;
            case FIRMO -> VitaSystem.DEFAULT_FIRMO;
            case BALANCED -> VitaSystem.DEFAULT_TOTAL;
        };
    }

    private static double deficit(ServerPlayer player, VitaElement element) {
        double baseline = baseline(element);
        double current = VitaSystem.getLifeEnergy(player, element);
        return Math.max(0.0D, baseline - current);
    }

    private static double excess(ServerPlayer player, VitaElement element) {
        double baseline = baseline(element);
        double current = VitaSystem.getLifeEnergy(player, element);
        return Math.max(0.0D, current - baseline);
    }

    private static boolean isInSunlight(ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        return level.canSeeSky(pos)
                && level.getBrightness(LightLayer.SKY, pos) >= 13
                && !player.isInWaterOrRain();
    }

    private static boolean isCooling(ServerPlayer player) {
        return player.isInWater() || player.isEyeInFluid(FluidTags.WATER) || player.isInWaterOrRain();
    }

    private static boolean isNearHeatSource(ServerPlayer player, int radius) {
        Level level = player.level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos origin = player.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(VitaTags.Blocks.HEAT_SOURCES) || state.is(BlockTags.FIRE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void sendFeedback(ServerPlayer player, String message) {
        if (!VitaRecoveryConfig.settings().feedbackMessages()) {
            return;
        }
        player.displayClientMessage(Component.literal("[Vita] " + message), true);
    }

    private static double clampDelta(double delta, double remaining) {
        double maxRemaining = remaining <= EPSILON ? Double.POSITIVE_INFINITY : remaining;
        double max = Math.min(5.0D, maxRemaining);
        if (delta > max) {
            return max;
        }
        if (delta < -max) {
            return -max;
        }
        return delta;
    }

    private static void snapToBaselineIfClose(ServerPlayer player, VitaElement element) {
        double baseline = baseline(element);
        double current = VitaSystem.getLifeEnergy(player, element);
        if (Math.abs(current - baseline) <= SNAP_THRESHOLD) {
            VitaSystem.forceSetElement(player, element, baseline);
        }
    }
}
