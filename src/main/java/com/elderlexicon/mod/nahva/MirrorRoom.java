package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The mirror ({@link TowerRoom#MIRROR}): a hall cut in two along its diagonal by a mirror no one can see. On the far
 * side, with the stairs out, stands the climber's reflection, doing what they did a moment before. The way through the
 * mirror opens only while the reflection stands on the plate on its side: the climber must stand on the image of it on
 * theirs, and run for the way through before their reflection steps off. The moment shrinks; when it is gone, the
 * reflection catches up with them, and it hurts, and the moment is long again.
 * <p>
 * The mirror is the same seen from either side of the room: it takes a place (x, z) from the middle of the tower to
 * (-z, -x), whichever way the room is turned.
 */
final class MirrorRoom {

    /** The plate (on the reflection's side) and its image (on the climber's), in the room's own turn. */
    private static final int PLATE_X = 12;
    private static final int PLATE_Z = 6;
    /** The way through: the middle of the mirror, this many blocks along it each way. */
    private static final int THROUGH = 2;
    private static final int HEIGHT = 15;
    /** How late the reflection is, in ticks: at first, at least, and how much less each second. */
    private static final double LATE_START = 100.0D;
    private static final double LATE_LEAST = 40.0D;
    private static final double LATE_SHRINK = 0.7D;
    private static final float CAUGHT = 4.0F;
    private static final int[][] PILLARS = {{-12, 6}, {4, -12}, {-8, -3}};

    /** Where each climber has been, a moment ago and now: what their reflection does, later. */
    private static final Map<UUID, Deque<Frame>> FRAMES = new HashMap<>();

    private record Frame(long time, double x, double y, double z, float yaw, float pitch, Pose pose) {
    }

    private MirrorRoom() {
    }

    /** The image of a place in the room (x, z from the middle), across the mirror. */
    private static int[] mirrored(int x, int z) {
        return new int[]{-z, -x};
    }

    static void build(TowerRoom.Room room) {
        BlockState line = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState far = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                if (x + z == 0) {
                    room.set(x, 0, z, line);
                    for (int dy = 1; dy <= HEIGHT; dy++) {
                        room.set(x, dy, z, Blocks.BARRIER.defaultBlockState());
                    }
                } else if (x + z > 0 && !(Math.abs(x - 11) <= 4 && Math.abs(z - 11) <= 4)) {
                    room.set(x, 0, z, far);
                }
            }
        }
        // The plate, and its image on this side; pillars, and theirs.
        BlockState plate = Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState();
        int[] image = mirrored(PLATE_X, PLATE_Z);
        room.set(PLATE_X, 0, PLATE_Z, Blocks.SHROOMLIGHT.defaultBlockState());
        room.set(PLATE_X, 1, PLATE_Z, plate);
        room.set(image[0], 0, image[1], Blocks.SHROOMLIGHT.defaultBlockState());
        room.set(image[0], 1, image[1], plate);
        BlockState stone = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int[] pillar : PILLARS) {
            int[] other = mirrored(pillar[0], pillar[1]);
            for (int dy = 1; dy <= HEIGHT; dy++) {
                room.set(pillar[0], dy, pillar[1], stone);
                room.set(other[0], dy, other[1], stone);
            }
        }
    }

    static void arrive(ServerPlayer player, TowerRoom.Room room) {
        FRAMES.remove(player.getUUID());
        room.state().putDouble("late", LATE_START);
        Phantur.speak(player, "mirror");
    }

    /** Whether the climber has passed through to the other side. */
    static boolean passed(TowerRoom.Room room) {
        return room.state().getBoolean("through");
    }

    /** Where the climber is, from the middle of the tower, along the line across the mirror (above zero: through). */
    private static double across(Entity entity, TowerRoom.Room room) {
        double x = entity.getX() - room.origin().getX() - 0.5D;
        double z = entity.getZ() - room.origin().getZ() - 0.5D;
        return room.s() * (x + z);
    }

    static void tick(ServerPlayer player, TowerRoom.Room room) {
        if (room.state().getBoolean("through")) {
            return;
        }
        ServerLevel level = room.level();
        if (across(player, room) > 1.0D) {
            through(player, room);
            return;
        }
        Reflection reflection = reflection(player, room);
        if (reflection == null) {
            return;
        }
        long now = level.getGameTime();
        Deque<Frame> frames = FRAMES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        frames.addLast(new Frame(now, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.getPose()));
        double late = room.state().getDouble("late") - LATE_SHRINK / 20.0D;
        room.state().putDouble("late", late);
        Frame shown = frames.peekFirst();
        while (frames.size() > 1 && frames.peekFirst().time() <= now - late) {
            shown = frames.pollFirst();
        }
        show(reflection, shown, room);
        copyWhatIsWorn(player, reflection);
        boolean onPlate = onPlate(reflection, room);
        if (onPlate != room.state().getBoolean("openWay")) {
            room.state().putBoolean("openWay", onPlate);
            way(player, room, onPlate);
        }
        if (now % 10 == 0) {
            shimmer(room);
        }
        if (late <= LATE_LEAST) {
            // It has caught up.
            player.hurt(player.damageSources().magic(), CAUGHT);
            player.displayClientMessage(Component.translatable("tower.elderlexicon.mirror.caught")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
            level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 0.5F);
            room.state().putDouble("late", LATE_START);
            Phantur.maybe(player, "mirror_caught", 0.6F);
        }
    }

    /** The reflection of the climber in this room, raised again if it is gone (it is never kept with the world). */
    private static Reflection reflection(ServerPlayer player, TowerRoom.Room room) {
        if (room.state().hasUUID("reflection")
                && room.level().getEntity(room.state().getUUID("reflection")) instanceof Reflection found
                && found.isAlive()) {
            return found;
        }
        Reflection reflection = ElderLexicon.REFLECTION.get().create(room.level());
        if (reflection == null) {
            return null;
        }
        reflection.setOwner(player.getUUID());
        Frame now = new Frame(0L, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.getPose());
        show(reflection, now, room);
        room.level().addFreshEntity(reflection);
        room.state().putUUID("reflection", reflection.getUUID());
        return reflection;
    }

    /** Puts the reflection where the climber was, across the mirror, looking the way they looked, mirrored. */
    private static void show(Reflection reflection, Frame frame, TowerRoom.Room room) {
        double ox = room.origin().getX() + 0.5D;
        double oz = room.origin().getZ() + 0.5D;
        double x = frame.x() - ox;
        double z = frame.z() - oz;
        float yaw = frame.yaw() * Mth.DEG_TO_RAD;
        double lookX = -Mth.sin(yaw);
        double lookZ = Mth.cos(yaw);
        float mirroredYaw = (float) (Mth.atan2(lookZ, -lookX) * Mth.RAD_TO_DEG);
        reflection.moveTo(ox - z, frame.y(), oz - x, mirroredYaw, frame.pitch());
        reflection.setYHeadRot(mirroredYaw);
        reflection.setYBodyRot(mirroredYaw);
        reflection.setPose(frame.pose());
        reflection.setShiftKeyDown(frame.pose() == Pose.CROUCHING);
    }

    /** It wears what the climber wears; and as in a mirror, its right hand is their left. */
    private static void copyWhatIsWorn(ServerPlayer player, Reflection reflection) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!net.minecraft.world.item.ItemStack.matches(reflection.getItemBySlot(slot), player.getItemBySlot(slot))) {
                reflection.setItemSlot(slot, player.getItemBySlot(slot).copy());
            }
        }
        reflection.setLeftHanded(player.getMainArm() == HumanoidArm.RIGHT);
    }

    private static boolean onPlate(Reflection reflection, TowerRoom.Room room) {
        BlockPos plate = room.pos(PLATE_X, 0, PLATE_Z);
        return Math.abs(reflection.getX() - (plate.getX() + 0.5D)) < 1.0D
                && Math.abs(reflection.getZ() - (plate.getZ() + 0.5D)) < 1.0D
                && reflection.getY() - room.y() < 2.5D;
    }

    /** The way through the middle of the mirror opens, or closes (never on the climber). */
    private static void way(ServerPlayer player, TowerRoom.Room room, boolean open) {
        AABB body = player.getBoundingBox();
        for (int k = -THROUGH; k <= THROUGH; k++) {
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos pos = room.pos(k, dy, -k);
                if (!open && body.intersects(new AABB(pos))) {
                    continue;
                }
                room.level().setBlock(pos, open ? Blocks.AIR.defaultBlockState()
                        : Blocks.BARRIER.defaultBlockState(), 2);
            }
        }
        BlockPos middle = room.pos(0, 2, 0);
        room.level().playSound(null, middle, open ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_HIT,
                SoundSource.BLOCKS, 1.5F, open ? 1.2F : 0.6F);
        if (open) {
            room.level().sendParticles(ParticleTypes.END_ROD, middle.getX() + 0.5D, middle.getY(), middle.getZ() + 0.5D,
                    20, 1.2D, 1.0D, 1.2D, 0.01D);
        }
    }

    /** Now and then the mirror catches the light, so it can be told where it stands. */
    private static void shimmer(TowerRoom.Room room) {
        int k = room.level().getRandom().nextInt(31) - 15;
        BlockPos at = room.pos(k, 1 + room.level().getRandom().nextInt(HEIGHT), -k);
        room.level().sendParticles(ParticleTypes.WAX_OFF, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, 1,
                0.3D, 0.3D, 0.3D, 0.0D);
    }

    /** Through: the mirror breaks, and the reflection with it. */
    private static void through(ServerPlayer player, TowerRoom.Room room) {
        room.state().putBoolean("through", true);
        FRAMES.remove(player.getUUID());
        if (room.state().hasUUID("reflection")
                && room.level().getEntity(room.state().getUUID("reflection")) instanceof Reflection reflection) {
            room.level().sendParticles(ParticleTypes.REVERSE_PORTAL, reflection.getX(), reflection.getY() + 1.0D,
                    reflection.getZ(), 40, 0.3D, 0.8D, 0.3D, 0.05D);
            reflection.discard();
        }
        for (int x = -15; x <= 15; x++) {
            for (int dy = 1; dy <= HEIGHT; dy++) {
                BlockPos pos = room.pos(x, dy, -x);
                if (room.level().getBlockState(pos).is(Blocks.BARRIER)) {
                    room.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        room.level().playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, 0.7F);
    }

    static void forget(UUID player) {
        FRAMES.remove(player);
    }
}
