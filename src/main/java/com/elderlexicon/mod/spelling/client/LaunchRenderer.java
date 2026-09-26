package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/** Draws a player thrown by a spell flying, laid along its course head first, until it lands ({@link ClientLaunches}). */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class LaunchRenderer {

    private static boolean drawingFlight;

    private LaunchRenderer() {
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void fly(RenderLivingEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (drawingFlight || !(entity instanceof Player)) {
            return;
        }
        Optional<Vec3> heading = ClientLaunches.heading(entity);
        if (heading.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        FlightPose.apply(poseStack, heading.get(), entity.getBbHeight());
        drawingFlight = true;
        try {
            float yaw = Mth.rotLerp(event.getPartialTick(), entity.yRotO, entity.getYRot());
            ((LivingEntityRenderer) event.getRenderer()).render(entity, yaw, event.getPartialTick(), poseStack,
                    event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            drawingFlight = false;
            poseStack.popPose();
        }
    }

    @SubscribeEvent
    public static void forget(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLaunches.clear();
    }
}
