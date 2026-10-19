package com.elderlexicon.mod.invocation.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.invocation.Conjured;
import com.elderlexicon.mod.invocation.Invocations;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The bench's screen, the things thrown as blows, and the conjured things coloured by what they are made of. */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class InvocationClient {

    private InvocationClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(Invocations.MENU.get(), InvocationBenchScreen::new));
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Invocations.THROWN.get(), ThrownThingRenderer::new);
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        Item[] conjured = Invocations.conjured().stream().map(object -> object.get()).toArray(Item[]::new);
        event.register((stack, layer) -> layer == 0 ? Conjured.color(Conjured.matter(stack)) : 0xFFFFFF, conjured);
    }
}
