package com.elderlexicon.mod.spelling.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Lays a body along the way it flies, head first, as a player gliding with wings is drawn: the model's up turns to
 * point where it is going, about the middle of the body.
 */
final class FlightPose {

    private FlightPose() {
    }

    /** Turns {@code poseStack}, at the body's feet, so the body of {@code height} points along {@code direction}. */
    static void apply(PoseStack poseStack, Vec3 direction, float height) {
        Vec3 unit = direction.normalize();
        float middle = height / 2.0F;
        poseStack.translate(0.0F, middle, 0.0F);
        poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) unit.x, (float) unit.y, (float) unit.z));
        poseStack.translate(0.0F, -middle, 0.0F);
    }
}
