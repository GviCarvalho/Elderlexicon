package com.elderlexicon.mod.tower;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Whose tower it is, and what it is built of (docs/mago-design.md, 3 and 5e): the Sorcerer's dark (deepslate and
 * blackstone, soul light, a black roof), the Wizard's light (stone bricks, spruce, lanterns, a blue roof).
 */
public enum TowerKind implements StringRepresentable {
    SORCERER("sorcerer", Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE,
            Blocks.DARK_OAK_PLANKS, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES,
            Blocks.BLACK_STAINED_GLASS_PANE, Blocks.SOUL_LANTERN, Blocks.DARK_OAK_DOOR, Blocks.BLACK_CARPET,
            Blocks.BLACK_BED),
    WIZARD("wizard", Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS,
            Blocks.CHISELED_STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.BLUE_TERRACOTTA, Blocks.GLASS_PANE,
            Blocks.LANTERN, Blocks.SPRUCE_DOOR, Blocks.BLUE_CARPET, Blocks.BLUE_BED);

    public static final Codec<TowerKind> CODEC = StringRepresentable.fromEnum(TowerKind::values);

    private final String name;
    final Block wall;
    /** Now and then in the wall, so it is not one flat stone. */
    final Block weathered;
    final Block foundation;
    final Block floor;
    /** The central column and the trims. */
    final Block column;
    /** The steps of the spiral stair. */
    final Block step;
    final Block roof;
    final Block window;
    final Block light;
    final Block door;
    final Block carpet;
    final Block bed;

    TowerKind(String name, Block wall, Block weathered, Block foundation, Block floor, Block column, Block step,
              Block roof, Block window, Block light, Block door, Block carpet, Block bed) {
        this.name = name;
        this.wall = wall;
        this.weathered = weathered;
        this.foundation = foundation;
        this.floor = floor;
        this.column = column;
        this.step = step;
        this.roof = roof;
        this.window = window;
        this.light = light;
        this.door = door;
        this.carpet = carpet;
        this.bed = bed;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The loot of its chests. */
    String lootTable() {
        return "chests/" + name + "_tower";
    }
}
