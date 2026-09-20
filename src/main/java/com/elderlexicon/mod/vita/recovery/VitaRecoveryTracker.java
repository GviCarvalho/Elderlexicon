package com.elderlexicon.mod.vita.recovery;

import net.minecraft.nbt.CompoundTag;

/**
 * Stores per-player timers/cooldowns for recovery rules.
 */
public final class VitaRecoveryTracker {

    private static final String TAG_SUNLIGHT_TIMER = "sunlightTimer";
    private static final String TAG_HEAT_TIMER = "heatTimer";
    private static final String TAG_WATER_TIMER = "waterTimer";
    private static final String TAG_STILLNESS_TIMER = "stillnessTimer";
    private static final String TAG_LAST_AQUA_LOW = "lastAquaLowTick";
    private static final String TAG_LAST_AQUA_HIGH = "lastAquaHighTick";
    private static final String TAG_LAST_IGNI_LOW = "lastIgniLowTick";
    private static final String TAG_LAST_IGNI_HIGH = "lastIgniHighTick";
    private static final String TAG_LAST_FIRMO_FOOD = "lastFirmoFoodTick";
    private static final String TAG_LAST_AURA_STILL = "lastAuraStillTick";
    private static final String TAG_LAST_AURA_ACTION = "lastAuraActionTick";
    private static final String TAG_LAST_POS_X = "lastPosX";
    private static final String TAG_LAST_POS_Y = "lastPosY";
    private static final String TAG_LAST_POS_Z = "lastPosZ";
    private static final String TAG_HAS_POS = "hasPos";

    private int sunlightTimer;
    private int heatTimer;
    private int waterTimer;
    private int stillnessTimer;
    private long lastAquaLowTick;
    private long lastAquaHighTick;
    private long lastIgniLowTick;
    private long lastIgniHighTick;
    private long lastFirmoFoodTick;
    private long lastAuraStillTick;
    private long lastAuraActionTick;
    private double lastPosX;
    private double lastPosY;
    private double lastPosZ;
    private boolean hasLastPos;

    public int sunlightTimer() {
        return sunlightTimer;
    }

    public void setSunlightTimer(int value) {
        this.sunlightTimer = value;
    }

    public int heatTimer() {
        return heatTimer;
    }

    public void setHeatTimer(int value) {
        this.heatTimer = value;
    }

    public int waterTimer() {
        return waterTimer;
    }

    public void setWaterTimer(int value) {
        this.waterTimer = value;
    }

    public int stillnessTimer() {
        return stillnessTimer;
    }

    public void setStillnessTimer(int value) {
        this.stillnessTimer = value;
    }

    public long lastAquaLowTick() {
        return lastAquaLowTick;
    }

    public void setLastAquaLowTick(long tick) {
        this.lastAquaLowTick = tick;
    }

    public long lastAquaHighTick() {
        return lastAquaHighTick;
    }

    public void setLastAquaHighTick(long tick) {
        this.lastAquaHighTick = tick;
    }

    public long lastIgniLowTick() {
        return lastIgniLowTick;
    }

    public void setLastIgniLowTick(long tick) {
        this.lastIgniLowTick = tick;
    }

    public long lastIgniHighTick() {
        return lastIgniHighTick;
    }

    public void setLastIgniHighTick(long tick) {
        this.lastIgniHighTick = tick;
    }

    public long lastFirmoFoodTick() {
        return lastFirmoFoodTick;
    }

    public void setLastFirmoFoodTick(long tick) {
        this.lastFirmoFoodTick = tick;
    }

    public long lastAuraStillTick() {
        return lastAuraStillTick;
    }

    public void setLastAuraStillTick(long tick) {
        this.lastAuraStillTick = tick;
    }

    public long lastAuraActionTick() {
        return lastAuraActionTick;
    }

    public void setLastAuraActionTick(long tick) {
        this.lastAuraActionTick = tick;
    }

    public double lastPosX() {
        return lastPosX;
    }

    public double lastPosY() {
        return lastPosY;
    }

    public double lastPosZ() {
        return lastPosZ;
    }

    public void setLastPosition(double x, double y, double z) {
        this.lastPosX = x;
        this.lastPosY = y;
        this.lastPosZ = z;
        this.hasLastPos = true;
    }

    public boolean hasLastPosition() {
        return hasLastPos;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_SUNLIGHT_TIMER, sunlightTimer);
        tag.putInt(TAG_HEAT_TIMER, heatTimer);
        tag.putInt(TAG_WATER_TIMER, waterTimer);
        tag.putInt(TAG_STILLNESS_TIMER, stillnessTimer);
        tag.putLong(TAG_LAST_AQUA_LOW, lastAquaLowTick);
        tag.putLong(TAG_LAST_AQUA_HIGH, lastAquaHighTick);
        tag.putLong(TAG_LAST_IGNI_LOW, lastIgniLowTick);
        tag.putLong(TAG_LAST_IGNI_HIGH, lastIgniHighTick);
        tag.putLong(TAG_LAST_FIRMO_FOOD, lastFirmoFoodTick);
        tag.putLong(TAG_LAST_AURA_STILL, lastAuraStillTick);
        tag.putLong(TAG_LAST_AURA_ACTION, lastAuraActionTick);
        tag.putDouble(TAG_LAST_POS_X, lastPosX);
        tag.putDouble(TAG_LAST_POS_Y, lastPosY);
        tag.putDouble(TAG_LAST_POS_Z, lastPosZ);
        tag.putBoolean(TAG_HAS_POS, hasLastPos);
        return tag;
    }

    public void load(CompoundTag tag) {
        sunlightTimer = tag.getInt(TAG_SUNLIGHT_TIMER);
        heatTimer = tag.getInt(TAG_HEAT_TIMER);
        waterTimer = tag.getInt(TAG_WATER_TIMER);
        stillnessTimer = tag.getInt(TAG_STILLNESS_TIMER);
        lastAquaLowTick = tag.getLong(TAG_LAST_AQUA_LOW);
        lastAquaHighTick = tag.getLong(TAG_LAST_AQUA_HIGH);
        lastIgniLowTick = tag.getLong(TAG_LAST_IGNI_LOW);
        lastIgniHighTick = tag.getLong(TAG_LAST_IGNI_HIGH);
        lastFirmoFoodTick = tag.getLong(TAG_LAST_FIRMO_FOOD);
        lastAuraStillTick = tag.getLong(TAG_LAST_AURA_STILL);
        lastAuraActionTick = tag.getLong(TAG_LAST_AURA_ACTION);
        lastPosX = tag.getDouble(TAG_LAST_POS_X);
        lastPosY = tag.getDouble(TAG_LAST_POS_Y);
        lastPosZ = tag.getDouble(TAG_LAST_POS_Z);
        hasLastPos = tag.getBoolean(TAG_HAS_POS);
    }

    public void reset() {
        sunlightTimer = 0;
        heatTimer = 0;
        waterTimer = 0;
        stillnessTimer = 0;
        lastAquaLowTick = 0L;
        lastAquaHighTick = 0L;
        lastIgniLowTick = 0L;
        lastIgniHighTick = 0L;
        lastFirmoFoodTick = 0L;
        lastAuraStillTick = 0L;
        lastAuraActionTick = 0L;
        lastPosX = 0.0D;
        lastPosY = 0.0D;
        lastPosZ = 0.0D;
        hasLastPos = false;
    }
}
