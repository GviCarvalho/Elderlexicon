package com.elderlexicon.mod.wizard.client;

import com.elderlexicon.mod.caster.client.BeardLayer;
import com.elderlexicon.mod.caster.client.FocusLayer;
import com.elderlexicon.mod.caster.client.GrimoireBookLayer;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * How a Wizard looks: a villager in a long robe, its arms raised in a trance as an evoker raises its arms, its
 * grimoire open in its hands while it reads. Its robe and hat are of where it lives, or of its rank at a university
 * ({@link WizardLook}); its beard is of its degree ({@link BeardLayer}); its focus is drawn in its trance and pointed
 * as it releases what it read ({@link FocusLayer}).
 */
public class WizardRenderer extends MobRenderer<Wizard, WizardModel> {

    public WizardRenderer(EntityRendererProvider.Context context) {
        super(context, new WizardModel(context.bakeLayer(WizardModel.LAYER)), 0.5F);
        addLayer(new BeardLayer<>(this, context.getModelSet()));
        addLayer(new WizardDressLayer(this, context.getModelSet()));
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        // What it holds in its folded arms (a bottle, a burin), only while they are folded.
        addLayer(new CrossedArmsItemLayer<>(this, context.getItemInHandRenderer()) {
            @Override
            public void render(com.mojang.blaze3d.vertex.PoseStack pose,
                               net.minecraft.client.renderer.MultiBufferSource buffers, int light, Wizard wizard,
                               float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                               float netHeadYaw, float headPitch) {
                if (!wizard.castingSpell() && !wizard.pointing() && !wizard.readingBook()) {
                    super.render(pose, buffers, light, wizard, limbSwing, limbSwingAmount, partialTick, ageInTicks,
                            netHeadYaw, headPitch);
                }
            }
        });
        addLayer(new FocusLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new GrimoireBookLayer<>(this, context.getModelSet()));
    }

    /** A villager's size, as a villager is drawn. */
    @Override
    protected void scale(Wizard wizard, com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public ResourceLocation getTextureLocation(Wizard wizard) {
        return WizardLook.of(wizard).texture();
    }
}
