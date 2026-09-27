package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.scene.client.ArcBoltRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles client-only MOD bus registrations for the Spelling feature.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpellingClientModBusEvents {

    private SpellingClientModBusEvents() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        SpellingKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ElderLexicon.PLACED_SCROLL.get(), PlacedScrollRenderer::new);
        event.registerEntityRenderer(ElderLexicon.ARC_BOLT.get(), ArcBoltRenderer::new);
        event.registerEntityRenderer(ElderLexicon.ELEMENT_ORB.get(),
                com.elderlexicon.mod.spell.function.client.ElementOrbRenderer::new);
        event.registerEntityRenderer(ElderLexicon.BLACK_HOLE.get(),
                com.elderlexicon.mod.spell.function.client.BlackHoleRenderer::new);
    }
}
