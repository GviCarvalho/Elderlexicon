package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.sight.Visibility;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hidden blocks on this client. The block stays what it is (it breaks, sounds, stops a body and gives light as
 * always); only its drawing goes. Every block model is wrapped so that, when the chunk builder asks it about a position,
 * it can answer that it has nothing to draw there. Between 1 and 9 the block is drawn again see-through, on its own.
 * Blocks with a renderer of their own (chests, signs) are handled by {@link HiddenBlockEntities}. Water and lava are not
 * drawn by models and still show.
 */
public final class HiddenBlocks {

    /** Marks a position the model must leave empty. */
    private static final ModelProperty<Boolean> HIDDEN = new ModelProperty<>();
    /**
     * The sides of a block that touch a hidden one. The game leaves out a face pressed against a solid block, and a
     * hidden block is still solid; those faces are drawn anyway, or the hidden block would be a hole into the sky.
     */
    private static final ModelProperty<Set<Direction>> OPENED = new ModelProperty<>();
    /**
     * The light an opened face gets: what the hidden block's place would have if it were empty. The place is still
     * solid, so the game gives it no light, and the faces around it came out dark, giving the hidden block away.
     */
    private static final ModelProperty<Integer> OPENED_LIGHT = new ModelProperty<>();

    /** The light a hidden block's place would have if it were empty: the brightest light around it. */
    static int openLight(BlockAndTintGetter level, BlockPos hidden) {
        int sky = 0;
        int block = 0;
        for (Direction side : Direction.values()) {
            BlockPos next = hidden.relative(side);
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, next));
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, next));
        }
        return LightTexture.pack(block, sky);
    }

    private HiddenBlocks() {
    }

    /** Wraps every block model (not the ones items are drawn with) once the models are baked. */
    @Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Models {

        private Models() {
        }

        @SubscribeEvent
        public static void wrap(ModelEvent.ModifyBakingResult event) {
            Map<net.minecraft.resources.ResourceLocation, BakedModel> models = event.getModels();
            models.replaceAll((location, model) -> location instanceof ModelResourceLocation variant
                    && !"inventory".equals(variant.getVariant()) ? new Hideable(model) : model);
        }
    }

    /** A block model that draws nothing at a hidden position. */
    private static final class Hideable extends BakedModelWrapper<BakedModel> {

        Hideable(BakedModel model) {
            super(model);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
            ModelData own = originalModel.getModelData(level, pos, state, modelData);
            if (ClientVisibility.blocks().isEmpty()) {
                return own;
            }
            if (ClientVisibility.ofBlock(pos).isPresent()) {
                return own.derive().with(HIDDEN, Boolean.TRUE).build();
            }
            Set<Direction> opened = EnumSet.noneOf(Direction.class);
            int light = 0;
            for (Direction side : Direction.values()) {
                if (ClientVisibility.ofBlock(pos.relative(side)).isPresent()) {
                    opened.add(side);
                    light = Math.max(light, openLight(level, pos.relative(side)));
                }
            }
            return opened.isEmpty() ? own : own.derive().with(OPENED, opened).with(OPENED_LIGHT, light).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                        ModelData data, @Nullable RenderType renderType) {
            if (Boolean.TRUE.equals(data.get(HIDDEN))) {
                return List.of();
            }
            Set<Direction> opened = data.get(OPENED);
            if (opened == null) {
                return originalModel.getQuads(state, side, rand, data, renderType);
            }
            if (side != null) {
                // An opened side is given with the unculled faces below; asked here too, it would be drawn twice.
                return opened.contains(side) ? List.of() : originalModel.getQuads(state, side, rand, data, renderType);
            }
            List<BakedQuad> quads = new ArrayList<>(originalModel.getQuads(state, null, rand, data, renderType));
            Integer light = data.get(OPENED_LIGHT);
            // Forge draws a face with the brighter of the light baked in it and the world's.
            IQuadTransformer lit = QuadTransformers.applyingLightmap(light == null ? 0 : light);
            for (Direction open : opened) {
                quads.addAll(lit.process(originalModel.getQuads(state, open, rand, data, renderType)));
            }
            return quads;
        }
    }

    @Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
    public static final class Events {

        private Events() {
        }

        /** Draws hidden blocks between 1 and 9 see-through, lit as the world around them. */
        @SubscribeEvent
        public static void drawGhosts(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || ClientRevelation.active()
                    || ClientVisibility.blocks().isEmpty()) {
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
            for (Map.Entry<BlockPos, Integer> hidden : ClientVisibility.blocks().entrySet()) {
                float opacity = Visibility.opacity(hidden.getValue());
                BlockPos pos = hidden.getKey();
                BlockState state = level.getBlockState(pos);
                if (opacity <= 0.0F || state.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
                minecraft.getBlockRenderer().renderSingleBlock(state, poseStack, new GhostBuffers(buffers, opacity),
                        openLight(level, pos), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                poseStack.popPose();
            }
            buffers.endBatch();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }

        /** A fully hidden block shows no outline when looked at, which would give it away. */
        @SubscribeEvent
        public static void noOutline(RenderHighlightEvent.Block event) {
            var level = ClientVisibility.ofBlock(event.getTarget().getBlockPos());
            if (level.isPresent() && level.getAsInt() <= Visibility.HIDDEN && !ClientRevelation.active()) {
                event.setCanceled(true);
            }
        }

        /** Leaving the world or changing dimension forgets it all; the server tells again what is in sight. */
        @SubscribeEvent
        public static void forgetOnLeave(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientVisibility.clear();
        }

        @SubscribeEvent
        public static void forgetOnRespawn(ClientPlayerNetworkEvent.Clone event) {
            ClientVisibility.clear();
        }
    }
}
