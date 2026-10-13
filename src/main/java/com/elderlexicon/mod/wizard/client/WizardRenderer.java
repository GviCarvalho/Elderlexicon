package com.elderlexicon.mod.wizard.client;

import com.elderlexicon.mod.caster.client.GrimoireBookLayer;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * How a Wizard looks: a villager in a long robe, its arms raised in a trance as an evoker raises its arms, its
 * grimoire open in its hands while it reads. For now the wandering trader's robe (the game's own texture, only
 * referred to), until the Wizard has a texture of its own.
 */
public class WizardRenderer extends MobRenderer<Wizard, WizardModel> {

    private static final ResourceLocation ROBE = new ResourceLocation("textures/entity/wandering_trader.png");

    public WizardRenderer(EntityRendererProvider.Context context) {
        super(context, new WizardModel(context.bakeLayer(WizardModel.LAYER)), 0.5F);
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        addLayer(new CrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new GrimoireBookLayer<>(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(Wizard wizard) {
        return ROBE;
    }
}
