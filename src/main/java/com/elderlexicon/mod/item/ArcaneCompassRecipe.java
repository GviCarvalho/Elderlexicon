package com.elderlexicon.mod.item;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.function.MarkHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Makes an arcane compass at the crafting table (docs/bussola-arcana-design.md): nuggets of one metal in a diamond, the
 * case, and one more of either metal in the middle. The middle one is the first anchor: renamed at the anvil before, it
 * brings its mark into the dial.
 */
public final class ArcaneCompassRecipe extends CustomRecipe {

    /** The diamond around the middle of a 3x3 grid, and the middle. */
    private static final int[] DIAMOND = {1, 3, 5, 7};
    private static final int MIDDLE = 4;

    public ArcaneCompassRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return metalOf(container) != null || !withNugget(container).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack set = withNugget(container);
        if (!set.isEmpty()) {
            return set;
        }
        Item nugget = metalOf(container);
        if (nugget == null) {
            return ItemStack.EMPTY;
        }
        ItemStack dial = new ItemStack(nugget == Items.GOLD_NUGGET
                ? ElderLexicon.GOLD_ARCANE_COMPASS.get() : ElderLexicon.IRON_ARCANE_COMPASS.get());
        ItemStack middle = container.getItem(MIDDLE);
        MarkHelper.markForItem(middle).ifPresent(mark ->
                ArcaneCompassItem.addNugget(dial, mark, ArcaneCompassItem.Metal.ofNugget(middle)));
        return dial;
    }

    /**
     * A dial and one marked nugget of its metal, anywhere in the grid and nothing else: the dial with the nugget set in it
     * (as at the anvil). Empty when the grid is not that, or the dial is full or has that mark already.
     */
    private static ItemStack withNugget(CraftingContainer container) {
        ItemStack dial = ItemStack.EMPTY;
        ItemStack nugget = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ArcaneCompassItem && dial.isEmpty()) {
                dial = stack;
            } else if ((stack.is(Items.IRON_NUGGET) || stack.is(Items.GOLD_NUGGET)) && nugget.isEmpty()) {
                nugget = stack;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (dial.isEmpty() || nugget.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ArcaneCompassItem.withNugget(dial, nugget).orElse(ItemStack.EMPTY);
    }

    /**
     * The nugget the case is made of, when the grid is a diamond of one metal with a nugget of either in the middle; null
     * otherwise.
     */
    private static Item metalOf(CraftingContainer container) {
        if (container.getWidth() != 3 || container.getHeight() != 3) {
            return null;
        }
        Item metal = container.getItem(DIAMOND[0]).getItem();
        if (metal != Items.IRON_NUGGET && metal != Items.GOLD_NUGGET
                || ArcaneCompassItem.Metal.ofNugget(container.getItem(MIDDLE)) == null) {
            return null;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            int at = i;
            boolean diamond = java.util.Arrays.stream(DIAMOND).anyMatch(slot -> slot == at);
            if (at != MIDDLE && (diamond ? !stack.is(metal) : !stack.isEmpty())) {
                return null;
            }
        }
        return metal;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        return NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ElderLexicon.ARCANE_COMPASS_SERIALIZER.get();
    }
}
