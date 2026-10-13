package com.elderlexicon.mod.caster.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.caster.RuneCaster;
import com.elderlexicon.mod.mind.Degree;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A caster's focus in its right hand (docs/mago-design.md, 5i): drawn out while it reads in its trance, the arms raised,
 * and pointed where it aims while what it read is released ({@link PointingPose}), along the arm. Put away at peace.
 * <p>
 * Only drawn: the focus is not in its hand as an item, so it conducts nothing (a creature's spell costs it as it always
 * did). An apprentice's an improvised wand, a master's or an elder's a made one; staves and sceptres later.
 */
public class FocusLayer<T extends LivingEntity, M extends EntityModel<T> & ArmedModel> extends RenderLayer<T, M> {

    /** Where the hand is in the arm's space, as ItemInHandLayer puts an item there: down the arm from the shoulder. */
    private static final float HAND_X = -1.0F / 16.0F;
    private static final float HAND_Y = 0.625F;
    private static final float HAND_Z = -0.125F;

    private final ItemInHandRenderer items;
    private ItemStack improvised;
    private ItemStack made;

    public FocusLayer(RenderLayerParent<T, M> parent, ItemInHandRenderer items) {
        super(parent);
        this.items = items;
    }

    private ItemStack focus(Degree degree) {
        if (improvised == null) {
            improvised = new ItemStack(ElderLexicon.IMPROVISED_WAND.get());
            made = new ItemStack(ElderLexicon.WAND.get());
        }
        return degree == Degree.APPRENTICE ? improvised : made;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof RuneCaster caster) || entity.isInvisible()) {
            return;
        }
        boolean pointing = caster.pointing();
        if (!pointing && !caster.entranced()) {
            return;
        }
        pose.pushPose();
        getParentModel().translateToHand(HumanoidArm.RIGHT, pose);
        if (pointing) {
            // An item in the hand stands out of the fist, across the arm: turned about the hand to run along it.
            pose.translate(HAND_X, HAND_Y, HAND_Z);
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            pose.translate(-HAND_X, -HAND_Y, -HAND_Z);
        }
        // As an item in the hand is held (ItemInHandLayer).
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(1.0F / 16.0F, 0.125F, -0.625F);
        items.renderItem(entity, focus(caster.shownDegree()), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose,
                buffers, light);
        pose.popPose();
    }
}
