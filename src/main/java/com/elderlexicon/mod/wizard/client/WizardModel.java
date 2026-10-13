package com.elderlexicon.mod.wizard.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.caster.client.PointingPose;
import com.elderlexicon.mod.caster.client.ReadingPose;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A villager body with arms of its own besides the crossed ones: in a trance the crossed arms part and rise, as an
 * evoker raises its arms; releasing what it read, the right one points its focus where it aims ({@link PointingPose});
 * reading, they hold its grimoire out before it ({@link ReadingPose}).
 */
public class WizardModel extends VillagerModel<Wizard> implements net.minecraft.client.model.ArmedModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(ElderLexicon.MODID, "wizard"), "main");

    private final ModelPart crossedArms;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public WizardModel(ModelPart root) {
        super(root);
        this.crossedArms = root.getChild("arms");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = VillagerModel.createBodyModel();
        PartDefinition root = mesh.getRoot();
        // Whole arms, as long as an evoker's, cut from where a villager's hat brim would be: a Wizard wears no brim.
        root.getChild("head").getChild("hat").addOrReplaceChild("hat_rim", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-3.0F, -2.0F, -2.0F,
                4.0F, 12.0F, 4.0F), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).mirror().addBox(-1.0F, -2.0F,
                -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(5.0F, 2.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Wizard wizard, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        super.setupAnim(wizard, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        boolean casting = wizard.castingSpell();
        crossedArms.visible = !casting;
        rightArm.visible = casting;
        leftArm.visible = casting;
        if (casting) {
            // As an evoker in a trance: arms raised, swaying.
            float sway = Mth.cos(ageInTicks * 0.6662F) * 0.25F;
            rightArm.xRot = sway;
            leftArm.xRot = sway;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightArm.zRot = 2.3561945F;
            leftArm.zRot = -2.3561945F;
        } else if (wizard.pointing()) {
            PointingPose.point(getHead(), crossedArms, rightArm, leftArm, ageInTicks);
        } else if (wizard.readingBook()) {
            ReadingPose.hold(getHead(), crossedArms, rightArm, leftArm, ageInTicks, ReadingPose.INWARD);
        }
    }

    @Override
    public void translateToHand(net.minecraft.world.entity.HumanoidArm arm, com.mojang.blaze3d.vertex.PoseStack pose) {
        (arm == net.minecraft.world.entity.HumanoidArm.LEFT ? leftArm : rightArm).translateAndRotate(pose);
    }
}
