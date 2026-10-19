package com.elderlexicon.mod.item;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.function.MarkHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Says what mark an item bears, so a name given at the anvil can be seen to have become one (docs/marks.md); and, for a
 * renamed item whose name could not, why: a nugget renamed "igni" bears no mark, and no dial would take it.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class MarkTooltips {

    private MarkTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Optional<String> mark = MarkHelper.markForItem(stack);
        if (mark.isPresent()) {
            event.getToolTip().add(Component.translatable("tooltip.elderlexicon.mark", mark.get())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (stack.hasCustomHoverName()) {
            event.getToolTip().add(Component.translatable("tooltip.elderlexicon.mark.unwritten")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
