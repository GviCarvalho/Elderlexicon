package com.elderlexicon.mod.tower;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.caster.RuneCaster;
import com.elderlexicon.mod.galdraria.Galdraria;
import com.elderlexicon.mod.library.RuneGuides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A caster's tower, drawn block by block (docs/mago-design.md, 3 and 5e): round, of stone, three to five storeys joined
 * by a stair winding round a central column. Below, the door and the storeroom; then the library (bookshelves, a
 * lectern with a guide on it, the galdraria table, the enchanting table); with five storeys a study, with four or more
 * an alchemist's room; the bedroom at the top storey; above all, the observatory, open on every side under a conical
 * roof. Whose it is says what it is built of ({@link TowerKind}), and who lives in it: the caster, at home in the
 * library, and now and then a Sorcerer's band at its door.
 * <p>
 * Everything is placed in world coordinates and only inside the chunk being made, so each chunk the tower crosses draws
 * its share; what is decided by chance is decided by the position, so the chunks agree.
 */
public class TowerPiece extends StructurePiece {

    /** The radius of the wall, and the height of a storey (its floor and four of air). */
    static final int RADIUS = 5;
    static final int STOREY = 5;
    /** How deep the foundation goes, at most, to reach the ground. */
    private static final int FOUNDATION = 12;
    /** How far from its tower its caster wanders at peace. */
    private static final int HOME_RADIUS = 16;
    /** The cells around the column the stair winds through, one step a cell, each a block higher. */
    private static final int[][] SPIRAL = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    private final TowerKind kind;
    private final int cx;
    private final int y0;
    private final int cz;
    private final int storeys;

    public TowerPiece(TowerKind kind, BlockPos center, RandomSource random) {
        this(kind, center, 3 + random.nextInt(3));
    }

    public TowerPiece(TowerKind kind, BlockPos center, int storeys) {
        super(Towers.TOWER_PIECE.get(), 0, box(center, storeys));
        this.kind = kind;
        this.cx = center.getX();
        this.y0 = center.getY();
        this.cz = center.getZ();
        this.storeys = storeys;
    }

    public TowerPiece(CompoundTag tag) {
        super(Towers.TOWER_PIECE.get(), tag);
        this.kind = TowerKind.valueOf(tag.getString("Kind"));
        this.cx = tag.getInt("X");
        this.y0 = tag.getInt("Y");
        this.cz = tag.getInt("Z");
        this.storeys = tag.getInt("Storeys");
    }

    private static BoundingBox box(BlockPos center, int storeys) {
        int top = center.getY() + storeys * STOREY + 4 + RADIUS + 3;
        return new BoundingBox(center.getX() - RADIUS - 3, center.getY() - FOUNDATION, center.getZ() - RADIUS - 3,
                center.getX() + RADIUS + 3, top, center.getZ() + RADIUS + 3);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Kind", kind.name());
        tag.putInt("X", cx);
        tag.putInt("Y", y0);
        tag.putInt("Z", cz);
        tag.putInt("Storeys", storeys);
    }

    /** The height of the observatory's floor. */
    private int top() {
        return y0 + storeys * STOREY;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        Draw draw = new Draw(level, chunk);
        foundation(draw);
        for (int storey = 0; storey < storeys; storey++) {
            shell(draw, y0 + storey * STOREY, storey == 0);
        }
        observatory(draw);
        roof(draw);
        stair(draw);
        furnish(draw);
        dwellers(level, chunk);
    }

    /** Fills the ground under the tower, down to where it is solid. */
    private void foundation(Draw draw) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (distance(dx, dz) > RADIUS + 0.5D) {
                    continue;
                }
                for (int y = y0 - 1; y >= y0 - FOUNDATION; y--) {
                    BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                    if (!draw.inside(pos)) {
                        break;
                    }
                    BlockState there = draw.level.getBlockState(pos);
                    if (!there.isAir() && !there.canBeReplaced() && there.getFluidState().isEmpty()) {
                        break;
                    }
                    draw.set(pos, kind.foundation.defaultBlockState());
                }
            }
        }
    }

    /** A storey: its floor, its wall, the air inside, its windows (and, at the bottom, the door). */
    private void shell(Draw draw, int floor, boolean ground) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                double d = distance(dx, dz);
                if (d > RADIUS + 0.5D) {
                    continue;
                }
                boolean wall = d > RADIUS - 0.5D;
                draw.set(cx + dx, floor, cz + dz, wall ? kind.column.defaultBlockState()
                        : kind.floor.defaultBlockState());
                for (int y = floor + 1; y < floor + STOREY; y++) {
                    draw.set(cx + dx, y, cz + dz, wall ? wallAt(cx + dx, y, cz + dz) : Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int[] side : new int[][]{{0, -RADIUS}, {RADIUS, 0}, {0, RADIUS}, {-RADIUS, 0}}) {
            boolean door = ground && side[1] == RADIUS;
            if (door) {
                doorway(draw, floor);
                continue;
            }
            for (int y = floor + 2; y <= floor + 3; y++) {
                draw.set(cx + side[0], y, cz + side[1], pane(side[0], side[1]));
            }
        }
    }

    /** The door, to the south, and a way cleared to it. */
    private void doorway(Draw draw, int floor) {
        BlockState door = kind.door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        draw.set(cx, floor + 1, cz + RADIUS, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        draw.set(cx, floor + 2, cz + RADIUS, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int out = RADIUS + 1; out <= RADIUS + 2; out++) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockPos ground = new BlockPos(cx + dx, floor, cz + out);
                if (draw.inside(ground) && draw.level.getBlockState(ground).canBeReplaced()) {
                    draw.set(ground, kind.foundation.defaultBlockState());
                }
                for (int y = floor + 1; y <= floor + 3; y++) {
                    draw.set(cx + dx, y, cz + out, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** The observatory: a floor, a low wall, and windows all round between eight pillars. */
    private void observatory(Draw draw) {
        int floor = top();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                double d = distance(dx, dz);
                if (d > RADIUS + 0.5D) {
                    continue;
                }
                boolean wall = d > RADIUS - 0.5D;
                draw.set(cx + dx, floor, cz + dz, wall ? kind.column.defaultBlockState()
                        : kind.floor.defaultBlockState());
                boolean pillar = wall && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz));
                for (int y = floor + 1; y <= floor + 3; y++) {
                    BlockState state = !wall ? Blocks.AIR.defaultBlockState()
                            : y == floor + 1 || pillar ? wallAt(cx + dx, y, cz + dz) : pane(dx, dz);
                    draw.set(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /** The conical roof, a ring a layer narrower each block up, with a rod at the point. */
    private void roof(Draw draw) {
        int base = top() + 4;
        for (int layer = 0; layer <= RADIUS + 1; layer++) {
            int radius = RADIUS + 1 - layer;
            for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                    double d = distance(dx, dz);
                    if (d > radius + 0.5D) {
                        continue;
                    }
                    // The first layer reaches over the wall's top, as eaves.
                    double inner = layer == 0 ? 1.5D : 0.5D;
                    draw.set(cx + dx, base + layer, cz + dz, d > radius - inner || radius == 0
                            ? kind.roof.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        draw.set(cx, base + RADIUS + 2, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
    }

    /** The column, and the stair winding round it from the door to the observatory, with room above each step. */
    private void stair(Draw draw) {
        for (int y = y0; y <= top() + 3; y++) {
            draw.set(cx, y, cz, kind.column.defaultBlockState());
        }
        int steps = storeys * STOREY;
        for (int k = 1; k <= steps; k++) {
            int[] cell = SPIRAL[(k - 1) % SPIRAL.length];
            int y = y0 + k;
            draw.set(cx + cell[0], y, cz + cell[1], kind.step.defaultBlockState());
            for (int above = 1; above <= 3; above++) {
                draw.set(cx + cell[0], y + above, cz + cell[1], Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** What each storey holds, and its light. */
    private void furnish(Draw draw) {
        for (int storey = 0; storey < storeys; storey++) {
            int floor = y0 + storey * STOREY;
            if (storey == 0) {
                storeroom(draw, floor);
            } else if (storey == 1) {
                library(draw, floor);
            } else if (storey == storeys - 1) {
                bedroom(draw, floor, storeys == 3);
            } else if (storey == 2) {
                alchemy(draw, floor);
            } else {
                study(draw, floor);
            }
            hangLights(draw, floor + STOREY - 1);
        }
        int floor = top();
        draw.set(cx + 2, floor + 1, cz - 2, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        draw.set(cx - 2, floor + 1, cz + 2, kind.light.defaultBlockState());
    }

    private void storeroom(Draw draw, int floor) {
        List<int[]> edge = edge(true);
        int placed = 0;
        for (int[] cell : edge) {
            BlockPos pos = new BlockPos(cx + cell[0], floor + 1, cz + cell[1]);
            switch (placed++ % 6) {
                case 0, 3 -> chest(draw, pos, cell);
                case 1, 4 -> draw.set(pos, Blocks.BARREL.defaultBlockState());
                case 2 -> draw.set(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
                default -> {
                }
            }
            if (placed >= 8) {
                break;
            }
        }
    }

    private void library(Draw draw, int floor) {
        for (int[] cell : edge(false)) {
            for (int y = floor + 1; y <= floor + 3; y++) {
                draw.set(cx + cell[0], y, cz + cell[1], Blocks.BOOKSHELF.defaultBlockState());
            }
        }
        lectern(draw, new BlockPos(cx, floor + 1, cz - 3));
        draw.set(cx - 3, floor + 1, cz, Galdraria.TABLE.get().defaultBlockState());
        draw.set(cx + 3, floor + 1, cz, Blocks.ENCHANTING_TABLE.defaultBlockState());
    }

    private void study(Draw draw, int floor) {
        for (int[] cell : edge(false)) {
            draw.set(cx + cell[0], floor + 1, cz + cell[1], Blocks.BOOKSHELF.defaultBlockState());
        }
        lectern(draw, new BlockPos(cx, floor + 1, cz + 3));
    }

    private void alchemy(Draw draw, int floor) {
        draw.set(cx - 3, floor + 1, cz, Blocks.BREWING_STAND.defaultBlockState());
        draw.set(cx + 3, floor + 1, cz, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));
        int placed = 0;
        for (int[] cell : edge(false)) {
            draw.set(cx + cell[0], floor + 1, cz + cell[1], placed++ % 3 == 0 ? Blocks.BARREL.defaultBlockState()
                    : Blocks.BOOKSHELF.defaultBlockState());
        }
    }

    private void bedroom(Draw draw, int floor, boolean brewing) {
        BlockState bed = kind.bed.defaultBlockState().setValue(BedBlock.FACING, Direction.WEST);
        draw.set(cx - 3, floor + 1, cz, bed.setValue(BedBlock.PART, BedPart.FOOT));
        draw.set(cx - 4, floor + 1, cz, bed.setValue(BedBlock.PART, BedPart.HEAD));
        chest(draw, new BlockPos(cx + 3, floor + 1, cz - 2), new int[]{3, -2});
        if (brewing) {
            draw.set(cx + 3, floor + 1, cz + 2, Blocks.BREWING_STAND.defaultBlockState());
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = distance(dx, dz);
                if (d > 1.5D && d <= 3.0D && Math.abs(dz) >= 2) {
                    draw.set(cx + dx, floor + 1, cz + dz, kind.carpet.defaultBlockState());
                }
            }
        }
    }

    /** Lanterns hung from the ceiling of a storey. */
    private void hangLights(Draw draw, int y) {
        BlockState lantern = kind.light.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int[] at : new int[][]{{2, 2}, {-2, -2}}) {
            draw.set(cx + at[0], y, cz + at[1], lantern);
        }
    }

    private void chest(Draw draw, BlockPos pos, int[] cell) {
        Direction facing = Math.abs(cell[0]) > Math.abs(cell[1]) ? (cell[0] > 0 ? Direction.WEST : Direction.EAST)
                : (cell[1] > 0 ? Direction.NORTH : Direction.SOUTH);
        if (!draw.set(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing))) {
            return;
        }
        if (draw.level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(new ResourceLocation(ElderLexicon.MODID, kind.lootTable()), Mth.getSeed(pos));
        }
    }

    /** A lectern with a guide open on it. */
    private void lectern(Draw draw, BlockPos pos) {
        if (!draw.set(pos, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH)
                .setValue(LecternBlock.HAS_BOOK, true))) {
            return;
        }
        if (draw.level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            List<String> runes = new ArrayList<>();
            for (RuneGuides.Family family : RuneGuides.Family.values()) {
                runes.addAll(RuneGuides.runesOf(family));
            }
            if (!runes.isEmpty()) {
                String rune = runes.get(Math.floorMod(Mth.getSeed(pos), runes.size()));
                RuneGuides.runeGuide(rune).ifPresent(lectern::setBook);
            }
        }
    }

    // ------------------------------------------------------------------ who lives there

    /** The caster at home in its library; at a Sorcerer's tower, now and then its band at the door. */
    private void dwellers(WorldGenLevel level, BoundingBox chunk) {
        BlockPos home = new BlockPos(cx, y0 + STOREY + 1, cz + 3);
        if (!chunk.isInside(home)) {
            return;
        }
        EntityType<? extends Mob> who = kind == TowerKind.SORCERER ? ElderLexicon.SORCERER.get()
                : ElderLexicon.WIZARD.get();
        Mob caster = spawn(level, who, home);
        if (caster instanceof RuneCaster runeCaster) {
            runeCaster.casterMind().home(new BlockPos(cx, y0 + 1, cz), HOME_RADIUS);
        }
        if (caster instanceof com.elderlexicon.mod.wizard.Wizard wizard) {
            wizard.dwellIn(com.elderlexicon.mod.wizard.Dwelling.TOWER);
        }
        if (kind != TowerKind.SORCERER) {
            return;
        }
        RandomSource chance = RandomSource.create(Mth.getSeed(home));
        int band = chance.nextFloat() < 0.5F ? 1 + chance.nextInt(2) : 0;
        for (int i = 0; i < band; i++) {
            int x = cx + (i == 0 ? -2 : 2);
            int z = cz + RADIUS + 3;
            if (!chunk.isInside(new BlockPos(x, y0, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            spawn(level, chance.nextBoolean() ? EntityType.VINDICATOR : EntityType.PILLAGER, new BlockPos(x, y, z));
        }
    }

    private static Mob spawn(WorldGenLevel level, EntityType<? extends Mob> type, BlockPos pos) {
        Mob mob = type.create(level.getLevel());
        if (mob == null) {
            return null;
        }
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
        mob.setPersistenceRequired();
        level.addFreshEntityWithPassengers(mob);
        return mob;
    }

    // ------------------------------------------------------------------ the shape

    private static double distance(int dx, int dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The wall's stone at a place: now and then a weathered one, the same in every chunk. */
    private BlockState wallAt(int x, int y, int z) {
        return Math.floorMod(Mth.getSeed(x, y, z), 100) < 15 ? kind.weathered.defaultBlockState()
                : kind.wall.defaultBlockState();
    }

    /** A window pane in the wall at {@code (dx, dz)}, joined to the wall on both sides. */
    private BlockState pane(int dx, int dz) {
        BlockState pane = kind.window.defaultBlockState();
        pane = pane.setValue(CrossCollisionBlock.NORTH, inWall(dx, dz - 1))
                .setValue(CrossCollisionBlock.SOUTH, inWall(dx, dz + 1))
                .setValue(CrossCollisionBlock.EAST, inWall(dx + 1, dz))
                .setValue(CrossCollisionBlock.WEST, inWall(dx - 1, dz));
        return pane;
    }

    private static boolean inWall(int dx, int dz) {
        double d = distance(dx, dz);
        return d > RADIUS - 0.5D && d <= RADIUS + 0.5D;
    }

    /**
     * The cells along the inside of the wall, round from the north, but those before the windows and the door (and,
     * {@code byTheDoor} false, those on either side of it as well).
     */
    private static List<int[]> edge(boolean byTheDoor) {
        List<int[]> cells = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                double d = distance(dx, dz);
                if (d <= RADIUS - 1.5D || d > RADIUS - 0.5D) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(dx, -dz));
                double fromCardinal = Math.abs(((angle % 90.0D) + 90.0D) % 90.0D);
                fromCardinal = Math.min(fromCardinal, 90.0D - fromCardinal);
                if (fromCardinal < 20.0D || !byTheDoor && dz > 0 && Math.abs(dx) <= 1) {
                    continue;
                }
                cells.add(new int[]{dx, dz});
            }
        }
        cells.sort(Comparator.comparingDouble(cell -> (Math.toDegrees(Math.atan2(cell[0], -cell[1])) + 360.0D)
                % 360.0D));
        return cells;
    }

    /** Places blocks only inside the chunk being made. */
    private static final class Draw {
        final WorldGenLevel level;
        final BoundingBox chunk;

        Draw(WorldGenLevel level, BoundingBox chunk) {
            this.level = level;
            this.chunk = chunk;
        }

        boolean inside(BlockPos pos) {
            return chunk.isInside(pos);
        }

        void set(int x, int y, int z, BlockState state) {
            set(new BlockPos(x, y, z), state);
        }

        boolean set(BlockPos pos, BlockState state) {
            if (!chunk.isInside(pos)) {
                return false;
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            return true;
        }
    }
}
