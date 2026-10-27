package com.elderlexicon.mod.invocation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The invocation bench worked by hand, as a crafting table is, but the grid is the bench's and stays on it when the
 * screen is closed: matter may be invoked into it at any time. The result is what the grid makes (BenchCrafting).
 */
public final class InvocationBenchMenu extends AbstractContainerMenu {

    public static final int RESULT_SLOT = 0;
    /** Where the player's inventory starts, centred under the wider bench. */
    public static final int INVENTORY_X = 59;
    private static final int GRID_START = 1;
    private static final int GRID_END = GRID_START + Formats.SIDE * Formats.SIDE;
    private static final int INVENTORY_END = GRID_END + 36;

    private final Container grid;
    private final ResultContainer result = new ResultContainer();
    private final Level level;
    private final BlockPos pos;
    private final ContainerListener listener = changed -> slotsChanged(changed);
    private Optional<BenchCrafting.Made> made = Optional.empty();

    /** On the client, opened by the server. */
    public InvocationBenchMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(Formats.SIDE * Formats.SIDE));
    }

    public InvocationBenchMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, inventory.player.level().getBlockEntity(pos) instanceof InvocationBenchBlockEntity bench
                ? bench.grid() : new SimpleContainer(Formats.SIDE * Formats.SIDE));
    }

    private InvocationBenchMenu(int id, Inventory inventory, BlockPos pos, Container grid) {
        super(Invocations.MENU.get(), id);
        this.grid = grid;
        this.level = inventory.player.level();
        this.pos = pos;
        addSlot(new Slot(result, 0, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack taken) {
                made.ifPresent(thing -> BenchCrafting.take(level, grid, thing, player));
                slotsChanged(grid);
                super.onTake(player, taken);
            }
        });
        for (int y = 0; y < Formats.SIDE; y++) {
            for (int x = 0; x < Formats.SIDE; x++) {
                addSlot(new Slot(grid, x + y * Formats.SIDE, 30 + x * 18, 17 + y * 18));
            }
        }
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new Slot(inventory, x + y * 9 + 9, INVENTORY_X + x * 18, 84 + y * 18));
            }
        }
        for (int x = 0; x < 9; x++) {
            addSlot(new Slot(inventory, x, INVENTORY_X + x * 18, 142));
        }
        if (grid instanceof SimpleContainer simple && !level.isClientSide) {
            simple.addListener(listener);
        }
        slotsChanged(grid);
    }

    @Override
    public void slotsChanged(Container changed) {
        if (level.isClientSide || changed != grid) {
            return;
        }
        made = BenchCrafting.made(level, grid);
        result.setItem(0, made.map(thing -> thing.thing().copy()).orElse(ItemStack.EMPTY));
        broadcastChanges();
    }

    /** Where the bench stands. */
    public BlockPos pos() {
        return pos;
    }

    /** A bench left with no mark, no line and nothing on it is a crafting table again. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (grid instanceof SimpleContainer simple) {
            simple.removeListener(listener);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof InvocationBenchBlockEntity bench
                && bench.blank()) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return level.getBlockState(pos).is(Invocations.BENCH.get())
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(stack, GRID_END, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, moved);
        } else if (index < GRID_END) {
            if (!moveItemStackTo(stack, GRID_END, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, GRID_START, GRID_END, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return moved;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }
}
