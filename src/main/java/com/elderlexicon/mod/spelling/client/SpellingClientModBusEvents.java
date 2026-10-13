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
        event.registerEntityRenderer(ElderLexicon.HOMUNCULUS.get(), HomunculusRenderer::new);
        event.registerEntityRenderer(ElderLexicon.SORCERER.get(),
                com.elderlexicon.mod.illager.client.SorcererRenderer::new);
        event.registerEntityRenderer(ElderLexicon.WIZARD.get(),
                com.elderlexicon.mod.wizard.client.WizardRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(com.elderlexicon.mod.wizard.client.WizardModel.LAYER,
                com.elderlexicon.mod.wizard.client.WizardModel::createBodyLayer);
        event.registerLayerDefinition(com.elderlexicon.mod.caster.client.BeardLayer.LAYER,
                com.elderlexicon.mod.caster.client.BeardLayer::createLayer);
        event.registerLayerDefinition(com.elderlexicon.mod.wizard.client.WizardDressLayer.LAYER,
                com.elderlexicon.mod.wizard.client.WizardDressLayer::createLayer);
    }
}
