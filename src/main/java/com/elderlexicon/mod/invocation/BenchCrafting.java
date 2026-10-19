package com.elderlexicon.mod.invocation;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the grid of the invocation bench makes (docs/invocacao-design.md, section 3): what a recipe of the game makes of
 * it, as at any crafting table; else, a format's conjured thing; else nothing, and what is on the grid is only what it is.
 */
public final class BenchCrafting {

    private BenchCrafting() {
    }

    /** A thing the grid makes, and how it is taken off the grid. */
    public record Made(ItemStack thing, Optional<CraftingRecipe> recipe, Optional<Formats.Drawn> drawn) {
    }

    public static Optional<Made> made(Level level, Container grid) {
        TransientCraftingContainer crafting = craftingOf(grid);
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, level);
        if (recipe.isPresent()) {
            ItemStack thing = recipe.get().assemble(crafting, level.registryAccess());
            if (!thing.isEmpty()) {
                return Optional.of(new Made(thing, recipe, Optional.empty()));
            }
        }
        return Formats.drawn(grid).map(drawn -> new Made(Formats.make(drawn, grid), Optional.empty(), Optional.of(drawn)))
                .filter(made -> !made.thing().isEmpty());
    }

    /**
     * Takes what was made off the grid: one of each square used, and what a recipe leaves behind (a bucket) put back
     * where it was, or given to {@code player}, or dropped.
     */
    public static void take(Level level, Container grid, Made made, Player player) {
        if (made.recipe().isPresent()) {
            NonNullList<ItemStack> left = made.recipe().get().getRemainingItems(craftingOf(grid));
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack there = grid.getItem(i);
                if (!there.isEmpty()) {
                    grid.removeItem(i, 1);
                }
                ItemStack back = i < left.size() ? left.get(i) : ItemStack.EMPTY;
                if (back.isEmpty()) {
                    continue;
                }
                if (grid.getItem(i).isEmpty()) {
                    grid.setItem(i, back);
                } else if (player == null || !player.getInventory().add(back)) {
                    if (player != null) {
                        player.drop(back, false);
                    }
                }
            }
            return;
        }
        made.drawn().ifPresent(drawn -> {
            List<Integer> used = new ArrayList<>(drawn.principal());
            used.addAll(drawn.parts());
            used.forEach(square -> grid.removeItem(square, 1));
        });
    }

    /** Everything on the grid, as it is: what a grid that makes nothing gives when it is sent off. */
    public static List<ItemStack> everything(Container grid) {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack there = grid.getItem(i);
            if (!there.isEmpty()) {
                all.add(there.copy());
                grid.setItem(i, ItemStack.EMPTY);
            }
        }
        return all;
    }

    private static TransientCraftingContainer craftingOf(Container grid) {
        TransientCraftingContainer crafting = new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return false;
            }
        }, Formats.SIDE, Formats.SIDE);
        for (int i = 0; i < Formats.SIDE * Formats.SIDE; i++) {
            crafting.setItem(i, grid.getItem(i).copy());
        }
        return crafting;
    }
}
