package com.elderlexicon.mod.ligabis.world.golem.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a golem "skin" out of a block's own particle texture, tiled to cover the iron golem's UV layout
 * (128x128). A block's texture is a flat, repeating pattern designed for a cube, not a body, so the result
 * reads as "made of that block" rather than a clean reskin — expected, and better for plain materials
 * (dirt, stone, wool) than for ones with an obvious directional pattern (logs, bookshelves).
 */
public final class GolemTextures {

    private static final int GOLEM_WIDTH = 128;
    private static final int GOLEM_HEIGHT = 128;
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private GolemTextures() {
    }

    public static ResourceLocation forBlock(String blockId) {
        return CACHE.computeIfAbsent(blockId, GolemTextures::generate);
    }

    private static ResourceLocation generate(String blockId) {
        ResourceLocation blockKey = ResourceLocation.tryParse(blockId);
        Block block = blockKey == null ? null : ForgeRegistries.BLOCKS.getValue(blockKey);
        if (block == null) {
            block = Blocks.DIRT;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer()
                .getBlockModelShaper().getParticleIcon(block.defaultBlockState());
        NativeImage source = sprite.contents().getOriginalImage();
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        NativeImage skin = new NativeImage(GOLEM_WIDTH, GOLEM_HEIGHT, false);
        for (int y = 0; y < GOLEM_HEIGHT; y++) {
            for (int x = 0; x < GOLEM_WIDTH; x++) {
                skin.setPixelRGBA(x, y, source.getPixelRGBA(x % sourceWidth, y % sourceHeight));
            }
        }

        ResourceLocation location = new ResourceLocation("elderlexicon", "dynamic/golem_" + safeName(blockId));
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(skin));
        return location;
    }

    private static String safeName(String blockId) {
        return blockId.replace(':', '_').replace('/', '_');
    }
}
