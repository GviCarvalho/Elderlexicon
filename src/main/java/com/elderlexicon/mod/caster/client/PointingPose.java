package com.elderlexicon.mod.caster.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * How a caster releases what it read, Wizard or Sorcerer (docs/mago-design.md, 5i): its right arm out, its focus
 * pointed where it looks (it looks where it aims), the other arm at its side.
 */
public final class PointingPose {

    private PointingPose() {
    }

    public static void point(ModelPart head, ModelPart crossedArms, ModelPart rightArm, ModelPart leftArm,
                             float ageInTicks) {
        crossedArms.visible = false;
        rightArm.visible = true;
        leftArm.visible = true;
        rightArm.xRot = -Mth.HALF_PI + head.xRot;
        rightArm.yRot = head.yRot - 0.1F;
        rightArm.zRot = 0.0F;
        leftArm.xRot = Mth.sin(ageInTicks * 0.067F) * 0.05F;
        leftArm.yRot = 0.0F;
        leftArm.zRot = -0.1F;
    }
}
