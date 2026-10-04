package com.elderlexicon.mod.spelling.inscription;

import com.elderlexicon.mod.spelling.client.SgaFont;
import com.elderlexicon.mod.ElderLexicon;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The runes written on block faces, as this client knows them: drawn on their faces in their pigment's colour (glow
 * ink shining in the dark), and opened for writing when the player uses a quill on a face with a pigment in the pack.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class ClientInscriptions {

    private static final Style SGA = SgaFont.STYLE;
    private static final Map<Long, Inscription> WRITTEN = new HashMap<>();
    /** How far written faces are drawn, in blocks. */
    private static final double SEEN = 48.0D;
    /** Font pixels to a block: a face holds four short lines. */
    private static final float SCALE = 1.0F / 72.0F;

    private ClientInscriptions() {
    }

    static void receive(List<Inscription> inscriptions, boolean replace) {
        if (replace) {
            WRITTEN.clear();
        }
        for (Inscription inscription : inscriptions) {
            if (inscription.text().isBlank()) {
                WRITTEN.remove(inscription.key());
            } else {
                WRITTEN.put(inscription.key(), inscription);
            }
        }
    }

    public static java.util.Collection<Inscription> all() {
        return WRITTEN.values();
    }

    public static String textAt(BlockPos pos, Direction face) {
        Inscription inscription = WRITTEN.get(Inscription.key(pos, face));
        return inscription == null ? "" : inscription.text();
    }

    /** A quill (a feather) used on a face: open it for writing, if there is a pigment to write with or runes to erase. */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !event.getItemStack().is(net.minecraft.world.item.Items.FEATHER)) {
            return;
        }
        BlockPos pos = event.getPos();
        Direction face = event.getFace() == null ? Direction.UP : event.getFace();
        String existing = textAt(pos, face);
        if (InscribePacket.pigmentSlot(event.getEntity()) < 0 && existing.isEmpty()) {
            event.getEntity().displayClientMessage(Component.literal("Para escrever, tenha um pigmento no inventário."),
                    true);
            return;
        }
        Minecraft.getInstance().setScreen(new InscriptionScreen(pos, face, existing));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || WRITTEN.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 eye = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        // As for the other world drawings of the mod: the camera's turn is already in the global model-view here.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        for (Inscription inscription : WRITTEN.values()) {
            BlockPos pos = inscription.pos();
            if (pos.getCenter().distanceToSqr(eye) > SEEN * SEEN || level.getBlockState(pos).isAir()
                    || com.elderlexicon.mod.spelling.client.ClientCircles.hides(pos, inscription.face())) {
                continue;
            }
            Direction face = inscription.face();
            pose.pushPose();
            Vec3 center = pos.getCenter().add(face.getStepX() * 0.502D, face.getStepY() * 0.502D,
                    face.getStepZ() * 0.502D);
            pose.translate(center.x - eye.x, center.y - eye.y, center.z - eye.z);
            switch (face) {
                case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180.0F));
                case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90.0F));
                case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
                case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
                case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                default -> {
                }
            }
            pose.scale(SCALE, -SCALE, SCALE);
            int light = inscription.glow() ? LightTexture.FULL_BRIGHT
                    : LevelRenderer.getLightColor(level, pos.relative(face));
            String[] lines = inscription.text().split("\n");
            float top = -lines.length * 10 / 2.0F;
            for (int i = 0; i < lines.length; i++) {
                Component line = Component.literal(lines[i]).withStyle(SGA);
                float x = -font.width(line) / 2.0F;
                font.drawInBatch(line, x, top + i * 10, 0xFF000000 | inscription.color(), false, pose.last().pose(),
                        buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
            }
            pose.popPose();
        }
        buffers.endBatch();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
