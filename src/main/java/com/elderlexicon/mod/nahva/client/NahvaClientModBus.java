package com.elderlexicon.mod.nahva.client;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers how Nahvá's own beings are drawn: the reflection of the tower's mirror. */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NahvaClientModBus {

    private NahvaClientModBus() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ElderLexicon.REFLECTION.get(), ReflectionRenderer::new);
    }
}
