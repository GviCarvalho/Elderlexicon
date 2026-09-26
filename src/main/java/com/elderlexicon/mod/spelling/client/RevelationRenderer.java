package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.network.RevelationPacket;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a revelation as a void: once the world is drawn, the whole picture (sky, sun, moon, land) is wiped to black,
 * and only what the spirit found is drawn again, as it truly looks and lit as if by its own light: blocks, water and
 * lava, creatures, and the mage too when seen from outside. The mark each one bears is written above it.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class RevelationRenderer {

    private static final float LABEL_SCALE = 0.025F;
    /**
     * The faintest a revealed block is shown: block light 9 is about a quarter as bright as full, so even loose soil
     * can be made out (the light curve is steep, and much lower reads as black).
     */
    private static final int MIN_LIGHT = 9;

    private RevelationRenderer() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientRevelation.tick();
        }
    }

    @SubscribeEvent
    public static void drawVoid(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !ClientRevelation.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        // Weather is drawn just before this with depth writes off, and with them off the depth clear does nothing: the
        // unseen world would still hide what is revealed, painting it black.
        RenderSystem.depthMask(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT | GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        // What is shown is seen by the spirit, not by light: no fog on it.
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // At this stage the camera's turn is already in the global model-view (weather is drawn with it); the event's
        // pose carries it as well, so the global one is cleared while drawing, or the turn would count twice.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupLevel(poseStack.last().pose());

        drawBlocks(level, poseStack, buffers, eye);
        drawEntities(minecraft, level, poseStack, buffers, eye, event.getPartialTick());
        buffers.endBatch();
        drawLabels(minecraft, level, poseStack, buffers, camera, eye, event.getPartialTick());
        buffers.endBatch();

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderFogStart(fogStart);
    }

    // ------------------------------------------------------------------ blocks

    private static void drawBlocks(ClientLevel level, PoseStack poseStack, MultiBufferSource buffers, Vec3 eye) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        for (RevelationPacket.SeenBlock seen : ClientRevelation.blocks()) {
            BlockPos pos = seen.pos();
            BlockState state = level.getBlockState(pos);
            poseStack.pushPose();
            poseStack.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
            if (state.getRenderShape() != RenderShape.INVISIBLE) {
                blocks.renderSingleBlock(state, poseStack, buffers, lightFor(seen.strength()), OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY, null);
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                FluidDrawing.draw(level, pos, fluid, poseStack, buffers, LightTexture.FULL_BRIGHT,
                        next -> level.getFluidState(next));
            }
            poseStack.popPose();
        }
    }

    /**
     * How strongly a block is seen becomes how much light it shows: dim for loose soil, full for dense rock. Only block
     * light is used; sky light follows the time of day, and at night it made everything but the densest rock black.
     */
    private static int lightFor(int strength) {
        if (strength >= 255) {
            return LightTexture.FULL_BRIGHT;
        }
        int level = MIN_LIGHT + Math.round((15 - MIN_LIGHT) * strength / 255.0F);
        return LightTexture.pack(level, 0);
    }

    // ------------------------------------------------------------------ creatures

    private static void drawEntities(Minecraft minecraft, ClientLevel level, PoseStack poseStack,
                                     MultiBufferSource buffers, Vec3 eye, float partialTick) {
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        // Shadows are cast on the ground, and here there is none.
        boolean shadows = minecraft.options.entityShadows().get();
        dispatcher.setRenderShadow(false);
        for (RevelationPacket.SeenEntity seen : ClientRevelation.entities()) {
            Entity entity = level.getEntity(seen.entityId());
            if (entity != null) {
                drawEntity(dispatcher, entity, poseStack, buffers, eye, partialTick);
            }
        }
        // Spirits out of their bodies float where they drifted, drawn as ghosts of those they left.
        for (RevelationPacket.SeenSpirit spirit : ClientRevelation.spirits()) {
            Entity owner = level.getEntity(spirit.playerId());
            if (owner != null) {
                dispatcher.render(owner, spirit.x() - eye.x, spirit.y() - eye.y, spirit.z() - eye.z,
                        owner.getViewYRot(partialTick), partialTick, poseStack, new GhostBuffers(buffers, 0.5F),
                        LightTexture.FULL_BRIGHT);
            }
        }
        // The mage stands in the void too, when seen from outside (Eleven in her dark room).
        Entity self = minecraft.getCameraEntity();
        if (self != null && !minecraft.options.getCameraType().isFirstPerson()) {
            drawEntity(dispatcher, self, poseStack, buffers, eye, partialTick);
        }
        dispatcher.setRenderShadow(shadows);
    }

    private static void drawEntity(EntityRenderDispatcher dispatcher, Entity entity, PoseStack poseStack,
                                   MultiBufferSource buffers, Vec3 eye, float partialTick) {
        Vec3 at = entity.getPosition(partialTick);
        float yaw = entity.getViewYRot(partialTick);
        dispatcher.render(entity, at.x - eye.x, at.y - eye.y, at.z - eye.z, yaw, partialTick, poseStack, buffers,
                LightTexture.FULL_BRIGHT);
    }

    // ------------------------------------------------------------------ marks

    private static void drawLabels(Minecraft minecraft, ClientLevel level, PoseStack poseStack,
                                   MultiBufferSource buffers, Camera camera, Vec3 eye, float partialTick) {
        Font font = minecraft.font;
        int color = ClientRevelation.color();
        for (RevelationPacket.SeenBlock block : ClientRevelation.blocks()) {
            if (!block.label().isEmpty()) {
                label(poseStack, buffers, font, camera, Vec3.atCenterOf(block.pos()).add(0.0D, 0.8D, 0.0D).subtract(eye),
                        block.label(), color);
            }
        }
        for (RevelationPacket.SeenEntity seen : ClientRevelation.entities()) {
            Entity entity = level.getEntity(seen.entityId());
            if (entity != null && !seen.label().isEmpty()) {
                Vec3 at = entity.getPosition(partialTick).add(0.0D, entity.getBbHeight() + 0.5D, 0.0D).subtract(eye);
                label(poseStack, buffers, font, camera, at, seen.label(), color);
            }
        }
    }

    private static void label(PoseStack poseStack, MultiBufferSource buffers, Font font, Camera camera, Vec3 at,
                              String text, int color) {
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, 0xFF000000 | color, false, poseStack.last().pose(),
                buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
