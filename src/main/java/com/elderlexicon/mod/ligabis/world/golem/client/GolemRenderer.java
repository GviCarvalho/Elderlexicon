package com.elderlexicon.mod.ligabis.world.golem.client;

import com.elderlexicon.mod.ligabis.world.golem.GolemEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Renders a {@link GolemEntity} exactly like a vanilla iron golem, except its skin is generated on the
 * fly from the block the ritual was built with (see {@link GolemTextures}).
 */
public final class GolemRenderer extends IronGolemRenderer {

    public GolemRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        if (entity instanceof GolemEntity golem) {
            return GolemTextures.forBlock(golem.getBlockId());
        }
        return super.getTextureLocation(entity);
    }
}
