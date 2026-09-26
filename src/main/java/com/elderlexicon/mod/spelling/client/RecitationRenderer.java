package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws a reciting mage's words above its head, as runes in the old script: the spell is said aloud, so whoever is
 * near sees it being spoken. It glows (full bright) and, once the trance ends, fades out.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class RecitationRenderer {

    /** Height above the head; a little more when a name tag is drawn there too. */
    private static final double ABOVE_HEAD = 0.45D;
    private static final double ABOVE_NAME_TAG = 0.3D;
    private static final float TEXT_SCALE = 0.035F;
    private static final int RECITING_COLOR = 0xD8B8FF;
    private static final int SPOKEN_COLOR = 0xF0D080;

    private RecitationRenderer() {
    }

    @SubscribeEvent
    public static void renderAboveHead(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && player.isInvisibleTo(minecraft.player)) {
            return;
        }
        Recitations.of(player.getId()).ifPresent(recitation -> {
            float opacity = recitation.opacity(Util.getMillis());
            if (opacity <= 0.05F) {
                return;
            }
            Component words = recitation.runes().isEmpty()
                    ? Component.literal("· · ·")
                    : RuneSgaMapper.sequenceComponent(recitation.runes());
            int color = recitation.ended() ? SPOKEN_COLOR : RECITING_COLOR;
            draw(event.getPoseStack(), event, player, words, color, opacity, player != minecraft.player);
        });
    }

    private static void draw(PoseStack poseStack, RenderLivingEvent.Post<?, ?> event, Player player, Component words,
                             int color, float opacity, boolean hasNameTag) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(0.0D, player.getBbHeight() + ABOVE_HEAD + (hasNameTag ? ABOVE_NAME_TAG : 0.0D), 0.0D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        int alpha = Math.max(0x10, Math.round(opacity * 255.0F));
        int background = Math.round(minecraft.options.getBackgroundOpacity(0.25F) * opacity * 255.0F) << 24;
        float x = -font.width(words) / 2.0F;
        font.drawInBatch(words, x, 0.0F, (alpha << 24) | color, false, poseStack.last().pose(),
                event.getMultiBufferSource(), Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
