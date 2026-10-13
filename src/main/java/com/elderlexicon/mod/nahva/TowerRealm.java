package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.spell.SpellTicks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The infinite tower of Nahvá (docs/habilidades-design.md), in the crater of Lig'dar.
 * <p>
 * Outside: a volcano, and in its crater a lake of lava, and out of the lake the tower, of a black stone that drinks the
 * light, with veins of fire climbing it, windows without number, some dark and some lit, spires leaning at angles no
 * builder would choose, stairs that wind around it and end in nothing, doors standing alone in the air, islands adrift
 * and planets far off: the spirits. Its top is never seen: the fog takes it long before.
 * <p>
 * Inside: rooms drawn anew for each climb ({@link TowerRoom}), each holding a trial and sealed until it is passed, and
 * between them broad stairs that turn and turn and seem never to end: each turn is the same as the last, and climbing
 * past one puts the climber back at its foot, a few times over, before the stairs let them out. Every room has a door
 * of rest, Phantur's offer: through it the spirit gives up and goes to its island. At the top is the rift.
 */
public final class TowerRealm {

    /** Where the first floor is. */
    private static final int GROUND = 64;
    /** Five turns of the stairs, of 8 each: a room below, and solid stone the stairs climb through. */
    private static final int FLOOR_HEIGHT = 40;
    /** How tall a room is; above it, up to the next floor, the tower is solid. */
    private static final int ROOM = 16;
    /** The turns of the stairs between two floors, and how much each climbs. */
    private static final int TURNS = 5;
    private static final int PITCH = 8;
    /**
     * The turn on which the climber is put back on the one below. Two turns before it and one after are plain stairs,
     * so whatever can be seen from either place is the same: neither the way in nor the way out is in sight.
     */
    private static final int LOOP_TURN = 3;
    /**
     * Floors: 0 the antechamber, 1 and 2 the rooms (taking turns, as many as the climb has), 3 the top with the rift;
     * and the roof over it.
     */
    private static final int FLOORS = 4;
    private static final int SUMMIT = 3;
    /** The walls of the rooms are at ±HALF; the inside is ±(HALF-1). */
    private static final int HALF = 16;
    private static final int INSIDE = HALF - 1;
    private static final int TOP = 636;
    /**
     * Which build of the outside stands: a block above the roof marks it, one step further along for each, so a tower
     * built before is built again (over itself) when the outside changes.
     */
    private static final int EXTERIOR = 3;
    /** How many times the stairs between two floors turn back on themselves before letting the climber out. */
    private static final int LOOPS = 4;
    /** How many times the stairs that go down turn back on themselves: more than up. */
    private static final int DOWN_LOOPS = 6;
    /** From which sixteenth of the last turn the floor above is open. */
    private static final int OPEN_SECTOR = 8;

    // The volcano.
    private static final int LAKE_Y = 58;
    private static final int LAKE_RADIUS = 50;
    private static final int RIM_RADIUS = 62;
    private static final int RIM_Y = 86;
    private static final int FOOT_RADIUS = 112;
    private static final int FOOT_Y = 26;
    /** Where the spirit stands on arrival: on the rim, at the head of the bridge, looking at the tower across the lava. */
    private static final int ARRIVAL_Z = RIM_RADIUS + 2;


    /** The climbers whose move by the stairs is on its way ({@link #shift}). */
    private static final Set<UUID> SHIFTING = new HashSet<>();

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState VEIN = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState STEP = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState HALF_STEP = Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState()
            .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    private static final BlockState STONE = Blocks.DEEPSLATE_BRICKS.defaultBlockState();

    private TowerRealm() {
    }

    // ------------------------------------------------------------------ building

    /**
     * Lays the tower out at {@code origin}: its outside once, its inside anew for every climb, with the rooms of the
     * climb drawn into {@code state}.
     */
    static Realms.Built build(ServerLevel level, BlockPos origin, CompoundTag state) {
        BlockPos marker = origin.offset(EXTERIOR - 1, TOP + 1, 0);
        if (!level.getBlockState(marker).is(Blocks.CRYING_OBSIDIAN)) {
            RandomSource random = RandomSource.create(origin.asLong());
            volcano(level, origin, random);
            tower(level, origin, random);
            bridge(level, origin);
            sky(level, origin, random);
            level.setBlock(marker, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
        }
        // What the last climb left inside goes: the spirits, and anything else.
        AABB inside = new AABB(origin.offset(-INSIDE, GROUND, -INSIDE), origin.offset(INSIDE + 1, floorY(FLOORS) + 1,
                INSIDE + 1));
        level.getEntities((Entity) null, inside, entity -> !(entity instanceof Player)).forEach(Entity::discard);
        for (int floor = 0; floor <= FLOORS; floor++) {
            slab(level, origin, floor);
        }
        antechamber(level, origin);
        restDoor(level, origin, 0);
        restDoor(level, origin, SUMMIT);
        state.putIntArray("rooms", draw());
        state.putInt("current", -1);
        stairs(level, origin, 0, false);
        layOut(level, origin, state, 0);
        layOut(level, origin, state, 1);
        Vec3 rift = rift(level, origin.offset(0, floorY(SUMMIT) + 5, 0));
        level.getEntities(EntityType.ITEM, inside, item -> true).forEach(Entity::discard);
        int arrivalY = rimHeight(ARRIVAL_Z) + 1;
        return new Realms.Built(new Vec3(origin.getX() + 0.5D, arrivalY, origin.getZ() + ARRIVAL_Z + 0.5D), rift);
    }

    private static int floorY(int floor) {
        return GROUND + floor * FLOOR_HEIGHT;
    }

    private static void set(ServerLevel level, BlockPos origin, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
        // A chest laid over spills what it held: what the last climb left in one goes with it, not onto the floor.
        if (level.getBlockEntity(pos) instanceof Container container) {
            container.clearContent();
        }
        level.setBlock(pos, state, 2);
    }

    private static BlockState get(ServerLevel level, BlockPos origin, int x, int y, int z) {
        return level.getBlockState(new BlockPos(origin.getX() + x, y, origin.getZ() + z));
    }

    /** The height of the volcano's surface at {@code r} from the tower: the crater wall, the rim, the long slope. */
    private static int rimHeight(double r) {
        if (r <= LAKE_RADIUS) {
            return LAKE_Y - 1;
        }
        if (r <= RIM_RADIUS) {
            return (int) Math.round(Mth.lerp((r - LAKE_RADIUS) / (RIM_RADIUS - LAKE_RADIUS), LAKE_Y, RIM_Y));
        }
        double t = (r - RIM_RADIUS) / (FOOT_RADIUS - RIM_RADIUS);
        return (int) Math.round(Mth.lerp(Math.pow(t, 0.8D), RIM_Y, FOOT_Y));
    }

    /** Lig'dar: a cone of dark rock with a lake of lava in its crater. */
    private static void volcano(ServerLevel level, BlockPos origin, RandomSource random) {
        BlockState basalt = Blocks.BASALT.defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();
        for (int x = -FOOT_RADIUS; x <= FOOT_RADIUS; x++) {
            for (int z = -FOOT_RADIUS; z <= FOOT_RADIUS; z++) {
                double r = Math.sqrt(x * x + z * z) + random.nextDouble() * 1.5D;
                if (r > FOOT_RADIUS) {
                    continue;
                }
                if (r <= LAKE_RADIUS) {
                    set(level, origin, x, LAKE_Y - 2, z, basalt);
                    set(level, origin, x, LAKE_Y - 1, z, basalt);
                    if (Math.max(Math.abs(x), Math.abs(z)) > halfWidth(LAKE_Y)) {
                        set(level, origin, x, LAKE_Y, z, lava);
                    }
                    continue;
                }
                int top = rimHeight(r);
                for (int y = top - 3; y <= top; y++) {
                    float roll = random.nextFloat();
                    BlockState rock = r < RIM_RADIUS + 6 && y == top && roll < 0.12F ? VEIN
                            : roll < 0.45F ? BLACKSTONE : basalt;
                    set(level, origin, x, y, z, rock);
                }
                // The crater wall holds the lake.
                if (r <= LAKE_RADIUS + 3) {
                    for (int y = LAKE_Y - 2; y < top - 3; y++) {
                        set(level, origin, x, y, z, basalt);
                    }
                }
            }
        }
    }

    /** How wide the tower is at {@code y}: broad at its foot, narrowing in steps as it climbs. */
    private static int halfWidth(int y) {
        return y < 120 ? 20 : y < 300 ? HALF : y < 460 ? 13 : 10;
    }

    /** The tower: black stone, veins of fire, windows, spires leaning off true, and the gate facing the rim. */
    private static void tower(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int y = floorY(FLOORS) + 1; y < TOP; y++) {
            int inner = halfWidth(y) - 1;
            for (int x = -inner; x <= inner; x++) {
                for (int z = -inner; z <= inner; z++) {
                    set(level, origin, x, y, z, AIR);
                }
            }
        }
        for (int y = LAKE_Y - 2; y <= TOP; y++) {
            int hw = halfWidth(y);
            boolean band = (y - GROUND) % FLOOR_HEIGHT == 0;
            for (int i = -hw; i <= hw; i++) {
                BlockState state = Math.abs(i) == hw || band ? BRICKS : BLACKSTONE;
                set(level, origin, i, y, -hw, state);
                set(level, origin, i, y, hw, state);
                set(level, origin, -hw, y, i, state);
                set(level, origin, hw, y, i, state);
            }
            // The foot is solid out to its broad walls; where the tower narrows, a ledge.
            if (hw > HALF) {
                for (int x = -hw; x <= hw; x++) {
                    for (int z = -hw; z <= hw; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) >= HALF) {
                            set(level, origin, x, y, z, BLACKSTONE);
                        }
                    }
                }
            }
            if (y > LAKE_Y && halfWidth(y - 1) > hw) {
                int below = halfWidth(y - 1);
                for (int x = -below; x <= below; x++) {
                    for (int z = -below; z <= below; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) > hw) {
                            set(level, origin, x, y - 1, z, BRICKS);
                        }
                    }
                }
            }
        }
        // The roof, lost in the fog.
        for (int x = -halfWidth(TOP); x <= halfWidth(TOP); x++) {
            for (int z = -halfWidth(TOP); z <= halfWidth(TOP); z++) {
                set(level, origin, x, TOP, z, BRICKS);
            }
        }
        veins(level, origin, random);
        windows(level, origin, random);
        spires(level, origin, random);
        outerStairs(level, origin, random);
        // The gate, through the broad foot, facing the rim.
        for (int x = -2; x <= 2; x++) {
            for (int y = GROUND + 1; y <= GROUND + 5; y++) {
                boolean arch = y == GROUND + 5 && Math.abs(x) == 2;
                for (int z = HALF; z <= halfWidth(y); z++) {
                    set(level, origin, x, y, z, arch ? BRICKS : AIR);
                }
            }
        }
    }

    /** Veins of fire wandering up the walls, like blood under the skin. */
    private static void veins(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int face = 0; face < 4; face++) {
            for (int v = 0; v < 7; v++) {
                double u = random.nextDouble() * 30.0D - 15.0D;
                double phase = random.nextDouble() * Math.PI * 2.0D;
                double wave = 6.0D + random.nextDouble() * 10.0D;
                int from = GROUND + random.nextInt(200);
                int to = Math.min(TOP - 1, from + 80 + random.nextInt(300));
                for (int y = from; y < to; y++) {
                    int hw = halfWidth(y);
                    int along = Mth.clamp((int) Math.round(u + Math.sin(y / wave + phase) * 3.0D), -hw + 1, hw - 1);
                    placeOnFace(level, origin, face, along, y, hw, VEIN);
                }
            }
        }
    }

    /** A block on one face of the tower: {@code along} across it, at its outer wall for {@code y}. */
    private static void placeOnFace(ServerLevel level, BlockPos origin, int face, int along, int y, int hw,
                                    BlockState state) {
        switch (face) {
            case 0 -> set(level, origin, along, y, -hw, state);
            case 1 -> set(level, origin, along, y, hw, state);
            case 2 -> set(level, origin, -hw, y, along, state);
            default -> set(level, origin, hw, y, along, state);
        }
    }

    /** Windows without number and without pattern: narrow slits and tall ones, most dark, some lit red. */
    private static void windows(ServerLevel level, BlockPos origin, RandomSource random) {
        BlockState dark = Blocks.TINTED_GLASS.defaultBlockState();
        BlockState lit = Blocks.RED_STAINED_GLASS.defaultBlockState();
        for (int n = 0; n < 900; n++) {
            int face = random.nextInt(4);
            int y = GROUND + 2 + random.nextInt(TOP - GROUND - 12);
            // Below the top floor, no window on the wall the puzzle's levers hang on.
            if (face == 0 && y < floorY(FLOORS)) {
                continue;
            }
            int hw = halfWidth(y);
            int width = 1 + random.nextInt(random.nextFloat() < 0.15F ? 4 : 2);
            int height = 2 + random.nextInt(random.nextFloat() < 0.2F ? 12 : 4);
            int along = -hw + 2 + random.nextInt(Math.max(1, 2 * hw - 3 - width));
            boolean light = random.nextFloat() < 0.3F;
            if (face == 1 && Math.abs(along) <= 4 && y < GROUND + 10) {
                continue; // the gate
            }
            for (int dy = 0; dy < height; dy++) {
                if (halfWidth(y + dy) != hw) {
                    break;
                }
                for (int du = 0; du < width; du++) {
                    placeOnFace(level, origin, face, along + du, y + dy, hw, light ? lit : dark);
                    if (light) {
                        // The light is behind the glass, inside; where the inside is stone, the window stays dark.
                        BlockPos behind = facePos(origin, face, along + du, y + dy, hw - 1);
                        if (level.getBlockState(behind).isAir()) {
                            level.setBlock(behind, Blocks.LIGHT.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    private static BlockPos facePos(BlockPos origin, int face, int along, int y, int depth) {
        return switch (face) {
            case 0 -> new BlockPos(origin.getX() + along, y, origin.getZ() - depth);
            case 1 -> new BlockPos(origin.getX() + along, y, origin.getZ() + depth);
            case 2 -> new BlockPos(origin.getX() - depth, y, origin.getZ() + along);
            default -> new BlockPos(origin.getX() + depth, y, origin.getZ() + along);
        };
    }

    /** Spires growing out of the tower and leaning away from it, at angles no builder would choose. */
    private static void spires(ServerLevel level, BlockPos origin, RandomSource random) {
        int[][] corners = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        int[] starts = {120, 300, 460};
        for (int start : starts) {
            for (int[] corner : corners) {
                if (random.nextFloat() < 0.35F) {
                    continue;
                }
                int hw = halfWidth(start);
                double lean = 0.08D + random.nextDouble() * 0.22D;
                int height = 50 + random.nextInt(110);
                int radius = 2 + random.nextInt(2);
                for (int dy = 0; dy < height && start + dy < TOP; dy++) {
                    double out = hw + dy * lean;
                    int r = dy > height - 12 ? Math.max(0, radius - (dy - (height - 12)) / 4) : radius;
                    int cx = (int) Math.round(corner[0] * out);
                    int cz = (int) Math.round(corner[1] * out);
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx * dx + dz * dz <= r * r + 1) {
                                boolean edge = dx * dx + dz * dz > (r - 1) * (r - 1);
                                set(level, origin, cx + dx, start + dy, cz + dz,
                                        edge && random.nextFloat() < 0.08F ? VEIN : BLACKSTONE);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Stairs leaving the walls and winding around the tower: some climb, some fall, some end in nothing. */
    private static void outerStairs(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int n = 0; n < 9; n++) {
            int y0 = 110 + random.nextInt(420);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int direction = random.nextBoolean() ? 1 : -1;
            int length = 40 + random.nextInt(140);
            double rising = random.nextFloat() < 0.7F ? 0.5D : -0.5D;
            double height = y0;
            for (int step = 0; step < length; step++) {
                int hw = halfWidth((int) height);
                double radius = hw * 1.42D + 2.0D;
                angle += direction * 1.0D / radius;
                height += rising * 0.5D;
                if (height < LAKE_Y + 10 || height > TOP - 4) {
                    break;
                }
                for (double r = radius; r < radius + 2.0D; r += 0.5D) {
                    int x = (int) Math.round(Math.cos(angle) * r);
                    int z = (int) Math.round(Math.sin(angle) * r);
                    int y = (int) Math.floor(height);
                    if (Math.max(Math.abs(x), Math.abs(z)) <= hw) {
                        continue;
                    }
                    set(level, origin, x, y, z, height - y >= 0.5D ? HALF_STEP.setValue(SlabBlock.TYPE, SlabType.TOP)
                            : HALF_STEP);
                }
            }
        }
    }

    /** From the rim down to the gate: a bridge that falls gently, half a block at a time, over the lava. */
    private static void bridge(ServerLevel level, BlockPos origin) {
        int fromZ = RIM_RADIUS + 2;
        int toZ = 21;
        int fromY = rimHeight(fromZ) + 1;
        int toY = GROUND + 1;
        BlockState rail = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
        for (int z = toZ; z <= fromZ; z++) {
            double t = (double) (z - toZ) / (fromZ - toZ);
            double surface = Math.round((toY + t * (fromY - toY)) * 2.0D) / 2.0D;
            int y = (int) Math.floor(surface);
            boolean halfway = surface - y >= 0.5D;
            for (int x = -2; x <= 2; x++) {
                for (int clear = y; clear <= y + 4; clear++) {
                    set(level, origin, x, clear, z, AIR);
                }
                set(level, origin, x, halfway ? y : y - 1, z, halfway ? HALF_STEP : STEP);
                if (halfway) {
                    set(level, origin, x, y - 1, z, BRICKS);
                }
            }
            set(level, origin, -3, y, z, rail);
            set(level, origin, 3, y, z, rail);
            set(level, origin, -3, y - 1, z, BRICKS);
            set(level, origin, 3, y - 1, z, BRICKS);
            if (z % 9 == 0) {
                for (int down = y - 2; down >= LAKE_Y - 2; down--) {
                    set(level, origin, -2, down, z, BRICKS);
                    set(level, origin, 2, down, z, BRICKS);
                }
            }
        }
    }

    /** All around, in the dark: islands adrift, doors standing alone, and planets far off, the spirits. */
    private static void sky(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int n = 0; n < 26; n++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 70.0D + random.nextDouble() * 110.0D;
            int x = (int) (Math.cos(angle) * distance);
            int z = (int) (Math.sin(angle) * distance);
            if (Math.abs(x) <= 8 && z > 0) {
                continue; // the bridge and the arrival
            }
            int y = 95 + random.nextInt(170);
            int r = 2 + random.nextInt(5);
            island(level, origin, x, y, z, r, random);
            if (random.nextFloat() < 0.4F) {
                door(level, origin, x, y + 1, z, random.nextBoolean());
            }
        }
        for (int n = 0; n < 8; n++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 50.0D + random.nextDouble() * 90.0D;
            door(level, origin, (int) (Math.cos(angle) * distance), 110 + random.nextInt(260),
                    (int) (Math.sin(angle) * distance), random.nextBoolean());
        }
        planet(level, origin, -175, 300, -120, 24, Blocks.NETHERRACK.defaultBlockState(), VEIN, random);
        planet(level, origin, 150, 360, -175, 11, Blocks.SMOOTH_BASALT.defaultBlockState(),
                Blocks.TUFF.defaultBlockState(), random);
        planet(level, origin, 165, 250, 120, 7, Blocks.CRYING_OBSIDIAN.defaultBlockState(), BLACKSTONE, random);
        planet(level, origin, -120, 420, 170, 15, Blocks.BLACKSTONE.defaultBlockState(), VEIN, random);
    }

    private static void island(ServerLevel level, BlockPos origin, int cx, int cy, int cz, int r, RandomSource random) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.3D) {
                    continue;
                }
                int depth = (int) Math.round((r - d) * 1.6D) + 1 + random.nextInt(2);
                for (int dy = 0; dy < depth; dy++) {
                    set(level, origin, cx + dx, cy - dy, cz + dz, dy == 0 && random.nextFloat() < 0.1F ? VEIN
                            : random.nextFloat() < 0.5F ? BLACKSTONE : Blocks.BASALT.defaultBlockState());
                }
            }
        }
    }

    /** A door with no wall around it, glowing from within, as if it opened somewhere else. */
    private static void door(ServerLevel level, BlockPos origin, int x, int y, int z, boolean alongX) {
        BlockState glow = Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
        for (int a = -2; a <= 2; a++) {
            for (int dy = 0; dy <= 6; dy++) {
                boolean frame = Math.abs(a) == 2 || dy == 0 || dy == 6;
                int px = alongX ? x + a : x;
                int pz = alongX ? z : z + a;
                set(level, origin, px, y + dy, pz, frame ? BRICKS : glow);
                if (!frame) {
                    int fx = alongX ? 0 : 1;
                    int fz = alongX ? 1 : 0;
                    set(level, origin, px + fx, y + dy, pz + fz, Blocks.LIGHT.defaultBlockState());
                    set(level, origin, px - fx, y + dy, pz - fz, Blocks.LIGHT.defaultBlockState());
                }
            }
        }
    }

    private static void planet(ServerLevel level, BlockPos origin, int cx, int cy, int cz, int r, BlockState body,
                               BlockState marks, RandomSource random) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d <= r && d > r - 2.0D) {
                        set(level, origin, cx + dx, cy + dy, cz + dz, random.nextFloat() < 0.18F ? marks : body);
                    }
                }
            }
        }
    }

    /** A floor: its stone, the room above it cleared and lit by lights no one sees, and solid stone up to the next. */
    private static void slab(ServerLevel level, BlockPos origin, int floor) {
        int y = floorY(floor);
        BlockState floorStone = floor == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : STONE;
        for (int x = -INSIDE; x <= INSIDE; x++) {
            for (int z = -INSIDE; z <= INSIDE; z++) {
                set(level, origin, x, y, z, floorStone);
                if (floor < FLOORS) {
                    for (int dy = 1; dy < FLOOR_HEIGHT; dy++) {
                        boolean light = dy == 9 && x % 6 == 0 && z % 6 == 0;
                        set(level, origin, x, y + dy, z, dy >= ROOM ? STONE
                                : light ? Blocks.LIGHT.defaultBlockState() : AIR);
                    }
                }
            }
        }
    }

    /** The first floor: what a mage needs is easy to find in Nahvá. */
    private static void antechamber(ServerLevel level, BlockPos origin) {
        chest(level, origin.offset(-3, floorY(0) + 1, 12), List.of(new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 32), new ItemStack(Items.SHIELD),
                new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
                new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)));
        chest(level, origin.offset(3, floorY(0) + 1, 12), List.of(new ItemStack(Items.BREAD, 16),
                new ItemStack(Items.COOKED_BEEF, 8), new ItemStack(Items.COBBLESTONE, 32),
                new ItemStack(Items.TORCH, 16), new ItemStack(Items.WATER_BUCKET)));
    }

    private static void chest(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.clearContent();
            for (int i = 0; i < items.size(); i++) {
                chest.setItem(i, items.get(i).copy());
            }
            chest.setChanged();
        }
    }

    /** Which corner the stairs leaving {@code floor} turn in: they alternate, so each room is crossed. */
    private static int corner(int floor) {
        return floor % 2 == 0 ? 11 : -11;
    }

    /** The way round floor {@code floor} is: 1 when its stairs leave from the corner (11, 11), -1 when from (-11, -11). */
    private static int side(int floor) {
        return corner(floor) > 0 ? 1 : -1;
    }

    /**
     * The door of rest, on every floor, by the way in: a frame of cherry wood against the wall, warm light through it,
     * grass and flowers at its foot. Phantur's offer, always in sight: going through it is giving up, and the spirit goes
     * to its island to rest.
     */
    private static void restDoor(ServerLevel level, BlockPos origin, int floor) {
        TowerRoom.Room room = new TowerRoom.Room(level, origin, floorY(floor), side(floor), new CompoundTag());
        BlockState frame = Blocks.STRIPPED_CHERRY_LOG.defaultBlockState();
        for (int z = -2; z <= 2; z++) {
            for (int dy = 1; dy <= 6; dy++) {
                boolean edge = Math.abs(z) == 2 || dy == 6;
                room.set(-14, dy, z, edge ? frame : AIR);
                room.set(-15, dy, z, edge ? frame : dy == 3 ? Blocks.LIGHT.defaultBlockState() : AIR);
                room.set(-16, dy, z, edge ? frame : Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            }
            if (Math.abs(z) <= 1) {
                room.set(-14, 0, z, Blocks.GRASS_BLOCK.defaultBlockState());
                room.set(-15, 0, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        room.set(-15, 1, -1, Blocks.LILY_OF_THE_VALLEY.defaultBlockState());
        room.set(-15, 1, 1, Blocks.AZURE_BLUET.defaultBlockState());
    }

    /** Whether the climber stands in the door of rest of {@code floor}. */
    private static boolean inRestDoor(ServerPlayer player, BlockPos origin, int floor) {
        int s = side(floor);
        int x = s * (player.getBlockX() - origin.getX());
        int z = s * (player.getBlockZ() - origin.getZ());
        double above = player.getY() - floorY(floor);
        return x <= -15 && Math.abs(z) <= 1 && above >= 0.5D && above < 5.0D;
    }

    /**
     * Which sixteenth of a turn a cell of the stairs is in, from the step at the way in: each sixteenth is half a block
     * higher than the last, so the climb never asks for a jump.
     */
    private static int sector(int dx, int dz, int s) {
        double start = Math.atan2(0.0D, -s) - Math.PI / 16.0D;
        double turn = Math.atan2(dz, dx) - start;
        turn = ((turn % (2.0D * Math.PI)) + 2.0D * Math.PI) % (2.0D * Math.PI);
        return Mth.clamp((int) (turn / (2.0D * Math.PI) * 16.0D), 0, 15);
    }

    /**
     * The stairs from {@code floor} up to the next, in a closed shaft: broad steps, two blocks wide, around a pillar,
     * rising half a block at a time, five turns of 8, coming out through an opening in the floor above. Every turn is
     * the same as the others, down to the lamps. Their way in is sealed while the floor's trial is not passed.
     */
    private static void stairs(ServerLevel level, BlockPos origin, int floor, boolean sealed) {
        int c = corner(floor);
        int s = c > 0 ? 1 : -1;
        int base = floorY(floor);
        for (int y = base + 1; y < base + FLOOR_HEIGHT; y++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    boolean lamp = ring == 4 && dx == 4 * s && dz == 0 && (y - base) % PITCH == 4;
                    set(level, origin, c + dx, y, c + dz, lamp ? Blocks.SHROOMLIGHT.defaultBlockState()
                            : ring == 4 || ring <= 1 ? STONE : AIR);
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) {
                    continue;
                }
                int h = sector(dx, dz, s);
                // Where the last turn comes up through the floor above, the floor opens: from early enough that the
                // climber walks out upright, the ceiling never at their head.
                if (h >= OPEN_SECTOR) {
                    set(level, origin, c + dx, base + FLOOR_HEIGHT, c + dz, AIR);
                }
                for (int turn = 0; turn < TURNS; turn++) {
                    double surface = base + 1 + turn * PITCH + h * 0.5D;
                    int y = (int) Math.floor(surface);
                    if (surface == y) {
                        set(level, origin, c + dx, y - 1, c + dz, STEP);
                    } else {
                        set(level, origin, c + dx, y, c + dz, HALF_STEP);
                    }
                }
            }
        }
        // The way in, three wide, facing the room.
        for (int y = base + 1; y <= base + 3; y++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, origin, c - 4 * s, y, c + dz, sealed ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : AIR);
            }
        }
    }

    private static Vec3 rift(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = -4; y <= 4; y++) {
            for (int z = -4; z <= 4; z++) {
                double distance = Math.sqrt(y * y + z * z);
                level.setBlock(pos.set(center.getX(), center.getY() + y, center.getZ() + z),
                        distance >= 2.5D && distance < 3.7D ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : AIR, 2);
            }
        }
        return Vec3.atCenterOf(center);
    }

    // ------------------------------------------------------------------ the rooms of a climb

    /** The room the next climb begins with, asked for by hand to try it ({@code /mago sala}); then forgotten. */
    private static TowerRoom first;

    /** Makes the next climb begin with the room {@code id}; false when there is no such room. */
    public static boolean beginWith(String id) {
        for (TowerRoom room : TowerRoom.values()) {
            if (room.id().equals(id)) {
                first = room;
                return true;
            }
        }
        return false;
    }

    /** The names of the rooms, for the command. */
    public static List<String> roomNames() {
        return java.util.Arrays.stream(TowerRoom.values()).map(TowerRoom::id).toList();
    }

    /** The rooms of this climb, drawn at its start: an even number of them, so the last is on the second floor. */
    private static int[] draw() {
        List<TowerRoom> rooms = new ArrayList<>(List.of(TowerRoom.values()));
        Collections.shuffle(rooms);
        // One room too many: one of the provisional ones goes, never one of the true rooms.
        if (rooms.size() % 2 == 1) {
            rooms.stream().filter(TowerRoom::provisional).findFirst().ifPresentOrElse(rooms::remove,
                    () -> rooms.remove(rooms.size() - 1));
        }
        // For testing (/mago sala): the room asked for comes first.
        if (first != null) {
            rooms.remove(first);
            rooms.add(0, first);
            if (rooms.size() % 2 == 1) {
                rooms.remove(rooms.size() - 1);
            }
            first = null;
        }
        // The stairs that go down lead to the next room: they cannot be the last.
        int down = rooms.indexOf(TowerRoom.DESCENT);
        if (down == rooms.size() - 1) {
            Collections.swap(rooms, down, new Random().nextInt(down));
        }
        return rooms.stream().mapToInt(Enum::ordinal).toArray();
    }

    private static int count(CompoundTag state) {
        return state.getIntArray("rooms").length;
    }

    private static TowerRoom room(CompoundTag state, int index) {
        return TowerRoom.values()[state.getIntArray("rooms")[index]];
    }

    /**
     * Which floor room {@code index} is laid out on. There are only two floors of rooms, the first and the second, and
     * the rooms take them by turns: the stairs out of the second lead, unseen, back into the stairs out of the
     * antechamber, which are the same, and up to the first again, laid out anew. So the tower has as many rooms as the
     * climb needs.
     */
    private static int host(int index) {
        return index % 2 == 0 ? 1 : 2;
    }

    private static CompoundTag roomState(CompoundTag state, int index) {
        String key = "room" + index;
        if (!state.contains(key)) {
            state.put(key, new CompoundTag());
        }
        return state.getCompound(key);
    }

    private static TowerRoom.Room at(ServerLevel level, BlockPos origin, CompoundTag state, int index) {
        int floor = host(index);
        return new TowerRoom.Room(level, origin, floorY(floor), side(floor), roomState(state, index));
    }

    private static boolean sealed(CompoundTag state, int index) {
        return index >= 0 && index < count(state) && room(state, index).sealed()
                && !roomState(state, index).getBoolean("open");
    }

    /** Lays room {@code index} out on its floor, over whatever room was there: never in the climber's sight. */
    private static void layOut(ServerLevel level, BlockPos origin, CompoundTag state, int index) {
        int floor = host(index);
        AABB room = new AABB(origin.offset(-INSIDE, floorY(floor), -INSIDE),
                origin.offset(INSIDE + 1, floorY(floor) + ROOM, INSIDE + 1));
        level.getEntities((Entity) null, room, entity -> !(entity instanceof Player)).forEach(Entity::discard);
        slab(level, origin, floor);
        if (room(state, index) == TowerRoom.MEMORY) {
            roomState(state, index).put("source", state.getCompound("memory").copy());
        }
        room(state, index).build(at(level, origin, state, index));
        restDoor(level, origin, floor);
        // A room come into from above, down its own stairs, leaves their way open until the climber is in.
        stairs(level, origin, floor, sealed(state, index) && !roomState(state, index).getBoolean("behind"));
        stairs(level, origin, floor - 1, sealed(state, index - 1));
        level.getEntities(EntityType.ITEM, room, item -> true).forEach(Entity::discard);
        state.putBoolean("ready" + index, true);
    }

    // ------------------------------------------------------------------ the climb

    private static BlockPos origin(CompoundTag dream) {
        return new BlockPos(dream.getInt("ox"), 0, dream.getInt("oz"));
    }

    private static CompoundTag state(CompoundTag dream) {
        if (!dream.contains("state")) {
            dream.put("state", new CompoundTag());
        }
        return dream.getCompound("state");
    }

    /** The floor the climber is on, or -1 outside the tower's rooms. */
    private static int floorOf(ServerPlayer player, BlockPos origin) {
        double x = player.getX() - origin.getX();
        double z = player.getZ() - origin.getZ();
        if (Math.abs(x) > INSIDE + 1 || Math.abs(z) > INSIDE + 1 || player.getY() < GROUND) {
            return -1;
        }
        return (int) Math.floor((player.getY() - GROUND - 0.5D) / FLOOR_HEIGHT);
    }

    /**
     * Moves the climber by {@code dy}, relatively: their own client keeps its pace, its sway and where it looks.
     * <p>
     * Not at once: this runs in the climber's own tick, and at its end the server puts them back where the tick began
     * until their client answers the move; in between, the stairs would see them where they were and move them again,
     * the client would go twice as far as the server thinks, and the difference would be taken for a fall. So the move
     * is made at the end of the next server tick, outside any player's, and until then the stairs leave them be.
     */
    private static void shift(ServerPlayer player, double dy) {
        UUID id = player.getUUID();
        SHIFTING.add(id);
        SpellTicks.schedule(player.server, 1, () -> {
            SHIFTING.remove(id);
            if (player.isRemoved() || !player.isAlive() || !Dreams.isDreaming(player)) {
                return;
            }
            player.connection.teleport(player.getX(), player.getY() + dy, player.getZ(), player.getYRot(),
                    player.getXRot(), RelativeMovement.ALL);
            player.fallDistance = 0.0F;
        });
    }

    /**
     * Every tick: the door of rest, and the stairs that seem never to end. Coming onto the fourth turn, the climber is
     * put back on the third, at the very same step of it, which looks the same; a few times over, then the stairs let
     * them out. Out of the second floor of rooms, while rooms remain, they are let out into the stairs out of the
     * antechamber instead, and so up to the first floor of rooms, laid out anew.
     */
    static void everyTick(ServerPlayer player, CompoundTag dream) {
        BlockPos origin = origin(dream);
        CompoundTag state = state(dream);
        int floor = floorOf(player, origin);
        if (floor < 0) {
            return;
        }
        if (inRestDoor(player, origin, floor)) {
            Dreams.giveUp(player);
            return;
        }
        int current = state.getInt("current");
        if (current >= 0 && host(current) == floor && sealed(state, current)) {
            room(state, current).tick(player, at(player.serverLevel(), origin, state, current));
        }
        if (floor >= SUMMIT || SHIFTING.contains(player.getUUID())) {
            return;
        }
        if (current >= 0 && room(state, current).descends() && floor == host(current) - 1) {
            descend(player, origin, state, floor, current);
            return;
        }
        int c = corner(floor);
        double dx = player.getX() - (origin.getX() + c + 0.5D);
        double dz = player.getZ() - (origin.getZ() + c + 0.5D);
        double above = player.getY() - floorY(floor);
        double from = 1 + LOOP_TURN * PITCH - 0.1D;
        if (Math.abs(dx) >= 3.7D || Math.abs(dz) >= 3.7D || above < from || above >= from + PITCH) {
            return;
        }
        ServerLevel level = player.serverLevel();
        String key = "loops" + floor;
        int loops = state.getInt(key);
        int next = current + 1;
        boolean more = next < count(state);
        // The room these stairs lead to, if it is new and just above, is laid out while the climber is in them.
        if (more && floor >= 1 && host(next) == floor + 1 && !state.getBoolean("ready" + next)) {
            layOut(level, origin, state, next);
        }
        if (loops < LOOPS) {
            state.putInt(key, loops + 1);
            shift(player, -PITCH);
            Phantur.maybe(player, "stairs", 0.3F);
        } else if (more && floor == 2) {
            layOut(level, origin, state, next);
            state.putInt("loops0", LOOPS);
            state.putInt(key, 0);
            shift(player, -2 * FLOOR_HEIGHT);
        }
    }

    /**
     * Going down the stairs out of a room with no way up ({@link TowerRoom#DESCENT}): the stairs the climber came by.
     * The next room is laid out at their foot, come into by its own stairs; on the way, the stairs turn back on
     * themselves, upwards now, more times than they did going up. Out of the first floor of rooms they lead, unseen,
     * into the stairs out of the second, which are the same, and so down to the second floor, laid out anew.
     */
    private static void descend(ServerPlayer player, BlockPos origin, CompoundTag state, int floor, int current) {
        int c = corner(floor);
        double dx = player.getX() - (origin.getX() + c + 0.5D);
        double dz = player.getZ() - (origin.getZ() + c + 0.5D);
        if (Math.abs(dx) >= 3.7D || Math.abs(dz) >= 3.7D) {
            return;
        }
        int next = current + 1;
        if (!state.getBoolean("ready" + next)) {
            roomState(state, next).putBoolean("behind", true);
            layOut(player.serverLevel(), origin, state, next);
            Phantur.speak(player, "descent");
        }
        double above = player.getY() - floorY(floor);
        double from = 1 + PITCH - 0.1D;
        if (above < from || above >= from + PITCH) {
            return;
        }
        int loops = state.getInt("down");
        if (loops < DOWN_LOOPS) {
            state.putInt("down", loops + 1);
            shift(player, PITCH);
        } else if (floor == 0) {
            shift(player, 2 * FLOOR_HEIGHT);
        }
    }

    /** Each second: arriving somewhere, the trial of the room the climber is in, and the door of rest. */
    static void everySecond(ServerPlayer player, CompoundTag dream) {
        BlockPos origin = origin(dream);
        CompoundTag state = state(dream);
        ServerLevel level = player.serverLevel();
        int floor = floorOf(player, origin);
        if (floor < 0) {
            return;
        }
        double above = player.getY() - floorY(floor);
        int current = state.getInt("current");
        int next = current + 1;
        if (above < 4.0D) {
            if (floor == 0 && !state.getBoolean("arrived")) {
                state.putBoolean("arrived", true);
                tell(player, Component.translatable("tower.elderlexicon.floor.0"));
            } else if (floor == SUMMIT && next >= count(state) && !state.getBoolean("summit")) {
                state.putBoolean("summit", true);
                tell(player, Component.translatable("tower.elderlexicon.floor.summit"));
                Phantur.speak(player, "summit");
            } else if (floor >= 1 && floor < SUMMIT && next < count(state) && host(next) == floor
                    && state.getBoolean("ready" + next)) {
                arrive(player, origin, state, next);
                current = next;
            }
        }
        if (current >= 0 && host(current) == floor) {
            closeBehind(player, origin, state, current);
            if (sealed(state, current) && room(state, current).passed(player, at(level, origin, state, current))) {
                open(player, origin, state, current);
            }
        }
        // The door of rest: a breath of spring through it; and the first time near it in a room, he speaks of it.
        BlockPos door = new TowerRoom.Room(level, origin, floorY(floor), side(floor), new CompoundTag()).pos(-15, 3, 0);
        level.sendParticles(player, ParticleTypes.CHERRY_LEAVES, false, door.getX() + 0.5D, door.getY(),
                door.getZ() + 0.5D, 3, 0.4D, 1.2D, 1.0D, 0.0D);
        String near = "door" + floor + "." + current;
        if (player.distanceToSqr(Vec3.atCenterOf(door)) < 30.0D && !state.getBoolean(near)) {
            state.putBoolean(near, true);
            Phantur.maybe(player, "door", 0.7F);
        }
    }

    /** A room come into down its own stairs: once the climber is out of them, their way in closes behind. */
    private static void closeBehind(ServerPlayer player, BlockPos origin, CompoundTag state, int index) {
        CompoundTag room = roomState(state, index);
        int floor = host(index);
        int c = corner(floor);
        if (!room.getBoolean("behind") || room.getBoolean("closed")
                || Math.abs(player.getX() - (origin.getX() + c + 0.5D)) < 5.5D
                && Math.abs(player.getZ() - (origin.getZ() + c + 0.5D)) < 5.5D) {
            return;
        }
        room.putBoolean("closed", true);
        if (!sealed(state, index)) {
            return;
        }
        int s = side(floor);
        for (int y = floorY(floor) + 1; y <= floorY(floor) + 3; y++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(player.serverLevel(), origin, c - 4 * s, y, c + dz, Blocks.CRYING_OBSIDIAN.defaultBlockState());
            }
        }
        player.serverLevel().playSound(null, origin.offset(c - 4 * s, floorY(floor) + 2, c),
                SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
    }

    /** The climber comes into room {@code index}: it begins. */
    private static void arrive(ServerPlayer player, BlockPos origin, CompoundTag state, int index) {
        state.putInt("current", index);
        state.putInt("loops" + host(index), 0);
        TowerRoom room = room(state, index);
        room.arrive(player, at(player.serverLevel(), origin, state, index));
        // The count the tower gives: after the stairs that go down, it has gone back.
        int shown = state.getInt("shown") + 1;
        if (index > 0 && room(state, index - 1).descends()) {
            shown = Math.max(1, shown - 3);
        }
        state.putInt("shown", shown);
        tell(player, Component.translatable("tower.elderlexicon.room", shown,
                Component.translatable("tower.elderlexicon.room." + room.id())));
        Phantur.maybe(player, "room", 0.5F);
    }

    /** A spell said in the tower, of {@code runes}: the room the climber is in may be listening. */
    public static void onSpellCast(ServerPlayer player, List<String> runes) {
        if (!Dreams.isDreaming(player)) {
            return;
        }
        CompoundTag dream = Dreams.dream(player);
        if (!Realms.TOWER.equals(dream.getString("realm"))) {
            return;
        }
        BlockPos origin = origin(dream);
        CompoundTag state = state(dream);
        int current = state.getInt("current");
        if (current >= 0 && host(current) == floorOf(player, origin) && sealed(state, current)
                && room(state, current).heard(player, at(player.serverLevel(), origin, state, current), runes)) {
            open(player, origin, state, current);
        }
    }

    /** The climber strikes a block: the room they are in may be waiting for it. */
    public static void onStrike(ServerPlayer player, BlockPos pos) {
        if (!Dreams.isDreaming(player)) {
            return;
        }
        CompoundTag dream = Dreams.dream(player);
        if (!Realms.TOWER.equals(dream.getString("realm"))) {
            return;
        }
        BlockPos origin = origin(dream);
        CompoundTag state = state(dream);
        int current = state.getInt("current");
        if (current >= 0 && host(current) == floorOf(player, origin) && sealed(state, current)
                && room(state, current).struck(player, at(player.serverLevel(), origin, state, current), pos)) {
            open(player, origin, state, current);
        }
    }

    /** The seal of room {@code index}'s stairs breaks. */
    private static void open(ServerPlayer player, BlockPos origin, CompoundTag state, int index) {
        roomState(state, index).putBoolean("open", true);
        int floor = host(index);
        int c = corner(floor);
        int s = c > 0 ? 1 : -1;
        for (int y = floorY(floor) + 1; y <= floorY(floor) + 3; y++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(player.serverLevel(), origin, c - 4 * s, y, c + dz, AIR);
            }
        }
        BlockPos door = origin.offset(c - 4 * s, floorY(floor) + 2, c);
        player.serverLevel().playSound(null, door, SoundEvents.RESPAWN_ANCHOR_DEPLETE.get(), SoundSource.BLOCKS,
                1.0F, 0.8F);
        player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, door.getX() + 0.5D, door.getY(),
                door.getZ() + 0.5D, 30, 0.4D, 1.0D, 1.2D, 0.05D);
        tell(player, Component.translatable("tower.elderlexicon.opened"));
    }
}
