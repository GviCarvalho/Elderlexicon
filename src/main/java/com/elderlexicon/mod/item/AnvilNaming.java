package com.elderlexicon.mod.item;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.function.MarkHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * A name given at the anvil is a mark (docs/varinhas-design.md): the item renamed "Varinha do Gui" carries the mark
 * {@code varinha_do_gui}, as if the Elder Brush had written it. Taking the name away takes away the mark it made; a mark
 * written some other way stays.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class AnvilNaming {

    private AnvilNaming() {
    }

    @SubscribeEvent
    public static void onAnvilTake(AnvilRepairEvent event) {
        ItemStack output = event.getOutput();
        ItemStack before = event.getLeft();
        if (output.isEmpty()) {
            return;
        }
        if (output.hasCustomHoverName()) {
            MarkHelper.markFromName(output.getHoverName().getString()).ifPresent(mark -> MarkHelper.applyMark(output, mark));
            return;
        }
        if (before.hasCustomHoverName()) {
            Optional<String> named = MarkHelper.markFromName(before.getHoverName().getString());
            if (named.isPresent() && named.equals(MarkHelper.markForItem(output))) {
                MarkHelper.applyMark(output, null);
            }
        }
    }
}
