package com.elderlexicon.mod.vita.damage;

import com.elderlexicon.mod.vita.VitaElement;
import com.google.common.collect.Maps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Tracks how much Vita delta has been applied per damage source in a sliding window so caps can be enforced.
 * Serialized via the player capability to survive deaths/logouts.
 */
public final class DamageAccumulator {

    private static final String TAG_WINDOWS = "windows";
    private static final String TAG_SOURCE = "source";
    private static final String TAG_ELEMENT = "element";
    private static final String TAG_WINDOW_START = "windowStart";
    private static final String TAG_LAST_SEEN = "lastSeen";
    private static final String TAG_APPLIED = "applied";
    private static final int MAX_TRACKED_SOURCES = 32;

    private final Map<ResourceLocation, SourceWindow> windows = Maps.newHashMap();

    /**
     * Clears all sliding window information (used when player respawns fresh).
     */
    public void clear() {
        windows.clear();
    }

    /**
     * Records a sample for the given source and element, tracking how much Vita delta has been applied within the
     * provided time window. Returns the total accumulated delta in the active window after recording.
     */
    public double recordSample(ResourceLocation sourceId,
                               VitaElement element,
                               double rawDamage,
                               double vitaDelta,
                               long gameTime,
                               long windowTicks) {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(element, "element");
        DamageTelemetry.record(sourceId, rawDamage, vitaDelta);
        SourceWindow window = windows.computeIfAbsent(sourceId, key -> new SourceWindow(element, gameTime));
        window.resetIfExpired(gameTime, windowTicks);
        window.apply(vitaDelta, gameTime);
        enforceLimit();
        return window.applied;
    }

    /**
     * Returns how much Vita delta has been accumulated for the source in the active window duration.
     */
    public double getAccumulated(ResourceLocation sourceId, long gameTime, long windowTicks) {
        SourceWindow window = windows.get(sourceId);
        if (window == null) {
            return 0.0D;
        }
        if (window.resetIfExpired(gameTime, windowTicks)) {
            if (window.isEmpty()) {
                windows.remove(sourceId);
            }
            return 0.0D;
        }
        return window.applied;
    }

    /**
     * Removes any windows whose last-seen tick is far in the past to avoid leaking memory.
     */
    public void purge(long gameTime, long expirationTicks) {
        windows.values().removeIf(window -> gameTime - window.lastSeenTick > expirationTicks);
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, SourceWindow> entry : windows.entrySet()) {
            SourceWindow window = entry.getValue();
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_SOURCE, entry.getKey().toString());
            tag.putString(TAG_ELEMENT, window.element.name());
            tag.putLong(TAG_WINDOW_START, window.windowStartTick);
            tag.putLong(TAG_LAST_SEEN, window.lastSeenTick);
            tag.putDouble(TAG_APPLIED, window.applied);
            list.add(tag);
        }
        root.put(TAG_WINDOWS, list);
        return root;
    }

    public void load(CompoundTag tag) {
        windows.clear();
        if (tag == null || !tag.contains(TAG_WINDOWS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(TAG_WINDOWS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation sourceId = ResourceLocation.tryParse(entry.getString(TAG_SOURCE));
            VitaElement element;
            try {
                element = VitaElement.valueOf(entry.getString(TAG_ELEMENT));
            } catch (IllegalArgumentException e) {
                element = VitaElement.BALANCED;
            }
            long windowStart = entry.getLong(TAG_WINDOW_START);
            long lastSeen = entry.getLong(TAG_LAST_SEEN);
            double applied = entry.getDouble(TAG_APPLIED);
            if (sourceId == null) {
                continue;
            }
            SourceWindow window = new SourceWindow(element, windowStart);
            window.lastSeenTick = lastSeen;
            window.applied = applied;
            windows.put(sourceId, window);
        }
    }

    private void enforceLimit() {
        if (windows.size() <= MAX_TRACKED_SOURCES) {
            return;
        }
        while (windows.size() > MAX_TRACKED_SOURCES) {
            ResourceLocation oldest = windows.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().lastSeenTick))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest == null) {
                break;
            }
            windows.remove(oldest);
        }
    }

    private static final class SourceWindow {
        private final VitaElement element;
        private long windowStartTick;
        private long lastSeenTick;
        private double applied;

        private SourceWindow(VitaElement element, long windowStartTick) {
            this.element = element;
            this.windowStartTick = windowStartTick;
            this.lastSeenTick = windowStartTick;
            this.applied = 0.0D;
        }

        private void apply(double delta, long tick) {
            this.applied += delta;
            this.lastSeenTick = tick;
        }

        private boolean resetIfExpired(long tick, long windowTicks) {
            if (windowTicks <= 0L) {
                return false;
            }
            if (tick - windowStartTick >= windowTicks) {
                windowStartTick = tick;
                lastSeenTick = tick;
                applied = 0.0D;
                return true;
            }
            return false;
        }

        private boolean isEmpty() {
            return Math.abs(applied) <= 1.0E-4D;
        }
    }
}
