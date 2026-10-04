package com.elderlexicon.mod.galdraria;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The galdraria table (docs/galdraria-design.md): one slot for the thing to engrave, one for the burin. The mage
 * writes one line of up to ten runes; carving them wears the burin by one for each rune. What was engraved before is
 * carved over. The items go back to the mage when the table is left.
 */
public final class GaldrariaMenu extends AbstractContainerMenu {

    public static final int ITEM_SLOT = 0;
    public static final int BURIN_SLOT = 1;
    /** Where the slots are drawn, for the screen. */
    public static final int ITEM_X = 26;
    public static final int BURIN_X = 8;
    public static final int SLOTS_Y = 52;
    public static final int INVENTORY_Y = 94;

    private final ContainerLevelAccess access;
    private final Container table = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };

    /** On the client, opened by the server. */
    public GaldrariaMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public GaldrariaMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(Galdraria.MENU.get(), id);
        this.access = access;
        addSlot(new Slot(table, ITEM_SLOT, ITEM_X, SLOTS_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(table, BURIN_SLOT, BURIN_X, SLOTS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BurinItem;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
    }

    /** The thing on the table, to be engraved. */
    public ItemStack item() {
        return table.getItem(ITEM_SLOT);
    }

    public ItemStack burin() {
        return table.getItem(BURIN_SLOT);
    }

    /** Carves {@code typed} into the thing on the table, if the burin can; tells the mage why not, if not. */
    public void engrave(ServerPlayer player, String typed) {
        ItemStack item = item();
        ItemStack burin = burin();
        if (item.isEmpty()) {
            tell(player, "Ponha na mesa o que vai gravar.");
            return;
        }
        if (!(burin.getItem() instanceof BurinItem)) {
            tell(player, "Falta o buril para gravar.");
            return;
        }
        List<String> words = Engravings.words(typed);
        if (words.isEmpty()) {
            tell(player, "Nada a gravar. Para raspar uma gravacao, use o rebolo.");
            return;
        }
        if (words.size() > Engravings.MAX_COLUMNS) {
            tell(player, "Cabem so " + Engravings.MAX_COLUMNS + " runas num item.");
            return;
        }
        if (Engravings.read(Engravings.written(words)).stream().anyMatch(String::isEmpty)) {
            tell(player, "Ha uma palavra que o espirito nao le.");
            return;
        }
        String written = Engravings.written(words);
        if (Engravings.of(item).map(written::equals).orElse(false)) {
            return;
        }
        int left = burin.getMaxDamage() - burin.getDamageValue();
        if (!player.getAbilities().instabuild && left < words.size()) {
            tell(player, "O buril so tem " + left + " runa(s) de gume.");
            return;
        }
        Engravings.engrave(item, written);
        if (!player.getAbilities().instabuild) {
            burin.hurtAndBreak(words.size(), player, broken -> { });
        }
        table.setChanged();
        broadcastChanges();
        tell(player, "Gravado: " + String.join(" ", Engravings.read(written)));
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, Galdraria.TABLE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, table));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int inventoryEnd = slots.size();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof BurinItem && !slots.get(BURIN_SLOT).hasItem()) {
            if (!moveItemStackTo(stack, BURIN_SLOT, BURIN_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, ITEM_SLOT, ITEM_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }
}
