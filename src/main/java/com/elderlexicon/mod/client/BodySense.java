package com.elderlexicon.mod.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.vita.Symptoms;
import com.elderlexicon.mod.vita.network.BodyPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How the body feels from the inside (docs/vita-sintomas-design.md): a shiver in the sight, the head swaying with fever,
 * the sight closing for want of breath or going dark when running on thin blood, the heart heard beating, a run that
 * gives out. No icon and no word: the player feels strange and does not know why. The Life Compass tells why.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class BodySense {

    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");

    private static BodyPacket seen = BodyPacket.WHOLE;
    private static Symptoms.Episode episode = Symptoms.Episode.NONE;
    private static int episodeLeft;
    private static int episodeLength = 1;
    private static int ticks;
    /** The sight darkening on effort with thin blood, eased in and out. */
    private static float dim;
    private static float dimBefore;
    private static int heartIn;
    private static int running;
    private static int winded;

    private BodySense() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            seen = BodyPacket.WHOLE;
            episode = Symptoms.Episode.NONE;
            episodeLeft = 0;
            dim = 0.0F;
            dimBefore = 0.0F;
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        ticks++;
        BodyPacket felt = BodyPacket.felt();
        if (felt != seen) {
            if (felt.episode() != Symptoms.Episode.NONE && (felt.episode() != episode || episodeLeft == 0)) {
                episode = felt.episode();
                episodeLength = Math.max(1, felt.episode().ticks());
                episodeLeft = Math.max(1, felt.episodeLeft());
            }
            seen = felt;
        }
        if (episodeLeft > 0 && --episodeLeft == 0) {
            episode = Symptoms.Episode.NONE;
        }

        float aquaLow = Math.max(0.0F, -felt.aqua());
        float auraLow = Math.max(0.0F, -felt.aura());

        // Thin blood: running or jumping, the pressure drops and the sight goes dark at the edges.
        boolean effort = player.isSprinting() || (!player.onGround() && player.getDeltaMovement().y > 0.0D);
        float target = aquaLow >= 0.3F && effort ? (aquaLow - 0.3F) / 0.7F * 0.9F : 0.0F;
        dimBefore = dim;
        dim += (target - dim) * (target > dim ? 0.08F : 0.04F);

        // A run that gives out: a broken leg cannot run at all, and without breath the run lasts less the less there is.
        if (player.isSprinting()) {
            if (felt.leg()) {
                stopRunning(minecraft, player, 40);
            } else if (auraLow >= 0.5F && ++running > (int) Mth.lerp((auraLow - 0.5F) / 0.5F, 200.0F, 40.0F)) {
                stopRunning(minecraft, player, 60);
            }
        } else {
            running = Math.max(0, running - 2);
        }
        if (winded > 0) {
            winded--;
            if (player.isSprinting()) {
                stopRunning(minecraft, player, winded);
            }
        }

        // The heart, heard from inside when there is too little blood or breath, faster the less there is.
        float heart = Math.max(aquaLow, auraLow);
        if (episode == Symptoms.Episode.FAINT) {
            heart = 1.0F;
        }
        if (heart >= 0.5F) {
            if (--heartIn <= 0) {
                float strength = (heart - 0.5F) / 0.5F;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT,
                        1.0F + 0.3F * strength, 0.25F + 0.35F * strength));
                heartIn = (int) Mth.lerp(strength, 26.0F, 12.0F);
            }
        } else {
            heartIn = 0;
        }
    }

    private static void stopRunning(Minecraft minecraft, LocalPlayer player, int rest) {
        player.setSprinting(false);
        KeyMapping sprint = minecraft.options.keySprint;
        if (sprint instanceof ToggleKeyMapping toggle && minecraft.options.toggleSprint().get()) {
            // A toggled key flips on each press: pressing it once more lets it go.
            if (toggle.isDown()) {
                toggle.setDown(true);
            }
        } else {
            sprint.setDown(false);
        }
        running = 0;
        winded = Math.max(winded, rest);
    }

    /** How far into the episode, 0 to 1 and back: it comes, holds, and goes. */
    private static float episodeWave(float partialTick) {
        if (episode == Symptoms.Episode.NONE || episodeLeft <= 0) {
            return 0.0F;
        }
        float done = 1.0F - (episodeLeft - partialTick) / episodeLength;
        return Mth.sin(Mth.clamp(done, 0.0F, 1.0F) * Mth.PI);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        BodyPacket felt = BodyPacket.felt();
        float time = ticks + (float) event.getPartialTick();
        float roll = 0.0F;
        float pitch = 0.0F;
        float yaw = 0.0F;

        // The cold shivers.
        float cold = Math.max(0.0F, -felt.igni());
        if (cold > 0.0F) {
            roll += cold * 0.8F * (Mth.sin(time * 2.9F) + 0.6F * Mth.sin(time * 4.7F));
            yaw += cold * 0.3F * Mth.sin(time * 3.7F);
        }

        // Fever, a swollen head, a light head: the world sways slowly.
        float sway = Math.max(above(felt.igni(), 0.4F), Math.max(above(felt.aqua(), 0.6F), above(felt.aura(), 0.5F)));
        if (sway > 0.0F) {
            roll += 2.5F * sway * Mth.sin(time * 0.06F);
            pitch += 1.2F * sway * Mth.sin(time * 0.045F);
        }

        float wave = episodeWave((float) event.getPartialTick());
        switch (episode) {
            case PAIN, COLIC -> {
                pitch += 4.0F * wave * Mth.sin(time * 1.7F);
                roll += 2.0F * wave * Mth.sin(time * 2.3F);
            }
            case FAINT -> {
                pitch += 12.0F * wave;
                roll += 6.0F * wave;
            }
            case HEAT -> roll += 3.0F * wave * Mth.sin(time * 0.2F);
            default -> {
            }
        }
        if (roll != 0.0F || pitch != 0.0F || yaw != 0.0F) {
            event.setRoll(event.getRoll() + roll);
            event.setPitch(event.getPitch() + pitch);
            event.setYaw(event.getYaw() + yaw);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            return;
        }
        BodyPacket felt = BodyPacket.felt();
        // Hyperventilating, or a head full of water: the sight breathes.
        float breath = Math.max(above(felt.aura(), 0.2F), above(felt.aqua(), 0.5F));
        if (breath > 0.0F) {
            float time = ticks + (float) event.getPartialTick();
            event.setFOV(event.getFOV() * (1.0D + 0.025D * breath * Mth.sin(time * 0.12F)));
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.VIGNETTE.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        BodyPacket felt = BodyPacket.felt();
        float partial = event.getPartialTick();
        float wave = episodeWave(partial);

        // The sight closes: a tunnel for want of breath, the dark of thin blood on effort, a faint, a wave of heat.
        float auraLow = Math.max(0.0F, -felt.aura());
        float dark = auraLow >= 0.3F ? (auraLow - 0.3F) / 0.7F * 0.85F : 0.0F;
        dark = Math.max(dark, Mth.lerp(partial, dimBefore, dim));
        if (episode == Symptoms.Episode.FAINT) {
            dark = Math.max(dark, wave);
        } else if (episode == Symptoms.Episode.HEAT) {
            dark = Math.max(dark, 0.6F * wave);
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        // The fever: the sight warms.
        float fever = Math.max(0.0F, felt.igni());
        if (episode == Symptoms.Episode.HEAT) {
            fever = Math.max(fever, wave);
        }
        if (fever > 0.15F) {
            int alpha = (int) (Mth.clamp((fever - 0.15F) / 0.85F, 0.0F, 1.0F) * 0.18F * 255.0F);
            graphics.fill(0, 0, width, height, (alpha << 24) | 0xFF7A30);
        }
        if (dark > 0.01F) {
            vignette(graphics, width, height, Math.min(1.0F, dark));
            if (dark > 0.5F) {
                vignette(graphics, width, height, (dark - 0.5F) * 2.0F);
            }
            if (dark > 0.7F) {
                int alpha = (int) ((dark - 0.7F) / 0.3F * 0.95F * 255.0F);
                graphics.fill(0, 0, width, height, alpha << 24);
            }
        }
    }

    /** Darkens the edges of the sight by {@code strength}, as the game's own vignette does. */
    private static void vignette(GuiGraphics graphics, int width, int height, float strength) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        graphics.setColor(strength, strength, strength, 1.0F);
        graphics.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** How far {@code value} is past {@code from}, toward 1, from 0 to 1. */
    private static float above(float value, float from) {
        return value <= from ? 0.0F : Math.min(1.0F, (value - from) / (1.0F - from));
    }
}
