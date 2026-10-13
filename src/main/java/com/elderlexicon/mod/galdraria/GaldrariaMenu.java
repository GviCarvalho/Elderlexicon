package com.elderlexicon.mod.galdraria;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The galdraria table (docs/galdraria-design.md), worked as the anvil is: the thing to engrave and the burin go in, the
 * line of runes is written above them, and the thing engraved comes out. Taking it uses up the thing that went in and
 * wears the burin by one for each rune carved; what was engraved before is carved over. No result shows while the line
 * cannot be carved: nothing written, more than ten columns, a word the spirit does not read, a burin too worn for it,
 * or the very engraving the thing already has.
 */
public final class GaldrariaMenu extends ItemCombinerMenu {

    public static final int ITEM_SLOT = 0;
    public static final int BURIN_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    /** Where the slots are drawn, for the screen. */
    public static final int ITEM_X = 27;
    public static final int BURIN_X = 76;
    public static final int RESULT_X = 134;
    public static final int SLOTS_Y = 50;

    /** The line being written at the table, as the mage typed it. */
    private String typed = "";

    /** On the client, opened by the server. */
    public GaldrariaMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public GaldrariaMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(Galdraria.MENU.get(), id, inventory, access);
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create()
                .withSlot(ITEM_SLOT, ITEM_X, SLOTS_Y, stack -> !(stack.getItem() instanceof BurinItem))
                .withSlot(BURIN_SLOT, BURIN_X, SLOTS_Y, stack -> stack.getItem() instanceof BurinItem)
                .withResultSlot(RESULT_SLOT, RESULT_X, SLOTS_Y)
                .build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(Galdraria.TABLE.get());
    }

    /** The thing on the table, to be engraved. */
    public ItemStack item() {
        return inputSlots.getItem(ITEM_SLOT);
    }

    /** The line written at the table changed: the result is carved anew. */
    public void setText(String text) {
        String kept = text == null ? "" : text;
        typed = kept.length() > Engravings.MAX_CHARS ? kept.substring(0, Engravings.MAX_CHARS) : kept;
        createResult();
    }

    @Override
    public void createResult() {
        resultSlots.setItem(0, carved());
    }

    /** The thing on the table with the line carved in it, or nothing when it cannot be carved. */
    private ItemStack carved() {
        ItemStack item = inputSlots.getItem(ITEM_SLOT);
        ItemStack burin = inputSlots.getItem(BURIN_SLOT);
        List<String> words = Engravings.words(typed);
        if (item.isEmpty() || !(burin.getItem() instanceof BurinItem) || words.isEmpty()
                || words.size() > Engravings.MAX_COLUMNS || Engravings.unreadable(words)) {
            return ItemStack.EMPTY;
        }
        int left = burin.getMaxDamage() - burin.getDamageValue();
        if (!player.getAbilities().instabuild && left < Engravings.runes(words)) {
            return ItemStack.EMPTY;
        }
        String written = Engravings.written(words);
        if (Engravings.of(item).map(written::equals).orElse(false)) {
            return ItemStack.EMPTY;
        }
        ItemStack engraved = item.copyWithCount(1);
        Engravings.engrave(engraved, written);
        return engraved;
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        return hasStack;
    }

    @Override
    protected void onTake(Player player, ItemStack taken) {
        int runes = Engravings.runes(Engravings.words(typed));
        ItemStack item = inputSlots.getItem(ITEM_SLOT);
        item.shrink(1);
        inputSlots.setItem(ITEM_SLOT, item);
        ItemStack burin = inputSlots.getItem(BURIN_SLOT);
        if (!player.getAbilities().instabuild && player instanceof ServerPlayer server) {
            burin.hurtAndBreak(runes, server, broken -> { });
            inputSlots.setItem(BURIN_SLOT, burin);
        }
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS,
                1.0F, 1.6F));
    }

    @Override
    public int getSlotToQuickMoveTo(ItemStack stack) {
        return stack.getItem() instanceof BurinItem ? BURIN_SLOT : ITEM_SLOT;
    }
}
