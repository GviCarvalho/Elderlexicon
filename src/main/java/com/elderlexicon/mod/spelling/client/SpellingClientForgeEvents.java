package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderItemInFrameEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Listens to Forge client events so the controller can update every frame.
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
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
        if (minecraft.player == null) {
            ClientFlow.set(false);
        }
    }

    @SubscribeEvent
    public static void handleKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());
        ClientSpellingController controller = ClientSpellingController.getInstance();

        // Before the trance is woken, this is only Minecraft: the keys of magic do nothing (docs/habilidades-design.md).
        if (SpellingKeyMappings.SPELLING_KEY.isActiveAndMatches(key) && controller.hasTrance()) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                controller.handleSpellingKeyPressed(minecraft);
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                controller.handleSpellingKeyReleased(minecraft);
            }
            cancel(event);
            return;
        }

        if (SpellingKeyMappings.FLOW_KEY.isActiveAndMatches(key) && event.getAction() == GLFW.GLFW_PRESS
                && minecraft.screen == null && controller.hasFlow()) {
            SpellingNetwork.sendFlowToggle();
            cancel(event);
            return;
        }

        if (SpellingKeyMappings.REPERTOIRE_KEY.isActiveAndMatches(key) && event.getAction() == GLFW.GLFW_PRESS
                && controller.hasTrance()) {
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

    /**
     * Hides the vanilla hotbar while in trance: item icons are drawn in front of anything painted later, so the rune
     * bar drawn over it was only readable with an empty hotbar. {@link SpellingOverlay} draws its own bar instead.
     */
    @SubscribeEvent
    public static void hideHotbarInTrance(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())
                && ClientSpellingController.getInstance().isRecording()
                && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
        }
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
        // In a frame, the page itself fills it, as a map does.
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        com.elderlexicon.mod.spell.block.WrittenTexts.upgrade(tag);
        String text = tag == null ? "" : tag.getString(SpellScrollItem.TEXT_TAG);
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        float size = 0.95F / ScrollPage.H;
        poseStack.scale(size, size, size);
        poseStack.translate(-ScrollPage.W / 2.0F, -ScrollPage.H / 2.0F, -1.0F);
        ScrollPage.draw(poseStack, event.getMultiBufferSource(), event.getPackedLight(), text, false);
        poseStack.popPose();
        event.setCanceled(true);
    }

    private static void cancel(InputEvent event) {
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
    }
}
