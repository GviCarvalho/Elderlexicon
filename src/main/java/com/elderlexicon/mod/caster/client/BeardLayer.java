package com.elderlexicon.mod.caster.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.caster.RuneCaster;
import com.elderlexicon.mod.mind.Degree;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
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
import net.minecraft.world.entity.LivingEntity;

/**
 * A caster's beard tells its degree (docs/mago-design.md, 5i), Wizard or Sorcerer alike: the longer the beard, the more
 * it knows. An apprentice has none; a master a black one, to the chin; an elder a long white one, down its chest, and
 * its brows turned white. A Sorcerer wears it as an old master of the fighting arts: no beard over its jaw, but a
 * mustache falling in two long strands from beside its nose and a thin beard from its chin; an elder's brows grow long
 * and droop past its face. Drawn on the head, so it follows the head as it turns; the long nose of its people stands
 * out through it.
 */
public class BeardLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel> extends RenderLayer<T, M> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(ElderLexicon.MODID, "caster_beard"), "main");
    private static final ResourceLocation BLACK = new ResourceLocation(ElderLexicon.MODID,
            "textures/entity/caster/beard_black.png");
    private static final ResourceLocation WHITE = new ResourceLocation(ElderLexicon.MODID,
            "textures/entity/caster/beard_white.png");

    private final ModelPart master;
    private final ModelPart elder;
    private final ModelPart villagerBrows;
    private final ModelPart illagerMaster;
    private final ModelPart illagerElder;
    private final ModelPart illagerBrows;

    public BeardLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        ModelPart root = models.bakeLayer(LAYER);
        this.master = root.getChild("master");
        this.elder = root.getChild("elder");
        this.villagerBrows = root.getChild("villager_brows");
        this.illagerMaster = root.getChild("illager_master");
        this.illagerElder = root.getChild("illager_elder");
        this.illagerBrows = root.getChild("illager_brows");
    }

    /**
     * The beards, in the head's space (the head runs from y -10 at its crown to 0 at its chin, its face at z -4): the
     * hair over the jaw and the cheeks, and below the chin what hangs from it. The hair's texture is one tone all over,
     * so every box takes it from the same corner.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("master", CubeListBuilder.create().texOffs(0, 0)
                // From the top of the nose down: over the jaw, at the sides, and a hand's breadth below the chin.
                .addBox(-4.0F, -3.0F, -4.5F, 8.0F, 3.0F, 0.5F)
                .addBox(-4.5F, -3.0F, -4.5F, 0.5F, 3.0F, 3.0F)
                .addBox(4.0F, -3.0F, -4.5F, 0.5F, 3.0F, 3.0F)
                .addBox(-3.5F, 0.0F, -4.5F, 7.0F, 2.0F, 1.0F)
                .addBox(-2.5F, 2.0F, -4.5F, 5.0F, 1.0F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("elder", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -3.0F, -4.6F, 8.0F, 3.0F, 0.6F)
                .addBox(-4.6F, -3.0F, -4.6F, 0.6F, 3.0F, 3.5F)
                .addBox(4.0F, -3.0F, -4.6F, 0.6F, 3.0F, 3.5F)
                // Down its chest, narrowing to a point.
                .addBox(-4.0F, 0.0F, -4.8F, 8.0F, 3.0F, 1.2F)
                .addBox(-3.5F, 3.0F, -4.8F, 7.0F, 3.0F, 1.2F)
                .addBox(-2.5F, 6.0F, -4.7F, 5.0F, 2.0F, 1.0F)
                .addBox(-1.5F, 8.0F, -4.6F, 3.0F, 2.0F, 1.0F), PartPose.ZERO);
        // A Sorcerer's: the mustache's two strands, from beside the nose down past the chin, and the thin beard under
        // the nose; an elder's longer, the strands parting as they fall, the beard down to a point.
        root.addOrReplaceChild("illager_master", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.5F, -2.0F, -4.4F, 1.5F, 1.0F, 0.4F)
                .addBox(1.0F, -2.0F, -4.4F, 1.5F, 1.0F, 0.4F)
                .addBox(-2.5F, -1.0F, -4.5F, 1.0F, 5.0F, 0.6F)
                .addBox(1.5F, -1.0F, -4.5F, 1.0F, 5.0F, 0.6F)
                .addBox(-0.5F, 1.0F, -4.5F, 1.0F, 2.0F, 0.6F), PartPose.ZERO);
        root.addOrReplaceChild("illager_elder", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.5F, -2.0F, -4.4F, 1.5F, 1.0F, 0.4F)
                .addBox(1.0F, -2.0F, -4.4F, 1.5F, 1.0F, 0.4F)
                .addBox(-2.5F, -1.0F, -4.5F, 1.0F, 5.0F, 0.6F)
                .addBox(1.5F, -1.0F, -4.5F, 1.0F, 5.0F, 0.6F)
                .addBox(-3.0F, 4.0F, -4.6F, 1.0F, 5.0F, 0.6F)
                .addBox(2.0F, 4.0F, -4.6F, 1.0F, 5.0F, 0.6F)
                .addBox(-1.0F, 1.0F, -4.6F, 2.0F, 4.0F, 0.8F)
                .addBox(-0.5F, 5.0F, -4.6F, 1.0F, 5.0F, 0.8F), PartPose.ZERO);
        // An elder's brows turn white where its people's brows are, a hair before the face: a villager's one brow
        // over both eyes, an illager's frowning pair.
        root.addOrReplaceChild("villager_brows", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -5.0F, -4.02F, 6.0F, 1.0F, 0.0F), PartPose.ZERO);
        root.addOrReplaceChild("illager_brows", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -6.0F, -4.02F, 2.0F, 1.0F, 0.0F)
                .addBox(2.0F, -6.0F, -4.02F, 2.0F, 1.0F, 0.0F)
                .addBox(-3.0F, -5.0F, -4.02F, 2.0F, 1.0F, 0.0F)
                .addBox(1.0F, -5.0F, -4.02F, 2.0F, 1.0F, 0.0F)
                // ... and grow long past its face, drooping to its cheeks.
                .addBox(-5.5F, -6.5F, -4.5F, 1.5F, 1.0F, 0.5F)
                .addBox(4.0F, -6.5F, -4.5F, 1.5F, 1.0F, 0.5F)
                .addBox(-6.0F, -5.5F, -4.5F, 1.0F, 2.0F, 0.5F)
                .addBox(5.0F, -5.5F, -4.5F, 1.0F, 2.0F, 0.5F)
                .addBox(-6.5F, -3.5F, -4.5F, 1.0F, 3.0F, 0.5F)
                .addBox(5.5F, -3.5F, -4.5F, 1.0F, 3.0F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof RuneCaster caster) || entity.isInvisible()) {
            return;
        }
        Degree degree = caster.shownDegree();
        if (degree == Degree.APPRENTICE) {
            return;
        }
        boolean illager = getParentModel() instanceof net.minecraft.client.model.IllagerModel;
        ModelPart beard = degree == Degree.ELDER ? (illager ? illagerElder : elder) : (illager ? illagerMaster : master);
        pose.pushPose();
        getParentModel().getHead().translateAndRotate(pose);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(
                degree == Degree.ELDER ? WHITE : BLACK));
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        beard.render(pose, vertices, light, overlay);
        if (degree == Degree.ELDER) {
            (illager ? illagerBrows : villagerBrows).render(pose, vertices, light, overlay);
        }
        pose.popPose();
    }
}
