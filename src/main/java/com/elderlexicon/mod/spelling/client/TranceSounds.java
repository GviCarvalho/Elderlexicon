package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.config.SpellingClientConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the mage feels in trance, as the book tells it (chapter VI, "Do Puxão da Âncora"):
 * <ul>
 *   <li>the weight falling backwards, "como se você estivesse caindo de um penhasco de costas": a tone sliding down;</li>
 *   <li>"um estalo silencioso na base do crânio": a dry click;</li>
 *   <li>"um leve formigamento que sobe pela nuca": a faint chime, rising in three notes;</li>
 *   <li>"uma queda abrupta e momentânea na sua frequência cardíaca": the heart, heard, beating slower and slower while
 *       the trance lasts;</li>
 *   <li>"sua audição física ficará abafada, como se você estivesse embaixo d'água": the world muffled, more as the
 *       trance deepens ({@link Muffle});</li>
 *   <li>"a sua visão periférica ficará completamente borrada": the edges of sight darkening, throbbing with the heart;</li>
 *   <li>and coming out, "o coração acelerará novamente e a fresta se fechará": two quick beats, a tone rising, the world
 *       clear again at once.</li>
 * </ul>
 * The sight going elsewhere (a revelation, a bond of sight, a projection) keeps the trance going.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class TranceSounds {

    private static final ResourceLocation VIGNETTE = new ResourceLocation("minecraft", "textures/misc/vignette.png");
    /** The heart at the start (about 85 beats a minute) and at its slowest (about 40), in ticks between beats. */
    private static final int FIRST_BEAT_GAP = 14;
    private static final int SLOWEST_BEAT_GAP = 30;
    private static final float BEAT_SLOWING = 2.5F;
    /** How long the trance takes to reach its deepest, in ticks: the two seconds of the window. */
    private static final float DEEPENING_TICKS = 40.0F;
    private static final float DARKEST_EDGES = 0.75F;

    private static boolean inTrance;
    private static int ticksIn;
    private static float nextBeatGap;
    private static int ticksToBeat;
    /** Game time of the last heartbeat, for the edges throbbing with it; and how dark the edges are now. */
    private static long lastBeat;
    private static float edges;
    /** The rising exit, a few ticks long: the heart's two quick beats. */
    private static int exitTicks = -1;

    private TranceSounds() {
    }

    @SubscribeEvent
    public static void listen(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            Muffle.setDepth(0.0F);
            inTrance = false;
            edges = 0.0F;
            return;
        }
        boolean cues = SpellingClientConfig.enableSoundCues;
        boolean now = ClientSpellingController.getInstance().isRecording() || ClientSightBond.active()
                || ClientRevelation.active();
        if (now && !inTrance) {
            ticksIn = 0;
            nextBeatGap = FIRST_BEAT_GAP;
            ticksToBeat = 6; // the first beat comes after the fall and the click
            exitTicks = -1;
            if (cues) {
                play(SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.35F); // falling backwards
            }
        } else if (!now && inTrance) {
            Muffle.setDepth(0.0F); // the crack closes: the world is heard again at once
            exitTicks = 0;
            if (cues) {
                play(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.25F); // back up
            }
        }
        inTrance = now;

        if (inTrance) {
            ticksIn++;
            if (cues) {
                sensations(minecraft);
            }
            float depth = Math.min(1.0F, ticksIn / DEEPENING_TICKS);
            Muffle.setDepth(cues ? depth : 0.0F);
            edges = DARKEST_EDGES * depth;
        } else {
            edges = Math.max(0.0F, edges - 0.08F);
            if (exitTicks >= 0 && cues) {
                // The heart speeds up again: two quick beats.
                if (exitTicks == 2 || exitTicks == 7) {
                    beat(minecraft, 1.15F);
                }
                exitTicks = exitTicks >= 8 ? -1 : exitTicks + 1;
            }
        }
    }

    /** The click at the base of the skull, the tingle climbing the neck, and the heart slowing down. */
    private static void sensations(Minecraft minecraft) {
        switch (ticksIn) {
            case 3 -> play(SoundEvents.SCULK_CLICKING, 1.3F, 0.5F);
            case 6 -> play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.18F);
            case 9 -> play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.15F);
            case 12 -> play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8F, 0.12F);
            default -> {
            }
        }
        if (--ticksToBeat <= 0) {
            beat(minecraft, 0.8F);
            ticksToBeat = Math.round(nextBeatGap);
            nextBeatGap = Math.min(SLOWEST_BEAT_GAP, nextBeatGap + BEAT_SLOWING);
        }
    }

    private static void beat(Minecraft minecraft, float pitch) {
        play(SoundEvents.WARDEN_HEARTBEAT, pitch, 0.7F);
        lastBeat = minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    /** A sound of the trance itself: heard clear, never muffled. */
    private static void play(SoundEvent sound, float pitch, float volume) {
        SoundInstance instance = SimpleSoundInstance.forLocalAmbience(sound, pitch, volume);
        Muffle.spare(instance);
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    /** The edges of sight going dark as the trance deepens, throbbing with each heartbeat. */
    @SubscribeEvent
    public static void blurEdges(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (edges <= 0.001F || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        float sinceBeat = minecraft.level.getGameTime() - lastBeat + event.getPartialTick();
        float throb = inTrance ? 0.25F * (float) Math.exp(-sinceBeat / 4.0F) : 0.0F;
        float darkness = Math.min(1.0F, edges + throb);
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // As the game darkens the edges of the screen: what is drawn takes light away where the vignette is dark.
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        graphics.setColor(darkness, darkness, darkness, 1.0F);
        graphics.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
