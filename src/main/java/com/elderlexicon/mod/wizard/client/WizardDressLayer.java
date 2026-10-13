package com.elderlexicon.mod.wizard.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.wizard.Wizard;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * What a Wizard wears beyond its robe (docs/mago-design.md, 5i): the hat of its place or rank, and a Magister's stole
 * and tassel in its chair's colour. The hats are drawn on the head, so they follow it as it turns.
 */
public class WizardDressLayer extends RenderLayer<Wizard, WizardModel> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(ElderLexicon.MODID, "wizard_hats"), "main");
    private static final ResourceLocation HATS = new ResourceLocation(ElderLexicon.MODID,
            "textures/entity/wizard/hats.png");
    /** The stole alone, pale, tinted over the Magister's gown. */
    private static final ResourceLocation STOLE = new ResourceLocation(ElderLexicon.MODID,
            "textures/entity/wizard/magister_stole.png");

    private final ModelPart pointed;
    private final ModelPart square;
    private final ModelPart tassel;
    private final ModelPart velvet;
    private final ModelPart beret;

    public WizardDressLayer(RenderLayerParent<Wizard, WizardModel> parent, EntityModelSet models) {
        super(parent);
        ModelPart root = models.bakeLayer(LAYER);
        this.pointed = root.getChild("pointed");
        this.square = root.getChild("square");
        this.tassel = root.getChild("tassel");
        this.velvet = root.getChild("velvet");
        this.beret = root.getChild("beret");
    }

    /**
     * The hats, in the head's space (its crown at y -10), on one 128x64 sheet: the tower's pointed hat, a brim and four
     * tiers bending back as they rise; the square cap, its button and tassel apart to be tinted; the Rector's tall
     * velvet cap; the Scriba's beret, worn aslant, a quill in it.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition pointed = root.addOrReplaceChild("pointed", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-6.0F, -1.0F, -6.0F, 12.0F, 1.0F, 12.0F), PartPose.offset(0.0F, -9.5F, 0.0F));
        PartDefinition tier1 = pointed.addOrReplaceChild("tier1", CubeListBuilder.create().texOffs(0, 13)
                .addBox(-4.0F, -4.0F, -4.0F, 8.0F, 4.0F, 8.0F), PartPose.offset(0.0F, -1.0F, 0.0F));
        PartDefinition tier2 = tier1.addOrReplaceChild("tier2", CubeListBuilder.create().texOffs(32, 13)
                        .addBox(-3.0F, -4.0F, -3.0F, 6.0F, 4.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, -4.0F, 0.5F, -0.1F, 0.0F, 0.0F));
        PartDefinition tier3 = tier2.addOrReplaceChild("tier3", CubeListBuilder.create().texOffs(0, 25)
                        .addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -4.0F, 0.6F, -0.2F, 0.0F, 0.0F));
        tier3.addOrReplaceChild("tier4", CubeListBuilder.create().texOffs(16, 25)
                        .addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, 0.6F, -0.3F, 0.0F, 0.0F));

        root.addOrReplaceChild("square", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.5F))
                .texOffs(0, 42).addBox(-7.0F, -3.5F, -7.0F, 14.0F, 1.0F, 14.0F), PartPose.offset(0.0F, -10.0F, 0.0F));
        root.addOrReplaceChild("tassel", CubeListBuilder.create()
                .texOffs(56, 42).addBox(-0.5F, -4.5F, -0.5F, 1.0F, 1.0F, 1.0F)
                .texOffs(60, 42).addBox(6.0F, -2.5F, -7.0F, 1.0F, 4.0F, 1.0F), PartPose.offset(0.0F, -10.0F, 0.0F));

        root.addOrReplaceChild("velvet", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-4.5F, -3.0F, -4.5F, 9.0F, 3.0F, 9.0F, new CubeDeformation(0.1F))
                .texOffs(64, 12).addBox(-5.0F, -5.0F, -5.0F, 10.0F, 2.0F, 10.0F), PartPose.offset(0.0F, -10.0F, 0.0F));

        PartDefinition beret = root.addOrReplaceChild("beret", CubeListBuilder.create().texOffs(64, 24)
                        .addBox(-5.0F, -1.5F, -5.0F, 10.0F, 2.0F, 10.0F),
                PartPose.offsetAndRotation(0.0F, -10.0F, 0.0F, 0.0F, 0.0F, 0.15F));
        beret.addOrReplaceChild("quill", CubeListBuilder.create().texOffs(104, 24)
                        .addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(3.5F, -1.0F, 1.5F, 0.4F, 0.0F, 0.35F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, Wizard wizard, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (wizard.isInvisible()) {
            return;
        }
        WizardLook look = WizardLook.of(wizard);
        int overlay = LivingEntityRenderer.getOverlayCoords(wizard, 0.0F);
        int chair = wizard.chair().map(WizardLook::chairColor).orElse(0xE8E8E8);
        float r = (chair >> 16 & 0xFF) / 255.0F;
        float g = (chair >> 8 & 0xFF) / 255.0F;
        float b = (chair & 0xFF) / 255.0F;
        if (look == WizardLook.MAGISTER) {
            renderColoredCutoutModel(getParentModel(), STOLE, pose, buffers, light, wizard, r, g, b);
        }
        ModelPart hat = switch (look.hat()) {
            case NONE -> null;
            case POINTED -> pointed;
            case SQUARE -> square;
            case VELVET -> velvet;
            case BERET -> beret;
        };
        if (hat == null) {
            return;
        }
        pose.pushPose();
        getParentModel().getHead().translateAndRotate(pose);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(HATS));
        hat.render(pose, vertices, light, overlay);
        if (hat == square) {
            tassel.render(pose, vertices, light, overlay, r, g, b, 1.0F);
        }
        pose.popPose();
    }
}
