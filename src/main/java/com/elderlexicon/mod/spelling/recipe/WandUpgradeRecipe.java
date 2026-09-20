package com.elderlexicon.mod.spelling.recipe;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spelling.item.ModularWandItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WandUpgradeRecipe extends CustomRecipe {

    private static final Map<Item, Item> RESULT_MAP = Map.of(
            ExampleMod.IMPROVISED_WAND.get(), ExampleMod.WAND.get(),
            ExampleMod.IMPROVISED_WAND_BONE.get(), ExampleMod.WAND_BONE.get(),
            ExampleMod.IMPROVISED_WAND_BAMBOO.get(), ExampleMod.WAND_BAMBOO.get(),
            ExampleMod.IMPROVISED_WAND_BLAZE.get(), ExampleMod.WAND_BLAZE.get()
    );

    private static final List<WoodMapping> WOOD_MAPPINGS = List.of(
            new WoodMapping(ItemTags.OAK_LOGS, "oak"),
            new WoodMapping(ItemTags.BIRCH_LOGS, "birch"),
            new WoodMapping(ItemTags.MANGROVE_LOGS, "mangrove"),
            new WoodMapping(ItemTags.ACACIA_LOGS, "acacia"),
            new WoodMapping(ItemTags.DARK_OAK_LOGS, "dark_oak"),
            new WoodMapping(ItemTags.CHERRY_LOGS, "cherry"),
            new WoodMapping(ItemTags.SPRUCE_LOGS, "spruce"),
            new WoodMapping(ItemTags.CRIMSON_STEMS, "crimson"),
            new WoodMapping(ItemTags.WARPED_STEMS, "warped")
    );

    public WandUpgradeRecipe(ResourceLocation id, net.minecraft.world.item.crafting.CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return findUpgrade(container).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return createResult(container);
    }

    private ItemStack createResult(CraftingContainer container) {
        Optional<Result> upgrade = findUpgrade(container);
        if (upgrade.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Result result = upgrade.get();
        ItemStack stack = new ItemStack(result.output());
        if (stack.getItem() instanceof ModularWandItem wandItem) {
            wandItem.applyMaterial(stack, result.materialId());
        }
        return stack;
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
        return ExampleMod.WAND_UPGRADE_SERIALIZER.get();
    }

    private Optional<Result> findUpgrade(CraftingContainer container) {
        ItemStack wand = ItemStack.EMPTY;
        ItemStack log = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (RESULT_MAP.containsKey(stack.getItem())) {
                if (!wand.isEmpty()) {
                    return Optional.empty();
                }
                wand = stack;
                continue;
            }
            if (isLog(stack)) {
                if (!log.isEmpty()) {
                    return Optional.empty();
                }
                log = stack;
                continue;
            }
            return Optional.empty();
        }
        if (wand.isEmpty() || log.isEmpty()) {
            return Optional.empty();
        }
        Item resultItem = RESULT_MAP.get(wand.getItem());
        if (resultItem == null) {
            return Optional.empty();
        }
        String materialId = woodMaterial(log);
        if (materialId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Result(resultItem, materialId));
    }

    private static boolean isLog(ItemStack stack) {
        return !woodMaterial(stack).isEmpty();
    }

    private static String woodMaterial(ItemStack stack) {
        for (WoodMapping mapping : WOOD_MAPPINGS) {
            if (stack.is(mapping.tag())) {
                return mapping.materialId();
            }
        }
        return "";
    }

    private record WoodMapping(TagKey<Item> tag, String materialId) {
    }

    private record Result(Item output, String materialId) {
    }
}
