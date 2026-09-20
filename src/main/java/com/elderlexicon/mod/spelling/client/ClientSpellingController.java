package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spelling.config.SpellingClientConfig;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.network.ServerSpellCastResultPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Owns the client-side state machine for the Spelling mechanic.
 */
public final class ClientSpellingController {

    public enum State {
        IDLE,
        RECORDING
    }

    private static final long RESULT_MESSAGE_DURATION_MS = 2_000L;
    private static final long SLOT_FLASH_DURATION_MS = 350L;
    private static final long SPELL_LOG_DURATION_MS = 8_000L;
    private static final int SPELL_LOG_LIMIT = 4;

    private static final ClientSpellingController INSTANCE = new ClientSpellingController();

    private final RuneBuffer buffer = new RuneBuffer();
    private final SpellingRepertoire repertoire = new SpellingRepertoire();
    private final SpellHintTracker hintTracker = new SpellHintTracker(SpellingClientConfig.hintHistorySize);
    private final long[] slotFlashExpiryMs = new long[SpellingRepertoire.SLOT_COUNT];
    private final Deque<SpellLogEntry> overlayLogs = new ArrayDeque<>();

    private State state = State.IDLE;
    private long activationTimestampMs = 0L;
    private long activeWindowMs = 2_000L;
    private long cooldownEndTimestampMs = 0L;
    private long lastCooldownDurationMs = 0L;

    private Component lastResultMessage = Component.empty();
    private long lastResultTimestampMs = 0L;
    private boolean lastResultSuccess = false;

    private boolean spellingKeyHeld = false;
    private long spellingKeyDownTimestamp = 0L;
    private boolean holdOpenedEditor = false;
    private int lockedHotbarSlot = -1;

    private ClientSpellingController() {
        Arrays.fill(slotFlashExpiryMs, 0L);
    }

    public static ClientSpellingController getInstance() {
        return INSTANCE;
    }

    public boolean isRecording() {
        return state == State.RECORDING;
    }

    public void handleSpellingKeyPressed(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (state == State.RECORDING) {
            finalizeSequence();
            return;
        }

        if (minecraft.screen != null) {
            return;
        }

        spellingKeyHeld = true;
        spellingKeyDownTimestamp = Util.getMillis();
        holdOpenedEditor = false;
    }

    public void handleSpellingKeyReleased(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!spellingKeyHeld) {
            return;
        }
        boolean openEditorTriggered = holdOpenedEditor;
        spellingKeyHeld = false;
        holdOpenedEditor = false;

        if (!openEditorTriggered && state != State.RECORDING && minecraft.screen == null) {
            if (isOnCooldown()) {
                notifyCooldown(minecraft);
                return;
            }
            startRecording();
        }
    }

    public void openRepertoireEditor(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen instanceof SpellingRepertoireScreen screen) {
            screen.onClose();
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        if (state == State.RECORDING) {
            reset();
        }
        spellingKeyHeld = false;
        holdOpenedEditor = false;
        minecraft.setScreen(new SpellingRepertoireScreen(this));
    }

    private static final Set<Item> IMPROVISED_BASE_ITEMS = Set.of(
            Items.STICK,
            Items.BONE,
            Items.BAMBOO,
            Items.BLAZE_ROD,
            Items.WRITABLE_BOOK
    );

    private void startRecording() {
        if (isOnCooldown()) {
            return;
        }
        requestConduitConversion();
        lockActiveHotbarSlot();
        buffer.clear();
        state = State.RECORDING;
        activationTimestampMs = Util.getMillis();
        activeWindowMs = baseWindowMs();
    }
    private void lockActiveHotbarSlot() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            lockedHotbarSlot = -1;
            return;
        }
        lockedHotbarSlot = player.getInventory().selected;
    }

    private void maintainLockedSlot(Player player) {
        if (state != State.RECORDING || lockedHotbarSlot < 0) {
            return;
        }
        if (player.getInventory().selected != lockedHotbarSlot) {
            player.getInventory().selected = lockedHotbarSlot;
        }
    }

    private void requestConduitConversion() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        if (isConduitBase(main)) {
            SpellingNetwork.requestImprovisedWand(InteractionHand.MAIN_HAND);
            return;
        }
        ItemStack off = player.getOffhandItem();
        if (isConduitBase(off)) {
            SpellingNetwork.requestImprovisedWand(InteractionHand.OFF_HAND);
        }
    }

    private boolean isConduitBase(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return IMPROVISED_BASE_ITEMS.contains(stack.getItem());
    }

    public boolean handleHotbarInput(int slotIndex) {
        if (state != State.RECORDING) {
            return false;
        }

        return repertoire.runeForSlot(slotIndex)
            .map(rune -> {
                boolean appended = buffer.append(rune);
                if (appended) {
                    flashSlot(slotIndex);
                    playTapSound();
                } else {
                    playDenySound();
                }
                return appended;
            })
            .orElse(true);
    }

    public void tick(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            reset();
            return;
        }

        maintainLockedSlot(player);

        if (!stateEqualsRecordingAndUpdateWindow(minecraft)) {
            maybeOpenEditor(minecraft);
            return;
        }

        long elapsed = Util.getMillis() - activationTimestampMs;
        if (elapsed >= activeWindowMs) {
            finalizeSequence();
        }
    }

    private void finalizeSequence() {
        List<String> snapshot = buffer.entries();
        SpellingNetwork.sendSpellCast(snapshot, activationTimestampMs);
        reset();
    }

    public double remainingSeconds() {
        if (state != State.RECORDING) {
            return 0D;
        }
        long remaining = Math.max(0L, activeWindowMs - (Util.getMillis() - activationTimestampMs));
        return remaining / 1000.0D;
    }

    public String previewSequence() {
        return buffer.previewString();
    }

    public List<String> currentSequence() {
        return buffer.entries();
    }

    public List<String> repertoireSlots() {
        return repertoire.slots();
    }

    public double progressFraction() {
        if (state != State.RECORDING) {
            return 0D;
        }
        long elapsed = Util.getMillis() - activationTimestampMs;
        return Math.min(1D, Math.max(0D, (double) elapsed / (double) activeWindowMs));
    }

    public double cooldownFraction() {
        if (!isOnCooldown() || lastCooldownDurationMs <= 0L) {
            return 0D;
        }
        long remaining = Math.max(0L, cooldownEndTimestampMs - Util.getMillis());
        double elapsed = lastCooldownDurationMs - remaining;
        return Math.min(1D, Math.max(0D, elapsed / (double) lastCooldownDurationMs));
    }

    public double cooldownRemainingSeconds() {
        if (!isOnCooldown()) {
            return 0D;
        }
        return Math.max(0D, cooldownEndTimestampMs - Util.getMillis()) / 1000.0D;
    }

    public boolean isOnCooldown() {
        return cooldownEndTimestampMs > Util.getMillis();
    }

    public void reset() {
        state = State.IDLE;
        buffer.clear();
        activationTimestampMs = 0L;
        activeWindowMs = baseWindowMs();
        Arrays.fill(slotFlashExpiryMs, 0L);
        lockedHotbarSlot = -1;
    }

    public void applyServerRepertoire(List<String> slots) {
        if (slots.size() != SpellingRepertoire.SLOT_COUNT) {
            return;
        }
        for (int i = 0; i < SpellingRepertoire.SLOT_COUNT; i++) {
            repertoire.assignSlot(i, slots.get(i));
        }
    }

    public void requestRepertoireUpdate(List<String> slots) {
        if (slots.size() != SpellingRepertoire.SLOT_COUNT) {
            return;
        }
        applyServerRepertoire(slots);
        SpellingNetwork.sendRepertoireUpdate(List.copyOf(slots));
    }

    public boolean isSlotHighlighted(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotFlashExpiryMs.length) {
            return false;
        }
        return slotFlashExpiryMs[slotIndex] > Util.getMillis();
    }

    public boolean shouldShowHint() {
        return SpellingClientConfig.showHints && !hintSequence().isEmpty();
    }

    public String hintPreview() {
        List<String> hint = hintSequence();
        return hint.isEmpty() ? "" : String.join(" -> ", hint);
    }

    public List<String> hintSequence() {
        return hintTracker.bestHint()
                .map(SpellHintTracker.HintEntry::sequence)
                .orElse(List.of());
    }

    public void handleServerResult(ServerSpellCastResultPacket packet) {
        Objects.requireNonNull(packet, "packet");
        Minecraft minecraft = Minecraft.getInstance();
        applyCooldown(packet.cooldownMs());

        Component message = packet.message() == null
                ? Component.translatable("overlay.elderlexicon.spelling.result.unknown")
                : packet.message();
        Player localPlayer = minecraft.player;
        if (localPlayer != null) {
            localPlayer.displayClientMessage(Objects.requireNonNull(message), !packet.success());
            for (Component warning : packet.warnings()) {
                if (warning != null) {
                    localPlayer.displayClientMessage(warning, true);
                }
            }
        }

        appendSpellLog(message, packet.success() ? SpellLogKind.SUCCESS : SpellLogKind.FAILURE);
        for (Component warning : packet.warnings()) {
            appendSpellLog(warning, SpellLogKind.WARNING);
        }

        if (packet.success()) {
            recordSuccessfulSequence(packet.runes());
        }
        playResultEffects(packet.success());

        lastResultMessage = message == null ? Component.empty() : message;
        lastResultTimestampMs = Util.getMillis();
        lastResultSuccess = packet.success();
    }

    public boolean hasRecentResultMessage() {
        return Util.getMillis() - lastResultTimestampMs <= RESULT_MESSAGE_DURATION_MS && lastResultMessage != null
                && !lastResultMessage.getString().isBlank();
    }

    public Component lastResultMessage() {
        return lastResultMessage;
    }

    public boolean lastResultWasSuccess() {
        return lastResultSuccess;
    }

    public List<SpellLogEntry> overlayLogs() {
        pruneExpiredLogs();
        return List.copyOf(overlayLogs);
    }

    private void maybeOpenEditor(Minecraft minecraft) {
        if (!spellingKeyHeld || holdOpenedEditor) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        long heldDuration = Util.getMillis() - spellingKeyDownTimestamp;
        long thresholdMs = (long) (SpellingClientConfig.holdDurationSeconds * 1000.0D);
        if (heldDuration >= thresholdMs) {
            minecraft.setScreen(new SpellingRepertoireScreen(this));
            holdOpenedEditor = true;
        }
    }

    private boolean stateEqualsRecordingAndUpdateWindow(Minecraft minecraft) {
        if (state != State.RECORDING) {
            return false;
        }
        long desired = currentWindowMs(minecraft);
        if (desired != activeWindowMs) {
            activeWindowMs = desired;
        }
        return true;
    }

    private long baseWindowMs() {
        return Math.round(SpellingClientConfig.inputWindowSeconds * 1000.0D);
    }

    private long currentWindowMs(Minecraft minecraft) {
        long base = baseWindowMs();
        if (!SpellingClientConfig.enableSlowMode) {
            return base;
        }
        Player player = minecraft.player;
        if (player != null && player.isShiftKeyDown()) {
            long slow = Math.round(SpellingClientConfig.slowModeSeconds * 1000.0D);
            return Math.max(base, slow);
        }
        return base;
    }

    public void notifyEditorClosed() {
        spellingKeyHeld = false;
        holdOpenedEditor = false;
    }

    public void handleClientConfigReload() {
        hintTracker.setCapacity(SpellingClientConfig.hintHistorySize);
        if (!SpellingClientConfig.showHints) {
            hintTracker.clear();
        }
    }

    private void applyCooldown(long durationMs) {
        long clamped = Math.max(0L, durationMs);
        lastCooldownDurationMs = clamped;
        if (clamped <= 0L) {
            cooldownEndTimestampMs = 0L;
            return;
        }
        cooldownEndTimestampMs = Util.getMillis() + clamped;
    }

    private void notifyCooldown(Minecraft minecraft) {
        Player localPlayer = minecraft.player;
        if (localPlayer == null) {
            return;
        }
        double seconds = cooldownRemainingSeconds();
        String formatted = String.format(Locale.ROOT, "%.1f", seconds);
        Component cooldownMessage = Component.translatable("overlay.elderlexicon.spelling.cooldown_toast", formatted);
        localPlayer.displayClientMessage(Objects.requireNonNull(cooldownMessage), true);
    }

    private void flashSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotFlashExpiryMs.length) {
            return;
        }
        slotFlashExpiryMs[slotIndex] = Util.getMillis() + SLOT_FLASH_DURATION_MS;
    }

    private void playTapSound() {
        if (!SpellingClientConfig.enableSoundCues) {
            return;
        }
        playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.5F, 1.2F);
    }

    private void appendSpellLog(Component text, SpellLogKind kind) {
        if (text == null) {
            return;
        }
        String raw = text.getString();
        if (raw == null || raw.isBlank()) {
            return;
        }
        pruneExpiredLogs();
        overlayLogs.addFirst(new SpellLogEntry(text, kind, Util.getMillis()));
        while (overlayLogs.size() > SPELL_LOG_LIMIT) {
            overlayLogs.removeLast();
        }
    }

    private void pruneExpiredLogs() {
        long now = Util.getMillis();
        overlayLogs.removeIf(entry -> now - entry.timestampMs > SPELL_LOG_DURATION_MS);
    }

    public enum SpellLogKind {
        SUCCESS,
        FAILURE,
        WARNING
    }

    public record SpellLogEntry(Component message, SpellLogKind kind, long timestampMs) {
    }

    private void playDenySound() {
        if (!SpellingClientConfig.enableSoundCues) {
            return;
        }
        playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.4F, 0.7F);
    }

    private void playResultEffects(boolean success) {
        playResultSound(success);
        spawnResultParticles(success);
    }

    private void playResultSound(boolean success) {
        if (!SpellingClientConfig.enableSoundCues) {
            return;
        }
        SoundEvent event = success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO;
        float pitch = success ? 1.3F : 0.8F;
        float volume = success ? 0.8F : 0.7F;
        playSound(event, volume, pitch);
    }

    private void spawnResultParticles(boolean success) {
        if (!SpellingClientConfig.enableParticleCues) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        var particle = success ? ParticleTypes.END_ROD : ParticleTypes.SMOKE;
        for (int i = 0; i < 6; i++) {
            double dx = (level.random.nextDouble() - 0.5D) * 0.6D;
            double dz = (level.random.nextDouble() - 0.5D) * 0.6D;
            double dy = level.random.nextDouble() * 0.4D;
            level.addParticle(Objects.requireNonNull(particle),
                    player.getX() + dx,
                    player.getEyeY() - 0.2D + dy,
                    player.getZ() + dz,
                    0.0D,
                    0.01D,
                    0.0D);
        }
    }

    private void playSound(SoundEvent event, float volume, float pitch) {
        if (event == null) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.playSound(event, volume, pitch);
        }
    }

    private void recordSuccessfulSequence(List<String> runes) {
        if (runes == null || runes.isEmpty()) {
            return;
        }
        if (SpellingClientConfig.showHints) {
            hintTracker.record(runes);
        }
    }
}
