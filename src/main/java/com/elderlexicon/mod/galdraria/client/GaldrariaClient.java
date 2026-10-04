package com.elderlexicon.mod.galdraria.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.galdraria.Engravings;
import com.elderlexicon.mod.galdraria.Galdraria;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The galdraria table's screen, and what is carved in an item shown on its tooltip: in glyphs, and as it is read. */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GaldrariaClient {

    private static final Style SGA = Style.EMPTY.withFont(new ResourceLocation("minecraft", "alt"));

    private GaldrariaClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(Galdraria.MENU.get(), GaldrariaScreen::new));
        MinecraftForge.EVENT_BUS.addListener(GaldrariaClient::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        Engravings.of(event.getItemStack()).ifPresent(written -> {
            event.getToolTip().add(Component.translatable("tooltip.elderlexicon.engraved")
                    .withStyle(ChatFormatting.DARK_PURPLE)
                    .append(Component.literal(written).withStyle(SGA.withColor(ChatFormatting.LIGHT_PURPLE))));
            event.getToolTip().add(Component.literal(String.join(" ", Engravings.read(written)))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        });
    }
}
