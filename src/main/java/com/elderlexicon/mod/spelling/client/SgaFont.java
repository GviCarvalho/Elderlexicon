package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * The font the Old Tongue is written in: the game's Standard Galactic letters ({@code minecraft:alt}) and the digits'
 * own glyphs ({@code assets/elderlexicon/font/sga.json}, drawn after the SGA Number Characters font, OFL).
 */
public final class SgaFont {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ElderLexicon.MODID, "sga");
    public static final Style STYLE = Style.EMPTY.withFont(ID);

    private SgaFont() {
    }
}
