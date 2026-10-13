package com.elderlexicon.mod.illager.client;

import com.elderlexicon.mod.caster.client.BeardLayer;
import com.elderlexicon.mod.caster.client.FocusLayer;
import com.elderlexicon.mod.caster.client.GrimoireBookLayer;
import com.elderlexicon.mod.illager.Sorcerer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * A Sorcerer looks as an evoker does, with a body that reads and points ({@link SorcererModel}): its grimoire held out
 * in its hands while it reads or writes in it, its focus drawn in its trance and pointed as it releases what it read
 * ({@link FocusLayer}); and a beard as long as its degree is high ({@link BeardLayer}).
 */
public class SorcererRenderer extends IllagerRenderer<Sorcerer> {

    private static final ResourceLocation EVOKER = new ResourceLocation("textures/entity/illager/evoker.png");

    public SorcererRenderer(EntityRendererProvider.Context context) {
        super(context, new SorcererModel(context.bakeLayer(ModelLayers.EVOKER)), 0.5F);
        addLayer(new FocusLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new BeardLayer<>(this, context.getModelSet()));
        addLayer(new GrimoireBookLayer<>(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(Sorcerer sorcerer) {
        return EVOKER;
    }
}
