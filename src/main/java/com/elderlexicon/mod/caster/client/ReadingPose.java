package com.elderlexicon.mod.caster.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * How a caster holds its grimoire to read it, Wizard or Sorcerer (docs/mago-design.md, 5c): both arms forward and in,
 * the hands meeting under the book before its chest, the head bent over the pages; now and then a breath of movement,
 * so it does not stand as a statue.
 */
public final class ReadingPose {

    /** How far in the arms turn for the hands to meet. */
    public static final float INWARD = 0.42F;

    private ReadingPose() {
    }

    public static void hold(ModelPart head, ModelPart crossedArms, ModelPart rightArm, ModelPart leftArm,
                            float ageInTicks, float inward) {
        float breath = Mth.sin(ageInTicks * 0.067F) * 0.03F;
        head.xRot = 0.4F + breath;
        head.yRot *= 0.25F; // its eyes on the page, not on whoever passes
        crossedArms.visible = false;
        rightArm.visible = true;
        leftArm.visible = true;
        rightArm.xRot = -1.05F + breath;
        leftArm.xRot = -1.05F + breath;
        rightArm.yRot = -inward;
        leftArm.yRot = inward;
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
    }
}
