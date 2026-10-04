package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.item.WandItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * A wand is seen as it is made (docs/varinhas-design.md): the haste as drawn, the grip wrapped around its butt and the
 * gem set at its tip, each in the color the foci data gives it. The {@code setting} property picks the model that has a
 * gem layer.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WandSprites {

    private static final ResourceLocation SETTING = new ResourceLocation(ElderLexicon.MODID, "setting");

    private WandSprites() {
    }

    private static List<RegistryObject<Item>> wands() {
        return List.of(ElderLexicon.IMPROVISED_WAND, ElderLexicon.IMPROVISED_WAND_BONE, ElderLexicon.IMPROVISED_WAND_BAMBOO,
                ElderLexicon.IMPROVISED_WAND_BLAZE, ElderLexicon.WAND, ElderLexicon.WAND_BONE, ElderLexicon.WAND_BAMBOO,
                ElderLexicon.WAND_BLAZE);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> wands().forEach(wand -> ItemProperties.register(wand.get(), SETTING,
                (stack, level, entity, seed) -> stack.getItem() instanceof WandItem item && item.hasSetting(stack)
                        ? 1.0F : 0.0F)));
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> stack.getItem() instanceof WandItem wand ? wand.layerColor(stack, layer) : -1,
                wands().stream().map(RegistryObject::get).toArray(Item[]::new));
    }
}
