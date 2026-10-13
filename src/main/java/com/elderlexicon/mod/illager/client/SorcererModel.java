package com.elderlexicon.mod.illager.client;

import com.elderlexicon.mod.caster.client.PointingPose;
import com.elderlexicon.mod.caster.client.ReadingPose;
import com.elderlexicon.mod.illager.Sorcerer;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The evoker's body, which reads (docs/mago-design.md, 5c): while its grimoire is open its arms leave their fold and
 * hold the book out before its chest, and it bends its head over the pages; releasing what it read, it points its focus
 * where it aims ({@link PointingPose}).
 */
public class SorcererModel extends IllagerModel<Sorcerer> {

    private final ModelPart head;
    private final ModelPart crossedArms;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public SorcererModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.crossedArms = root.getChild("arms");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
    }

    @Override
    public void setupAnim(Sorcerer sorcerer, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        super.setupAnim(sorcerer, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (sorcerer.pointing() && !sorcerer.isCastingSpell()) {
            PointingPose.point(head, crossedArms, rightArm, leftArm, ageInTicks);
        } else if (sorcerer.readingBook() && !sorcerer.isCastingSpell()) {
            ReadingPose.hold(head, crossedArms, rightArm, leftArm, ageInTicks, ReadingPose.INWARD);
        }
    }
}
