package com.elderlexicon.mod.item;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Sets one more nugget in an arcane compass at the anvil (docs/bussola-arcana-design.md): an iron or gold nugget, in a
 * dial of either metal, renamed at the anvil before, so it bears a mark. Up to five; a mark the dial already has is not set twice. (The
 * crafting table does the same: {@link ArcaneCompassRecipe}.)
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class ArcaneCompassAnvil {

    private static final int LEVEL_COST = 1;

    private ArcaneCompassAnvil() {
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack dial = event.getLeft();
        Optional<ItemStack> set = ArcaneCompassItem.withNugget(dial, event.getRight());
        if (set.isEmpty()) {
            return;
        }
        ItemStack output = set.get();
        // A name typed in the field renames the dial, as the anvil does; the name it already has is no new name (it
        // would give the dial a mark of its own name).
        String name = event.getName();
        if (name != null && !name.isBlank() && !name.equals(dial.getHoverName().getString())) {
            output.setHoverName(Component.literal(name));
        }
        event.setOutput(output);
        event.setCost(LEVEL_COST);
        event.setMaterialCost(1);
    }
}
