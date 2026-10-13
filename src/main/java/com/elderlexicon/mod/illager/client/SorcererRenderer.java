package com.elderlexicon.mod.illager.client;

import com.elderlexicon.mod.caster.client.GrimoireBookLayer;
import com.elderlexicon.mod.illager.Sorcerer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EvokerRenderer;

/** A Sorcerer looks as an evoker does, with its grimoire open in its hands while it reads or writes in it. */
public class SorcererRenderer extends EvokerRenderer<Sorcerer> {

    public SorcererRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new GrimoireBookLayer<>(this, context.getModelSet()));
    }
}
