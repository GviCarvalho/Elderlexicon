package com.elderlexicon.mod.spelling.recipe;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.lexicon.Foci;
import com.elderlexicon.mod.spelling.item.ModularWandItem;
import com.elderlexicon.mod.spelling.item.WandItem;
import com.elderlexicon.mod.spelling.item.WandParts;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;

/**
 * Assembles a wand at the crafting table (docs/varinhas-design.md): a wand with the parts it still lacks. A haste takes a
 * grip, a gem, or both; a wand with a grip takes a gem; a haste with a gem takes a grip. A part already there is never
 * changed here: a wand with a grip takes no other grip, nor a wand with a gem another gem.
 */
public final class WandUpgradeRecipe extends CustomRecipe {

    private static final Map<Item, Item> HELD_WAND_OF = Map.of(
            ElderLexicon.IMPROVISED_WAND.get(), ElderLexicon.WAND.get(),
            ElderLexicon.IMPROVISED_WAND_BONE.get(), ElderLexicon.WAND_BONE.get(),
            ElderLexicon.IMPROVISED_WAND_BAMBOO.get(), ElderLexicon.WAND_BAMBOO.get(),
            ElderLexicon.IMPROVISED_WAND_BLAZE.get(), ElderLexicon.WAND_BLAZE.get()
    );

    public WandUpgradeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return findAssembly(container).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        Optional<Assembly> found = findAssembly(container);
        if (found.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Assembly assembly = found.get();
        ItemStack given = assembly.wand();
        ItemStack result;
        if (assembly.grip() != null) {
            result = new ItemStack(HELD_WAND_OF.get(given.getItem()));
            WandItem.carrySetting(given, result);
            if (given.hasCustomHoverName()) {
                result.setHoverName(given.getHoverName());
            }
            if (result.getItem() instanceof ModularWandItem wand) {
                wand.applyGrip(result, assembly.grip().id());
            }
        } else {
            result = given.copyWithCount(1);
        }
        if (assembly.setting() != null && result.getItem() instanceof WandItem wand) {
            wand.applySetting(result, assembly.setting());
        }
        return result;
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
        return ElderLexicon.WAND_UPGRADE_SERIALIZER.get();
    }

    private Optional<Assembly> findAssembly(CraftingContainer container) {
        ItemStack wand = ItemStack.EMPTY;
        Foci.Grip grip = null;
        Foci.Setting setting = null;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof WandItem) {
                if (!wand.isEmpty()) {
                    return Optional.empty();
                }
                wand = stack;
                continue;
            }
            Optional<Foci.Grip> asGrip = WandParts.gripOf(stack);
            if (asGrip.isPresent()) {
                if (grip != null) {
                    return Optional.empty();
                }
                grip = asGrip.get();
                continue;
            }
            Optional<Foci.Setting> asSetting = WandParts.settingOf(stack);
            if (asSetting.isPresent()) {
                if (setting != null) {
                    return Optional.empty();
                }
                setting = asSetting.get();
                continue;
            }
            return Optional.empty();
        }
        if (wand.isEmpty() || (grip == null && setting == null)) {
            return Optional.empty();
        }
        // Only a haste with no grip yet takes one: a wand with a grip keeps it until it breaks.
        if (grip != null && !HELD_WAND_OF.containsKey(wand.getItem())) {
            return Optional.empty();
        }
        // Nor does a wand with a gem take another here.
        if (setting != null && ((WandItem) wand.getItem()).hasSetting(wand)) {
            return Optional.empty();
        }
        return Optional.of(new Assembly(wand, grip, setting));
    }

    private record Assembly(ItemStack wand, Foci.Grip grip, Foci.Setting setting) {
    }
}
