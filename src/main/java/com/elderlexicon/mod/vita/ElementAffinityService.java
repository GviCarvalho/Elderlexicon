package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves which entities align with Vita elements using datapack tags.
 */
public final class ElementAffinityService {

    private static final Map<VitaElement, TagKey<EntityType<?>>> ENTITY_TAGS = createEntityTagMap();
    private static final Map<VitaElement, TagKey<Block>> BLOCK_TAGS = createBlockTagMap();
    private static final Map<VitaElement, TagKey<Item>> ITEM_TAGS = createItemTagMap();
    private static final Set<TagKey<Block>> KNOWN_BLOCK_TAGS = new HashSet<>(BLOCK_TAGS.values());
    private static final Set<TagKey<Item>> KNOWN_ITEM_TAGS = new HashSet<>(ITEM_TAGS.values());

    private ElementAffinityService() {
    }

    /**
     * Returns {@code true} when the given entity matches the affinity for the provided element.
     * Balanced (or null) elements always succeed, preserving the "affects all" behavior.
     */
    public static boolean matches(VitaElement element, LivingEntity entity) {
        if (entity == null || element == null || element.isBalanced()) {
            return true;
        }
        TagKey<EntityType<?>> tag = ENTITY_TAGS.get(element);
        if (tag == null) {
            return false;
        }
        return entity.getType().is(tag);
    }

    public static boolean matchesBlock(VitaElement element, BlockState state) {
        if (element == null || element.isBalanced() || state == null) {
            return false;
        }
        TagKey<Block> tag = BLOCK_TAGS.get(element);
        return tag != null && state.is(tag);
    }

    public static boolean matchesItem(VitaElement element, ItemStack stack) {
        if (element == null || element.isBalanced() || stack == null) {
            return false;
        }
        TagKey<Item> tag = ITEM_TAGS.get(element);
        return tag != null && stack.is(tag);
    }

    public static boolean hasBlockAffinity(BlockState state) {
        if (state == null) {
            return false;
        }
        return matchesAnyBlockTag(state, KNOWN_BLOCK_TAGS);
    }

    public static boolean hasItemAffinity(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        return matchesAnyItemTag(stack, KNOWN_ITEM_TAGS);
    }

    private static boolean matchesAnyBlockTag(BlockState state, Collection<TagKey<Block>> tags) {
        for (TagKey<Block> tag : tags) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyItemTag(ItemStack stack, Collection<TagKey<Item>> tags) {
        for (TagKey<Item> tag : tags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static Map<VitaElement, TagKey<EntityType<?>>> createEntityTagMap() {
        EnumMap<VitaElement, TagKey<EntityType<?>>> map = new EnumMap<>(VitaElement.class);
        map.put(VitaElement.IGNI, entityTag("igni_affinity"));
        map.put(VitaElement.AQUA, entityTag("aqua_affinity"));
        map.put(VitaElement.AURA, entityTag("aura_affinity"));
        map.put(VitaElement.FIRMO, entityTag("firmo_affinity"));
        return map;
    }

    private static Map<VitaElement, TagKey<Block>> createBlockTagMap() {
        EnumMap<VitaElement, TagKey<Block>> map = new EnumMap<>(VitaElement.class);
        map.put(VitaElement.IGNI, blockTag("igni_source_blocks"));
        map.put(VitaElement.AQUA, blockTag("aqua_source_blocks"));
        map.put(VitaElement.AURA, blockTag("aura_source_blocks"));
        map.put(VitaElement.FIRMO, blockTag("firmo_source_blocks"));
        return map;
    }

    private static Map<VitaElement, TagKey<Item>> createItemTagMap() {
        EnumMap<VitaElement, TagKey<Item>> map = new EnumMap<>(VitaElement.class);
        map.put(VitaElement.IGNI, itemTag("igni_source_items"));
        map.put(VitaElement.AQUA, itemTag("aqua_source_items"));
        map.put(VitaElement.AURA, itemTag("aura_source_items"));
        map.put(VitaElement.FIRMO, itemTag("firmo_source_items"));
        return map;
    }

    private static TagKey<EntityType<?>> entityTag(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, Objects.requireNonNull(path));
        return TagKey.create(Registries.ENTITY_TYPE, id);
    }

    private static TagKey<Block> blockTag(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, Objects.requireNonNull(path));
        return TagKey.create(Registries.BLOCK, id);
    }

    private static TagKey<Item> itemTag(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, Objects.requireNonNull(path));
        return TagKey.create(Registries.ITEM, id);
    }
}
