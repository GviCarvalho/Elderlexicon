package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.life.Homunculus;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** A homunculus: a figure of a person in pale, unfinished flesh, with no face yet (a texture of our own). */
public class HomunculusRenderer extends HumanoidMobRenderer<Homunculus, HumanoidModel<Homunculus>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ElderLexicon.MODID, "textures/entity/homunculus.png");

    public HomunculusRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(Homunculus homunculus) {
        return TEXTURE;
    }
}
