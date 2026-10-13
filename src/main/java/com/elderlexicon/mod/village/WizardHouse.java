package com.elderlexicon.mod.village;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.galdraria.Galdraria;
import com.elderlexicon.mod.library.RuneGuides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Locale;

/**
 * The Wizard's house in a village (docs/mago-design.md, 3 and 5f), drawn by code in each village's own materials: one
 * room under a pitched roof, its door to the street, a shelf of books along the back wall with a lectern and the
 * galdraria table before it, a bed, a brewing stand, a chest. The village's jigsaw takes only saved structures, so the
 * house is drawn into a world and saved ({@link #template}); a test keeps the saved ones as the code draws them.
 * <p>
 * In the house's own coordinates the door faces north (toward -z): the street joins it at the jigsaw before the door.
 */
public final class WizardHouse {

    /** The house's box: seven wide, nine tall (the roof's ridge), ten deep (the eaves over the door and the back). */
    public static final int WIDTH = 7;
    public static final int HEIGHT = 9;
    public static final int DEPTH = 10;
    /** Where the door is, along the front, and where the Wizard stands when the house is made. */
    public static final int DOOR_X = 3;
    public static final BlockPos DWELLER = new BlockPos(3, 1, 4);

    /** The villages it stands in, each with its own materials. */
    public enum Style {
        PLAINS(Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_STAIRS,
                Blocks.OAK_DOOR),
        DESERT(Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
                Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.JUNGLE_DOOR),
        SAVANNA(Blocks.COBBLESTONE, Blocks.ACACIA_PLANKS, Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG,
                Blocks.ACACIA_STAIRS, Blocks.ACACIA_DOOR),
        SNOWY(Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG,
                Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_DOOR),
        TAIGA(Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS,
                Blocks.SPRUCE_DOOR);

        final Block foundation;
        final Block floor;
        final Block wall;
        final Block corner;
        final Block roof;
        final Block door;

        Style(Block foundation, Block floor, Block wall, Block corner, Block roof, Block door) {
            this.foundation = foundation;
            this.floor = floor;
            this.wall = wall;
            this.corner = corner;
            this.roof = roof;
            this.door = door;
        }

        /** The village's name, as the game names its pools ({@code minecraft:village/plains/houses}). */
        public String village() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Where its house is saved, and the pool it joins. */
        public ResourceLocation template() {
            return new ResourceLocation(ElderLexicon.MODID, "village/" + village() + "/wizard_house");
        }

        public ResourceLocation housesPool() {
            return new ResourceLocation("minecraft", "village/" + village() + "/houses");
        }
    }

    private WizardHouse() {
    }

    /**
     * Draws the house of {@code style} into {@code level} with its least corner at {@code origin}: what is outside the
     * house is structure void (the village's ground is kept there).
     */
    public static void build(ServerLevel level, BlockPos origin, Style style) {
        Draw draw = new Draw(level, origin);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < DEPTH; z++) {
                    draw.set(x, y, z, Blocks.STRUCTURE_VOID.defaultBlockState());
                }
            }
        }
        // The floor and the walls, with logs at the corners.
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 1; z < DEPTH - 1; z++) {
                boolean edge = x == 0 || x == WIDTH - 1 || z == 1 || z == DEPTH - 2;
                boolean corner = (x == 0 || x == WIDTH - 1) && (z == 1 || z == DEPTH - 2);
                draw.set(x, 0, z, (edge ? style.foundation : style.floor).defaultBlockState());
                for (int y = 1; y <= 3; y++) {
                    BlockState state = !edge ? Blocks.AIR.defaultBlockState()
                            : corner ? pillar(style.corner) : style.wall.defaultBlockState();
                    draw.set(x, y, z, state);
                }
            }
        }
        // The roof: a slope from each side up to the ridge, over the eaves before and behind; the gables closed.
        for (int layer = 0; layer <= 3; layer++) {
            int y = 4 + layer;
            for (int z = 0; z < DEPTH; z++) {
                int left = layer;
                int right = WIDTH - 1 - layer;
                if (left == right) {
                    draw.set(left, y, z, style.wall.defaultBlockState());
                    continue;
                }
                draw.set(left, y, z, style.roof.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
                draw.set(right, y, z, style.roof.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
                if (z == 0 || z == DEPTH - 1) {
                    continue; // the eaves: beneath them is the village's
                }
                for (int x = left + 1; x < right; x++) {
                    boolean gable = z == 1 || z == DEPTH - 2;
                    draw.set(x, y, z, gable ? style.wall.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // The door, and the way to it: the jigsaw the street joins, before it.
        BlockState door = style.door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        draw.set(DOOR_X, 1, 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        draw.set(DOOR_X, 2, 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        draw.set(DOOR_X, 1, 0, Blocks.AIR.defaultBlockState());
        draw.set(DOOR_X, 2, 0, Blocks.AIR.defaultBlockState());
        entrance(draw);
        // Windows on the sides and at the back.
        draw.set(0, 2, 4, pane(true));
        draw.set(WIDTH - 1, 2, 4, pane(true));
        draw.set(1, 2, 1, pane(false));
        draw.set(5, 2, 1, pane(false));
        furnish(draw);
    }

    /** The jigsaw before the door: the street's building entrance. */
    private static void entrance(Draw draw) {
        BlockPos at = draw.at(DOOR_X, 0, 0);
        draw.level.setBlock(at, Blocks.JIGSAW.defaultBlockState().setValue(JigsawBlock.ORIENTATION,
                FrontAndTop.NORTH_UP), Block.UPDATE_CLIENTS);
        if (draw.level.getBlockEntity(at) instanceof JigsawBlockEntity jigsaw) {
            jigsaw.setName(new ResourceLocation("minecraft", "building_entrance"));
            jigsaw.setTarget(new ResourceLocation("minecraft", "building_entrance"));
            jigsaw.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, new ResourceLocation("minecraft", "empty")));
            jigsaw.setFinalState("minecraft:structure_void");
            jigsaw.setJoint(JigsawBlockEntity.JointType.ALIGNED);
        }
    }

    private static void furnish(Draw draw) {
        // The shelf of books along the back wall, a lantern at each end.
        for (int x = 1; x <= 5; x++) {
            draw.set(x, 1, 7, Blocks.BOOKSHELF.defaultBlockState());
            draw.set(x, 2, 7, Blocks.BOOKSHELF.defaultBlockState());
        }
        draw.set(1, 3, 7, Blocks.LANTERN.defaultBlockState());
        draw.set(5, 3, 7, Blocks.LANTERN.defaultBlockState());
        // Before it, the lectern with a guide open and the galdraria table.
        BlockPos lectern = draw.at(2, 1, 6);
        draw.level.setBlock(lectern, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH)
                .setValue(LecternBlock.HAS_BOOK, true), Block.UPDATE_CLIENTS);
        if (draw.level.getBlockEntity(lectern) instanceof LecternBlockEntity stand) {
            RuneGuides.runeGuide("vocant").ifPresent(stand::setBook);
        }
        draw.set(4, 1, 6, Galdraria.TABLE.get().defaultBlockState());
        // The bed by the left wall, the brewing stand and the chest by the right.
        BlockState bed = Blocks.PURPLE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        draw.set(1, 1, 4, bed.setValue(BedBlock.PART, BedPart.FOOT));
        draw.set(1, 1, 3, bed.setValue(BedBlock.PART, BedPart.HEAD));
        draw.set(5, 1, 4, Blocks.BREWING_STAND.defaultBlockState());
        BlockPos chest = draw.at(5, 1, 2);
        draw.level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST),
                Block.UPDATE_CLIENTS);
        if (draw.level.getBlockEntity(chest) instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(new ResourceLocation(ElderLexicon.MODID, "chests/wizard_house"), 0L);
        }
        for (int x = 2; x <= 4; x++) {
            for (int z = 3; z <= 5; z++) {
                draw.set(x, 1, z, Blocks.PURPLE_CARPET.defaultBlockState());
            }
        }
    }

    private static BlockState pillar(Block block) {
        BlockState state = block.defaultBlockState();
        return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
                : state;
    }

    /** A glass pane in a wall running along x ({@code alongX}) or along z. */
    private static BlockState pane(boolean alongZ) {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        return alongZ ? pane.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true)
                : pane.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);
    }

    private record Draw(ServerLevel level, BlockPos origin) {

        BlockPos at(int x, int y, int z) {
            return origin.offset(x, y, z);
        }

        void set(int x, int y, int z, BlockState state) {
            level.setBlock(at(x, y, z), state, Block.UPDATE_CLIENTS);
        }
    }
}
