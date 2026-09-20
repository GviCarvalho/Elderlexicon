package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class VitaTags {

    private VitaTags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> HEAT_SOURCES = block("heat_sources");

        private Blocks() {
        }

        private static TagKey<Block> block(String name) {
            return TagKey.create(Registries.BLOCK, new ResourceLocation(ExampleMod.MODID, name));
        }
    }

    public static final class Items {
        public static final TagKey<Item> FIRMO_RECOVERY_FOODS = item("firmo_recovery_foods");

        private Items() {
        }

        private static TagKey<Item> item(String name) {
            return TagKey.create(Registries.ITEM, new ResourceLocation(ExampleMod.MODID, name));
        }
    }
}
