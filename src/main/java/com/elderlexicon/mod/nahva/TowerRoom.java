package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The rooms of the tower, drawn anew for each climb (docs/habilidades-design.md). Each is laid out on a floor of the
 * tower when the climber is about to reach it, out of their sight; each may seal the stairs out of it until its trial is
 * passed. The first four are plain, provisional ones, to be replaced; then the descent, the memory, the mirror and the
 * speech.
 * <p>
 * A room is laid out as if the stairs came up into it at the corner (-11, -11) and left it from (11, 11); {@link Room}
 * turns that around on the floors where it is the other way.
 */
enum TowerRoom {

    /** The spirits of those who came before rise as the climber comes in; the seal breaks when all have fallen. */
    COMBAT("combat", true, true) {
        @Override
        void arrive(ServerPlayer player, Room room) {
            ListTag spawned = new ListTag();
            int[][] at = {{-4, 0}, {4, 0}, {0, -4}, {0, 4}, {-5, 5}, {5, -5}, {2, 2}};
            for (int i = 0; i < at.length; i++) {
                EntityType<? extends Mob> type = i < 4 ? EntityType.ZOMBIE : EntityType.SKELETON;
                Mob mob = type.create(room.level());
                if (mob == null) {
                    continue;
                }
                BlockPos pos = room.pos(at[i][0], 1, at[i][1]);
                mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
                mob.finalizeSpawn(room.level(), room.level().getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null,
                        null);
                mob.setPersistenceRequired();
                room.level().addFreshEntity(mob);
                spawned.add(NbtUtils.createUUID(mob.getUUID()));
            }
            room.state().put("spirits", spawned);
        }

        @Override
        boolean passed(ServerPlayer player, Room room) {
            for (Tag id : room.state().getList("spirits", Tag.TAG_INT_ARRAY)) {
                UUID uuid = NbtUtils.loadUUID(id);
                Entity spirit = room.level().getEntity(uuid);
                if (spirit != null && spirit.isAlive()) {
                    return false;
                }
            }
            return room.state().contains("spirits");
        }
    },

    /** The floor is a sea of lava (held by the solid stone below), with stones to leap across; crossing is the trial. */
    PARKOUR("parkour", false, true) {
        @Override
        void build(Room room) {
            BlockState stone = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            for (int x = -15; x <= 15; x++) {
                for (int z = -15; z <= 15; z++) {
                    boolean arrival = x <= -7 && z <= -7;
                    boolean departure = x >= 4 && z >= 7;
                    // A path along the wall from the arrival to the door of rest: it is always easy to reach.
                    boolean toRest = x <= -13 && z <= 2;
                    room.set(x, 0, z, arrival || departure || toRest ? stone : Blocks.LAVA.defaultBlockState());
                }
            }
            for (int x : new int[]{-4, -1, 2, 5}) {
                room.set(x, 0, -8, stone);
            }
            for (int z : new int[]{-5, -2, 1, 4}) {
                room.set(5, 0, z, stone);
            }
        }
    },

    /** Five levers on a wall, and across the room the pattern of light and dark they must make. */
    PUZZLE("puzzle", true, true) {
        private static final int[] LEVERS_X = {-4, -2, 0, 2, 4};

        @Override
        void build(Room room) {
            int pattern = 1 + room.level().getRandom().nextInt((1 << LEVERS_X.length) - 1);
            room.state().putInt("pattern", pattern);
            for (int i = 0; i < LEVERS_X.length; i++) {
                room.setAbsolute(LEVERS_X[i], 2, -15, Blocks.LEVER.defaultBlockState()
                        .setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.SOUTH)
                        .setValue(LeverBlock.POWERED, false));
                boolean lit = (pattern >> i & 1) == 1;
                room.setAbsolute(LEVERS_X[i], 5, 15, lit ? Blocks.GLOWSTONE.defaultBlockState()
                        : Blocks.BLACK_CONCRETE.defaultBlockState());
            }
        }

        @Override
        boolean passed(ServerPlayer player, Room room) {
            int pattern = room.state().getInt("pattern");
            for (int i = 0; i < LEVERS_X.length; i++) {
                BlockState lever = room.getAbsolute(LEVERS_X[i], 2, -15);
                boolean on = lever.hasProperty(LeverBlock.POWERED) && lever.getValue(LeverBlock.POWERED);
                if (on != ((pattern >> i & 1) == 1)) {
                    return false;
                }
            }
            return true;
        }
    },

    /** The tower hears only who speaks the old tongue: a spell said in the room breaks the seal. */
    RUNES("runes", true, true) {
        @Override
        void build(Room room) {
            BlockPos pos = room.absolute(4, 1, 4);
            room.level().setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 6), 2);
            if (room.level().getBlockEntity(pos) instanceof SignBlockEntity sign) {
                SignText text = new SignText()
                        .setMessage(0, Component.literal("A torre só"))
                        .setMessage(1, Component.literal("ouve quem fala"))
                        .setMessage(2, Component.literal("a língua antiga."));
                sign.setText(text, true);
                sign.setChanged();
            }
        }

        @Override
        boolean heard(ServerPlayer player, Room room, List<String> runes) {
            return true;
        }
    },

    /**
     * The stairs that go down: a dim chapel whose way up is sealed for good. The only way on is back down the stairs the
     * climber came by, and they lead (turning back on themselves more than ever) to the next room, come into from its
     * own stairs; and the count the tower gives has gone back.
     */
    DESCENT("descent", true, false) {
        @Override
        void build(Room room) {
            // Only candles: the room's own lights go out.
            for (int x = -12; x <= 12; x += 6) {
                for (int z = -12; z <= 12; z += 6) {
                    room.setAbsolute(x, 9, z, Blocks.AIR.defaultBlockState());
                }
            }
            BlockState carpet = Blocks.RED_CARPET.defaultBlockState();
            for (int x = -6; x <= 6; x++) {
                for (int z = 10; z <= 12; z++) {
                    room.set(x, 1, z, carpet);
                }
            }
            BlockState pew = Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, room.s() > 0 ? Direction.WEST : Direction.EAST);
            BlockState candles = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3)
                    .setValue(CandleBlock.LIT, true);
            for (int x = -5; x <= 4; x += 3) {
                for (int z = 5; z <= 8; z++) {
                    room.set(x, 1, z, pew);
                }
                for (int z = 14; z <= 15; z++) {
                    room.set(x, 1, z, pew);
                }
                room.set(x, 1, 9, candles);
                room.set(x, 1, 13, candles);
            }
            room.set(6, 1, 9, candles);
            room.set(6, 1, 13, candles);
        }

        @Override
        boolean descends() {
            return true;
        }
    },

    /** The memory: the place the mage sleeps, remembered, and one thing in it wrong ({@link MemoryRoom}). */
    MEMORY("memory", true, false) {
        @Override
        void build(Room room) {
            MemoryRoom.build(room);
        }

        @Override
        void arrive(ServerPlayer player, Room room) {
            Phantur.speak(player, "memory");
        }

        @Override
        boolean struck(ServerPlayer player, Room room, BlockPos pos) {
            return MemoryRoom.struck(player, room, pos);
        }
    },

    /** The mirror: the climber's reflection, a moment late, on the side of the way out ({@link MirrorRoom}). */
    MIRROR("mirror", true, false) {
        @Override
        void build(Room room) {
            MirrorRoom.build(room);
        }

        @Override
        void arrive(ServerPlayer player, Room room) {
            MirrorRoom.arrive(player, room);
        }

        @Override
        void tick(ServerPlayer player, Room room) {
            MirrorRoom.tick(player, room);
        }

        @Override
        boolean passed(ServerPlayer player, Room room) {
            return MirrorRoom.passed(room);
        }
    },

    /**
     * The speech: a cold brazier on an altar, and before it a few words in the old alphabet, in Latin, asking for fire
     * in the hearth. The runes for it are lent for the climb; the seal breaks when the brazier burns.
     */
    SPEECH("speech", true, false) {
        /** What the words may say: all of them, put into the living tongue, ask for fire thrown into the hearth. */
        private static final String[][] RIDDLES = {
                {"IACTA IGNEM", "IN FOCUM"},
                {"FOCUS FRIGET", "IACTA IGNEM"},
                {"IGNIS DORMIT", "IN CINERE", "IACTA EUM", "IN FOCUM"},
                {"SINE IGNE", "NULLA VIA", "IACTA IGNEM", "IN FOCUM"}};

        @Override
        void build(Room room) {
            BlockState altar = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    room.set(x, 1, z, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
                }
            }
            room.set(0, 1, 0, altar);
            room.set(0, 2, 0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
            String[] riddle = RIDDLES[room.level().getRandom().nextInt(RIDDLES.length)];
            Style old = Style.EMPTY.withFont(new ResourceLocation(ElderLexicon.MODID, "sga"));
            SignText text = new SignText();
            for (int line = 0; line < riddle.length; line++) {
                text = text.setMessage(line, Component.literal(riddle[line]).withStyle(old));
            }
            // Before the altar, facing the way in, written on both sides.
            BlockPos pos = room.pos(-3, 1, -3);
            room.level().setBlock(pos, Blocks.DARK_OAK_SIGN.defaultBlockState()
                    .setValue(StandingSignBlock.ROTATION, room.s() > 0 ? 6 : 14), 2);
            if (room.level().getBlockEntity(pos) instanceof SignBlockEntity sign) {
                sign.setText(text, true);
                sign.setText(text, false);
                sign.setWaxed(true);
                sign.setChanged();
            }
        }

        @Override
        void arrive(ServerPlayer player, Room room) {
            Loans.lend(player, Dreams.dream(player), List.of("igni", "iactare"));
            Phantur.speak(player, "speech");
        }

        @Override
        boolean passed(ServerPlayer player, Room room) {
            BlockState brazier = room.level().getBlockState(room.pos(0, 2, 0));
            return brazier.is(Blocks.CAMPFIRE) && brazier.getValue(CampfireBlock.LIT);
        }

        /**
         * Fire said towards the brazier lights it: the tower hears the word, whatever the fire itself does on the way
         * (heat let into the air need not catch on a campfire).
         */
        @Override
        boolean heard(ServerPlayer player, Room room, List<String> runes) {
            BlockPos pos = room.pos(0, 2, 0);
            BlockState brazier = room.level().getBlockState(pos);
            if (!runes.contains("igni") || !brazier.is(Blocks.CAMPFIRE)) {
                return false;
            }
            Vec3 toward = Vec3.atBottomCenterOf(pos).add(0.0D, 0.3D, 0.0D).subtract(player.getEyePosition());
            if (toward.length() > 24.0D || toward.normalize().dot(player.getViewVector(1.0F)) < 0.96D) {
                return false;
            }
            room.level().setBlock(pos, brazier.setValue(CampfireBlock.LIT, true), 3);
            room.level().playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2F, 0.8F);
            room.level().sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    30, 0.3D, 0.4D, 0.3D, 0.03D);
            return true;
        }
    };

    private final String id;
    private final boolean sealed;
    private final boolean provisional;

    TowerRoom(String id, boolean sealed, boolean provisional) {
        this.id = id;
        this.sealed = sealed;
        this.provisional = provisional;
    }

    /** One of the first, plain rooms, to be replaced: when a climb has one room too many, one of these goes. */
    boolean provisional() {
        return provisional;
    }

    String id() {
        return id;
    }

    /** Whether the stairs out of the room are sealed until its trial is passed. */
    boolean sealed() {
        return sealed;
    }

    /** Lays the room out, on its floor already cleared. */
    void build(Room room) {
    }

    /** The climber has come into the room. */
    void arrive(ServerPlayer player, Room room) {
    }

    /** Each tick the climber is in the room, until it opens. */
    void tick(ServerPlayer player, Room room) {
    }

    /** Each second the climber is in the room, until it opens: whether its trial is passed. */
    boolean passed(ServerPlayer player, Room room) {
        return false;
    }

    /** A spell said in the room, of {@code runes}: whether it passes the trial. */
    boolean heard(ServerPlayer player, Room room, List<String> runes) {
        return false;
    }

    /** The climber strikes a block in the room: whether that passes the trial. */
    boolean struck(ServerPlayer player, Room room, BlockPos pos) {
        return false;
    }

    /** Whether the way on from the room is down, not up. */
    boolean descends() {
        return false;
    }

    /**
     * A room laid out on a floor of the tower: {@code y} its floor, {@code s} the way round it is (1 when the stairs
     * leave it at the corner (11, 11)), {@code state} what it keeps while the climb lasts.
     */
    record Room(ServerLevel level, BlockPos origin, int y, int s, CompoundTag state) {

        /** A place in the room, turned the way the room is. */
        BlockPos pos(int x, int dy, int z) {
            return absolute(s * x, dy, s * z);
        }

        /** A place in the room as it is, not turned. */
        BlockPos absolute(int x, int dy, int z) {
            return new BlockPos(origin.getX() + x, y + dy, origin.getZ() + z);
        }

        void set(int x, int dy, int z, BlockState block) {
            level.setBlock(pos(x, dy, z), block, 2);
        }

        void setAbsolute(int x, int dy, int z, BlockState block) {
            level.setBlock(absolute(x, dy, z), block, 2);
        }

        BlockState getAbsolute(int x, int dy, int z) {
            return level.getBlockState(absolute(x, dy, z));
        }
    }
}
