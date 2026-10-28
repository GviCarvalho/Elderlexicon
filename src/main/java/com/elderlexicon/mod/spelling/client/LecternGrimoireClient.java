package com.elderlexicon.mod.spelling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Opens a grimoire lying on a lectern, to be read and its pages turned, not written in. */
@OnlyIn(Dist.CLIENT)
public final class LecternGrimoireClient {

    private LecternGrimoireClient() {
    }

    public static void open(BlockPos lectern, int page, ItemStack book) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        mc.setScreen(new GrimoireEditScreen(mc.player, book, lectern, page));
    }
}
