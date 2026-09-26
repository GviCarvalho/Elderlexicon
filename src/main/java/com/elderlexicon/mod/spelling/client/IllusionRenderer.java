package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Draws the illusions standing around (docs/surgit-visao-design.md, section 4): images of blocks as they would look,
 * lit by the world, and images of marked things standing where they were called, moving as the things themselves move.
 * They are only drawn: nothing of them can be touched. The spirit's own sight (a revelation) sees through them.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class IllusionRenderer {

    private IllusionRenderer() {
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || ClientRevelation.active()
                || ClientIllusions.blocks().isEmpty() && ClientIllusions.things().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 eye = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // As in the revelation: the camera's turn is already in the global model-view at this stage.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        Lighting.setupLevel(poseStack.last().pose());

        for (Map.Entry<BlockPos, BlockState> image : ClientIllusions.blocks().entrySet()) {
            BlockPos pos = image.getKey();
            BlockState state = image.getValue();
            int light = LevelRenderer.getLightColor(level, pos);
            poseStack.pushPose();
            poseStack.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
            if (state.getRenderShape() == RenderShape.MODEL) {
                minecraft.getBlockRenderer().renderSingleBlock(state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY, null);
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                FluidDrawing.draw(level, pos, fluid, poseStack, buffers, light, IllusionRenderer::fluidSeenAt);
            }
            poseStack.popPose();
        }

        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        boolean shadows = minecraft.options.entityShadows().get();
        dispatcher.setRenderShadow(false);
        float partialTick = event.getPartialTick();
        double now = level.getGameTime() + partialTick;
        for (ClientIllusions.ThingImage image : ClientIllusions.things().values()) {
            Entity source = level.getEntity(image.sourceId());
            if (source == null) {
                continue;
            }
            Vec3 at = image.at(now);
            Vec3 heading = image.to().subtract(image.from());
            boolean travelling = heading.lengthSqr() > 1.0E-6D && at.distanceToSqr(image.to()) > 1.0E-4D;
            poseStack.pushPose();
            poseStack.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
            if (travelling) {
                // A thrown image flies as a thrown body does: laid along its course, head first.
                FlightPose.apply(poseStack, heading, source.getBbHeight());
            }
            dispatcher.render(source, 0.0D, 0.0D, 0.0D, source.getViewYRot(partialTick), partialTick, poseStack, buffers,
                    LevelRenderer.getLightColor(level, BlockPos.containing(at)));
            poseStack.popPose();
        }
        dispatcher.setRenderShadow(shadows);

        buffers.endBatch();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** The fluid seen at a place: an image of one, or the real one. */
    private static FluidState fluidSeenAt(BlockPos pos) {
        BlockState image = ClientIllusions.blocks().get(pos);
        if (image != null) {
            return image.getFluidState();
        }
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState() : level.getFluidState(pos);
    }

    @SubscribeEvent
    public static void forgetOnLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientIllusions.clear();
    }

    @SubscribeEvent
    public static void forgetOnRespawn(ClientPlayerNetworkEvent.Clone event) {
        ClientIllusions.clear();
    }
}
