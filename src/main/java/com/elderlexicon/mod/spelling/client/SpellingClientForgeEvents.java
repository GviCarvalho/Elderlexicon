package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import com.elderlexicon.mod.spelling.render.SpellMapHelper;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderItemInFrameEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Listens to Forge client events so the controller can update every frame.
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SpellingClientForgeEvents {

    private SpellingClientForgeEvents() {
    }

    @SubscribeEvent
    public static void handleClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientSpellingController.getInstance().tick(minecraft);
    }

    @SubscribeEvent
    public static void handleKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());
        ClientSpellingController controller = ClientSpellingController.getInstance();

        if (SpellingKeyMappings.SPELLING_KEY.isActiveAndMatches(key)) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                controller.handleSpellingKeyPressed(minecraft);
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                controller.handleSpellingKeyReleased(minecraft);
            }
            cancel(event);
            return;
        }

        if (SpellingKeyMappings.REPERTOIRE_KEY.isActiveAndMatches(key) && event.getAction() == GLFW.GLFW_PRESS) {
            controller.openRepertoireEditor(minecraft);
            cancel(event);
            return;
        }

        if (minecraft.screen != null) {
            return;
        }

        if (event.getAction() != GLFW.GLFW_PRESS || !controller.isRecording()) {
            return;
        }

        KeyMapping[] hotbar = minecraft.options.keyHotbarSlots;
        for (int slot = 0; slot < hotbar.length; slot++) {
            KeyMapping hotbarMapping = hotbar[slot];
            if (hotbarMapping != null && hotbarMapping.isActiveAndMatches(key)) {
                boolean consumed = controller.handleHotbarInput(slot);
                if (consumed) {
                    cancel(event);
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public static void handleRenderGui(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientSpellingController controller = ClientSpellingController.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (!controller.isRecording()) return;
        // Removido: lógica antiga de stick renomeado/improvised wand
    }

    @SubscribeEvent
    public static void handleMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientSpellingController.getInstance().isRecording()) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        cancel(event);
    }

    @SubscribeEvent
    public static void renderOverlay(RenderGuiEvent.Post event) {
        SpellingOverlay.getInstance().render(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void renderScrollInFrame(RenderItemInFrameEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SpellScrollItem)) {
            return;
        }
        MapItemSavedData data = SpellMapHelper.getSavedData(stack, event.getItemFrameEntity().level());
        Integer mapId = SpellMapHelper.getMapId(stack);
        if (data == null || mapId == null) {
            return;
        }

        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(0.0078125F, 0.0078125F, 0.0078125F);
        poseStack.translate(-64.0F, -64.0F, -1.0F);
        Minecraft.getInstance().gameRenderer.getMapRenderer()
                .render(poseStack, event.getMultiBufferSource(), mapId, data, true, event.getPackedLight());
        poseStack.popPose();
        event.setCanceled(true);
    }

    private static void cancel(InputEvent event) {
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
    }
}
