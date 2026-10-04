package com.elderlexicon.mod.galdraria;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The grindstone scrapes an engraving away (docs/galdraria-design.md): an engraved item alone on it comes out bare of
 * runes, and nothing else changes; enchantments, if any, are ground on a second pass, as always.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class GrindstoneScraping {

    private GrindstoneScraping() {
    }

    @SubscribeEvent
    public static void onPlace(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem();
        ItemStack bottom = event.getBottomItem();
        ItemStack engraved = top.isEmpty() ? bottom : bottom.isEmpty() ? top : ItemStack.EMPTY;
        if (!Engravings.has(engraved)) {
            return;
        }
        ItemStack scraped = engraved.copy();
        Engravings.scrape(scraped);
        event.setOutput(scraped);
        event.setXp(0);
    }
}
