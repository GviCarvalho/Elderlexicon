package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.galdraria.BurinItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** The burin a mage has at hand to carve a bench with: in either hand, or on the hotbar. */
public final class Burins {

    private Burins() {
    }

    public static Optional<ItemStack> atHand(Player player) {
        for (ItemStack held : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (held.getItem() instanceof BurinItem) {
                return Optional.of(held);
            }
        }
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BurinItem) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }
}
