package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The dreams of the functions (docs/aprendizado-runas-design.md, 4c): a function is a verb, something done, so in its
 * dream the physics of the kern is that verb, and then the verb turned round, as a negative quantity turns any verb.
 * The halls are mazes of strange rules more than of harm; the dream has a time limit, and running out of it is waking
 * without the rune.
 */
final class FeverVerbs {

    /** What a scene does beyond its halls: when they are built, each tick of the dreamer, what they break or use. */
    interface Rule {
        Rule NONE = new Rule() {
        };

        default void built(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, CompoundTag state,
                           RandomSource random) {
        }

        default void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
        }

        default void broke(ServerPlayer player, BlockPos island, CompoundTag state, BlockPos pos) {
        }

        default boolean used(ServerPlayer player, BlockPos island, CompoundTag state, BlockPos pos, Direction face,
                             ItemStack held) {
            return false;
        }

        /** Whether the rift stands in its ring of stone; one that moves is only its light. */
        default boolean ringed() {
            return true;
        }
    }

    private FeverVerbs() {
    }

    // ------------------------------------------------------------------ the halls of the verbs

    static final FeverHalls.Stuff THROWN = new FeverHalls.Stuff(Blocks.POLISHED_ANDESITE, Blocks.ANDESITE,
            Blocks.STONE_BRICKS, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, Blocks.STONE_BRICK_STAIRS, Blocks.OAK_FENCE,
            Blocks.STONE_BRICK_SLAB, Blocks.OAK_PLANKS, Blocks.LANTERN, 6, false, false, null)
            .otherwise(FeverHalls.Opening.WIDE, false, false, 2);
    static final FeverHalls.Stuff GAZE = new FeverHalls.Stuff(Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK,
            Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.PURPUR_BLOCK, Blocks.PURPUR_STAIRS, Blocks.BIRCH_FENCE,
            Blocks.PURPUR_SLAB, Blocks.BIRCH_PLANKS, Blocks.END_ROD, 6, false, false, null)
            .otherwise(FeverHalls.Opening.WINDOW, false, false, 2);
    static final FeverHalls.Stuff SUMMONED = new FeverHalls.Stuff(Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK,
            Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.PURPUR_BLOCK, Blocks.PURPUR_STAIRS, Blocks.BIRCH_FENCE,
            Blocks.PURPUR_SLAB, Blocks.BIRCH_PLANKS, Blocks.END_ROD, 6, false, false, null);
    static final FeverHalls.Stuff WARD = new FeverHalls.Stuff(Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.DARK_OAK_FENCE, Blocks.POLISHED_BLACKSTONE_SLAB,
            Blocks.DARK_OAK_PLANKS, Blocks.SOUL_LANTERN, 6, false, false, null);
    static final FeverHalls.Stuff CHANGING = new FeverHalls.Stuff(Blocks.WAXED_EXPOSED_CUT_COPPER,
            Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CUT_COPPER,
            Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.SPRUCE_FENCE, Blocks.WAXED_CUT_COPPER_SLAB, Blocks.SPRUCE_PLANKS,
            Blocks.LANTERN, 6, false, false, null);
    static final FeverHalls.Stuff STILL = new FeverHalls.Stuff(Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS,
            Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.DARK_OAK_PLANKS, Blocks.MUD_BRICK_STAIRS,
            Blocks.DARK_OAK_FENCE, Blocks.MUD_BRICK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.LANTERN, 6, false, false,
            null);
    static final FeverHalls.Stuff BOUND = new FeverHalls.Stuff(Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_WOOD,
            Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_FENCE, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.LANTERN, 6, false, false,
            Blocks.CHAIN);
    static final FeverHalls.Stuff LOOSE = BOUND.otherwise(FeverHalls.Opening.NONE, true, false, 0);
    static final FeverHalls.Stuff NAMED = new FeverHalls.Stuff(Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS,
            Blocks.BOOKSHELF, Blocks.OAK_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_SLAB, Blocks.BOOKSHELF, Blocks.CANDLE, 6, false, false, null);
    static final FeverHalls.Stuff NAMELESS = new FeverHalls.Stuff(Blocks.STONE, Blocks.STONE, Blocks.STONE,
            Blocks.STONE, Blocks.STONE, Blocks.STONE_STAIRS, Blocks.OAK_FENCE, Blocks.STONE_SLAB, Blocks.STONE,
            Blocks.LANTERN, 6, false, false, null).otherwise(FeverHalls.Opening.DOOR, false, true, 6);
    static final FeverHalls.Stuff DARK = new FeverHalls.Stuff(Blocks.BLACKSTONE, Blocks.BLACKSTONE,
            Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICK_STAIRS,
            Blocks.DARK_OAK_FENCE, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.BOOKSHELF, Blocks.AIR, 6, false, false, null);
    static final FeverHalls.Stuff BARE = new FeverHalls.Stuff(Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK,
            Blocks.GLASS, Blocks.GLASS, Blocks.GLASS, Blocks.QUARTZ_STAIRS, Blocks.BIRCH_FENCE, Blocks.QUARTZ_SLAB,
            Blocks.BIRCH_PLANKS, Blocks.SEA_LANTERN, 6, false, false, null)
            .otherwise(FeverHalls.Opening.DOOR, false, false, 4);

    // ------------------------------------------------------------------ iactare

    /** iactare: no walking; a jump is a throw, along the look. Chasms between the rooms. */
    static final Rule THROWN_RULE = new Rule() {
        @Override
        public void built(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, CompoundTag state,
                          RandomSource random) {
            chasms(level, mage, island, rift, THROWN, random);
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            if (player.tickCount % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 5, false, false));
            }
            boolean ground = player.onGround();
            if (ground && player.getY() > island.getY() - 0.5D) {
                // Where the dreamer last stood: the dark gives them back there.
                state.putDouble("lastX", player.getX());
                state.putDouble("lastY", player.getY());
                state.putDouble("lastZ", player.getZ());
            }
            if (player.getY() < island.getY() - 2.5D && state.contains("lastX")) {
                player.teleportTo(state.getDouble("lastX"), state.getDouble("lastY"), state.getDouble("lastZ"));
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 0.6F);
                state.putBoolean("wasGround", false);
                return;
            }
            // The jump, from the ground: thrown along the look, some six blocks. Looking ahead, it flies low, under the
            // lintel of a doorway; looking up, it flies higher and farther.
            if (state.getBoolean("wasGround") && !ground && player.getDeltaMovement().y > 0.3D) {
                Vec3 look = player.getLookAngle();
                Vec3 flat = look.multiply(1.0D, 0.0D, 1.0D).normalize();
                double up = Math.max(0.36D, Math.min(0.8D, 0.42D + look.y * 0.5D));
                player.setDeltaMovement(flat.x * 0.9D, up, flat.z * 0.9D);
                player.hurtMarked = true;
            }
            state.putBoolean("wasGround", ground);
            // What is thrown is not hurt by landing: the trial is to aim, not to survive the falls.
            player.fallDistance = 0.0F;
        }
    };

    /** iactare turned round: what is looked at pulls. */
    static final Rule PULLED_RULE = new Rule() {
        @Override
        public void built(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, CompoundTag state,
                          RandomSource random) {
            chasms(level, mage, island, rift, THROWN, random);
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            player.fallDistance = 0.0F;
            HitResult hit = look(player, 16.0D);
            Vec3 toward = hit.getLocation().subtract(player.position());
            if (hit.getType() == HitResult.Type.BLOCK && toward.length() > 1.5D) {
                Vec3 pull = toward.normalize().scale(0.055D);
                player.push(pull.x, pull.y, pull.z);
                player.hurtMarked = true;
            }
        }
    };

    /** Some rooms' floors gone, chasms into the dark with slime at the bottom: not the middle's, nor the rift's. */
    private static void chasms(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, FeverHalls.Stuff stuff,
                               RandomSource random) {
        int[] riftRoom = FeverHalls.roomAt(island, Vec3.atCenterOf(rift));
        for (int i = 0; i < FeverHalls.ROOMS; i++) {
            for (int j = 0; j < FeverHalls.ROOMS; j++) {
                boolean middle = i == FeverHalls.ROOMS / 2 && j == FeverHalls.ROOMS / 2;
                boolean riftHere = riftRoom != null && riftRoom[0] == i && riftRoom[1] == j;
                if (!middle && !riftHere && random.nextFloat() < 0.4F) {
                    FeverHalls.chasm(level, mage, island, i, j, Blocks.SLIME_BLOCK);
                    // A stone left standing in the middle, three by three: a throw to it from any doorway, and on.
                    BlockPos at = FeverHalls.corner(island, i, j);
                    for (int x = 3; x <= 5; x++) {
                        for (int z = 3; z <= 5; z++) {
                            KernDressing.put(level, mage, at.offset(x, 0, z), stuff.floor().defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ vocant

    /** vocant: no doors, only windows: one goes where one looks, after a moment's gaze. */
    static final Rule GAZE_RULE = new Rule() {
        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            BlockPos at = gazed(player, state, 24.0D, 25);
            if (at == null) {
                return;
            }
            HitResult hit = look(player, 24.0D);
            if (hit instanceof BlockHitResult block) {
                BlockPos to = block.getBlockPos().relative(block.getDirection());
                if (block.getDirection() == Direction.UP || !player.level().getBlockState(to.below()).isAir()) {
                    player.teleportTo(to.getX() + 0.5D, to.getY(), to.getZ() + 0.5D);
                } else {
                    player.teleportTo(to.getX() + 0.5D, to.getY() - 1.0D, to.getZ() + 0.5D);
                }
                player.serverLevel().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D,
                        player.getZ(), 30, 0.4D, 0.8D, 0.4D, 0.5D);
            }
        }
    };

    /** vocant turned round: the rift's room has no way in; what is looked at comes, wall by wall. */
    static final Rule SUMMONED_RULE = new Rule() {
        @Override
        public void built(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, CompoundTag state,
                          RandomSource random) {
            sealRift(level, mage, island, rift, SUMMONED);
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            BlockPos at = gazed(player, state, 10.0D, 25);
            if (at == null) {
                return;
            }
            ServerLevel level = player.serverLevel();
            BlockState thing = level.getBlockState(at);
            if (thing.isAir() || thing.is(Blocks.CRYING_OBSIDIAN) || FeverHalls.roomAt(island, Vec3.atCenterOf(at))
                    == null || at.getY() <= island.getY()) {
                return;
            }
            // It comes away from where it was, and sets itself down behind the one who called it.
            KernDressing.put(level, player.getUUID(), at, Blocks.AIR.defaultBlockState());
            BlockPos behind = player.blockPosition().relative(player.getDirection().getOpposite());
            if (level.getBlockState(behind).isAir()) {
                KernDressing.put(level, player.getUUID(), behind, thing);
            }
            level.sendParticles(ParticleTypes.PORTAL, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, 20, 0.3D,
                    0.3D, 0.3D, 0.4D);
        }
    };

    /**
     * The block the dreamer has been looking at for {@code ticks} ticks without looking away, once, as they reach it;
     * null otherwise. While they look, it glimmers.
     */
    private static BlockPos gazed(ServerPlayer player, CompoundTag state, double reach, int ticks) {
        HitResult hit = look(player, reach);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            state.putInt("gazing", 0);
            return null;
        }
        long at = block.getBlockPos().asLong();
        int gazing = state.getLong("gazedAt") == at ? state.getInt("gazing") + 1 : 0;
        state.putLong("gazedAt", at);
        state.putInt("gazing", gazing);
        if (gazing % 5 == 0) {
            Vec3 where = hit.getLocation();
            player.serverLevel().sendParticles(player, ParticleTypes.REVERSE_PORTAL, true, where.x, where.y, where.z,
                    2 + gazing / 5, 0.2D, 0.2D, 0.2D, 0.0D);
        }
        if (gazing == ticks) {
            state.putInt("gazing", 0);
            state.putLong("gazedAt", Long.MIN_VALUE);
            return block.getBlockPos();
        }
        return null;
    }

    // ------------------------------------------------------------------ impediunt

    /** impediunt: the rift flees from the look; it is come at with the back turned. */
    static final Rule FLEEING_RULE = new Rule() {
        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            Vec3 rift = FeverDream.rift(dream);
            Vec3 toward = rift.subtract(player.getEyePosition());
            shine(player, rift);
            if (toward.length() < 7.0D && player.getLookAngle().dot(toward.normalize()) > 0.3D) {
                // Seen, it flees: to a room far from the one who saw it.
                RandomSource random = player.getRandom();
                for (int tries = 0; tries < 20; tries++) {
                    int i = random.nextInt(FeverHalls.ROOMS);
                    int j = random.nextInt(FeverHalls.ROOMS);
                    Vec3 to = Vec3.atCenterOf(FeverHalls.middle(island, i, j).above(1));
                    if (to.distanceTo(player.position()) > 14.0D) {
                        FeverDream.riftAt(dream, to);
                        player.playNotifySound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                                net.minecraft.sounds.SoundSource.AMBIENT, 0.5F, 1.4F);
                        return;
                    }
                }
            }
        }
    };

    /** impediunt turned round: the rift comes after the one who walks away from it, and backs from the one who comes. */
    static final Rule FOLLOWING_RULE = new Rule() {
        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            Vec3 rift = FeverDream.rift(dream);
            shine(player, rift);
            double far = rift.distanceTo(player.position());
            double was = state.contains("far") ? state.getDouble("far") : far;
            double moved = far - was;
            if (Math.abs(moved) > 0.02D) {
                // Half again what the dreamer moved: walking away, it closes in; coming on, it backs off further.
                Vec3 way = player.position().add(0.0D, 1.0D, 0.0D).subtract(rift).normalize();
                rift = rift.add(way.scale(moved * 1.5D));
                FeverDream.riftAt(dream, rift);
                far = rift.distanceTo(player.position());
            }
            state.putDouble("far", far);
        }
    };

    /** A rift with no ring: its light, in a column, seen through the halls. */
    private static void shine(ServerPlayer player, Vec3 rift) {
        if (player.tickCount % 5 == 0) {
            player.serverLevel().sendParticles(player, ParticleTypes.END_ROD, true, rift.x, rift.y, rift.z, 4, 0.3D,
                    0.6D, 0.3D, 0.01D);
        }
    }

    // ------------------------------------------------------------------ vertere

    /** What the halls of vertere turn into, one after another; the rift opens only while they are of copper. */
    private static final Block[][] FORMS = {
            {Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE},
            {Blocks.WAXED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER},
            {Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE},
            {Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE},
            {Blocks.NETHER_BRICKS, Blocks.BLACKSTONE},
            {Blocks.MOSSY_COBBLESTONE, Blocks.COARSE_DIRT}};
    private static final int COPPER = 1;
    private static final int FORM_TICKS = 60;

    /** vertere: nothing stays what it is; the rift is there only in one of the forms. */
    static final Rule CHANGING_RULE = new Rule() {
        @Override
        public void built(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, CompoundTag state,
                          RandomSource random) {
            state.putInt("form", COPPER);
            state.putLong("riftAt", rift.asLong());
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            if (player.tickCount % FORM_TICKS != 0) {
                return;
            }
            int form = (state.getInt("form") + 1) % FORMS.length;
            state.putInt("form", form);
            FeverHalls.repaint(player.serverLevel(), player.getUUID(), island, CHANGING, FORMS[form][0],
                    FORMS[form][1]);
            BlockPos rift = BlockPos.of(state.getLong("riftAt"));
            FeverDream.riftAt(dream, form == COPPER ? Vec3.atCenterOf(rift) : Vec3.atCenterOf(rift).add(0, -1000, 0));
        }
    };

    /** vertere turned round: nothing changes; statues stand still, but one; touching it opens the rift. */
    static final Rule STILL_RULE = new Rule() {
        @Override
        public boolean ringed() {
            return false;
        }

        @Override
        public void built(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, CompoundTag state,
                          RandomSource random) {
            int moving = random.nextInt(FeverHalls.ROOMS * FeverHalls.ROOMS - 1) + 1;
            int n = 0;
            for (int i = 0; i < FeverHalls.ROOMS; i++) {
                for (int j = 0; j < FeverHalls.ROOMS; j++, n++) {
                    if (i == FeverHalls.ROOMS / 2 && j == FeverHalls.ROOMS / 2) {
                        continue;
                    }
                    ArmorStand statue = new ArmorStand(EntityType.ARMOR_STAND, level);
                    BlockPos at = FeverHalls.middle(island, i, j).offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                    statue.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                    statue.setNoGravity(true);
                    statue.setInvulnerable(true);
                    statue.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
                    FeverDream.mark(statue);
                    level.addFreshEntity(statue);
                    if (n == moving) {
                        state.putUUID("moving", statue.getUUID());
                    }
                }
            }
        }

        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            if (!state.hasUUID("moving")) {
                return;
            }
            if (!state.getBoolean("touched")) {
                FeverDream.riftAt(dream, player.position().add(0.0D, -1000.0D, 0.0D));
            }
            net.minecraft.world.entity.Entity statue = player.serverLevel().getEntity(state.getUUID("moving"));
            if (statue == null) {
                return;
            }
            // The one thing that moves: it turns, slowly.
            float turn = statue.getYRot() + 3.0F;
            statue.setYRot(turn);
            statue.setYHeadRot(turn);
            statue.setYBodyRot(turn);
            if (!state.getBoolean("touched") && statue.distanceTo(player) < 1.8D) {
                state.putBoolean("touched", true);
                Vec3 rift = statue.position().add(0.0D, 1.0D, 0.0D);
                FeverDream.riftAt(dream, rift);
                statue.discard();
                player.playNotifySound(net.minecraft.sounds.SoundEvents.BELL_RESONATE,
                        net.minecraft.sounds.SoundSource.AMBIENT, 1.0F, 1.0F);
            }
            if (state.getBoolean("touched")) {
                shine(player, FeverDream.rift(dream));
            }
        }
    };

    // ------------------------------------------------------------------ ligabis

    /** ligabis: rooms in pairs; what is broken in one is broken in its pair. The rift's room has no way in. */
    static final Rule PAIRS_RULE = new Rule() {
        @Override
        public void built(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, CompoundTag state,
                          RandomSource random) {
            sealRift(level, mage, island, rift, BOUND);
            List<Integer> rooms = new ArrayList<>();
            for (int n = 0; n < FeverHalls.ROOMS * FeverHalls.ROOMS; n++) {
                rooms.add(n);
            }
            java.util.Collections.shuffle(rooms, new java.util.Random(random.nextLong()));
            int[] partner = new int[rooms.size()];
            java.util.Arrays.fill(partner, -1);
            Block[] colours = {Blocks.RED_CARPET, Blocks.ORANGE_CARPET, Blocks.YELLOW_CARPET, Blocks.LIME_CARPET,
                    Blocks.CYAN_CARPET, Blocks.BLUE_CARPET, Blocks.PURPLE_CARPET, Blocks.MAGENTA_CARPET,
                    Blocks.PINK_CARPET, Blocks.BROWN_CARPET, Blocks.WHITE_CARPET, Blocks.BLACK_CARPET};
            for (int n = 0; n + 1 < rooms.size(); n += 2) {
                int a = rooms.get(n);
                int b = rooms.get(n + 1);
                partner[a] = b;
                partner[b] = a;
                // A pair is known by its carpet: the same colour in both.
                Block carpet = colours[(n / 2) % colours.length];
                for (int room : new int[]{a, b}) {
                    BlockPos middle = FeverHalls.middle(island, room / FeverHalls.ROOMS, room % FeverHalls.ROOMS);
                    if (level.getBlockState(middle).isAir()) {
                        KernDressing.put(level, mage, middle, carpet.defaultBlockState());
                    }
                }
            }
            state.putIntArray("partner", partner);
        }

        @Override
        public void broke(ServerPlayer player, BlockPos island, CompoundTag state, BlockPos pos) {
            int[] room = FeverHalls.roomAt(island, Vec3.atCenterOf(pos));
            int[] partner = state.getIntArray("partner");
            if (room == null || partner.length == 0) {
                return;
            }
            int other = partner[room[0] * FeverHalls.ROOMS + room[1]];
            if (other < 0) {
                return;
            }
            BlockPos offset = pos.subtract(FeverHalls.corner(island, room[0], room[1]));
            BlockPos there = FeverHalls.corner(island, other / FeverHalls.ROOMS, other % FeverHalls.ROOMS).offset(offset);
            KernDressing.put(player.serverLevel(), player.getUUID(), there, Blocks.AIR.defaultBlockState());
        }
    };

    /** ligabis turned round: every room loose, a gap between; holding a lead, a bridge to the room looked at. */
    static final Rule LOOSE_RULE = new Rule() {
        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            if (!player.getMainHandItem().is(Items.LEAD) && !player.getOffhandItem().is(Items.LEAD)) {
                return;
            }
            int[] room = FeverHalls.roomAt(island, player.position());
            if (room == null) {
                return;
            }
            Direction way = player.getDirection();
            int i = room[0] + way.getStepX();
            int j = room[1] + way.getStepZ();
            if (i < 0 || j < 0 || i >= FeverHalls.ROOMS || j >= FeverHalls.ROOMS) {
                return;
            }
            // The two stones of the gap before the doorway on that side, bound into a bridge to walk on.
            BlockPos middle = FeverHalls.middle(island, room[0], room[1]).below();
            ServerLevel level = player.serverLevel();
            for (int step = FeverHalls.ROOM / 2; step <= FeverHalls.ROOM / 2 + 1; step++) {
                BlockPos gap = middle.relative(way, step);
                if (level.getBlockState(gap).isAir()) {
                    KernDressing.put(level, player.getUUID(), gap, LOOSE.floor().defaultBlockState());
                    level.sendParticles(ParticleTypes.CRIT, gap.getX() + 0.5D, gap.getY() + 1.0D, gap.getZ() + 0.5D,
                            6, 0.3D, 0.1D, 0.3D, 0.0D);
                }
            }
        }
    };

    // ------------------------------------------------------------------ reframe

    /** reframe: things are what their names say; a name tag called "porta" makes a wall a door. */
    static final Rule NAMED_RULE = new Rule() {
        @Override
        public void built(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, CompoundTag state,
                          RandomSource random) {
            sealRift(level, mage, island, rift, NAMED);
            // Signs that say what is not so: "Saída" on walls that lead nowhere.
            for (int n = 0; n < 6; n++) {
                int i = random.nextInt(FeverHalls.ROOMS);
                int j = random.nextInt(FeverHalls.ROOMS);
                Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos wall = FeverHalls.middle(island, i, j).relative(side, FeverHalls.ROOM / 2 - 1).above();
                sign(level, mage, wall, side.getOpposite(), random.nextBoolean() ? "Saída" : "Porta");
            }
        }

        @Override
        public boolean used(ServerPlayer player, BlockPos island, CompoundTag state, BlockPos pos, Direction face,
                            ItemStack held) {
            if (!held.is(Items.NAME_TAG) || !held.hasCustomHoverName()
                    || !"porta".equalsIgnoreCase(held.getHoverName().getString().trim())
                    || FeverHalls.roomAt(island, Vec3.atCenterOf(pos)) == null || pos.getY() <= island.getY()) {
                return false;
            }
            // Named a door, the wall is a door: through both walls, three high.
            ServerLevel level = player.serverLevel();
            BlockPos base = new BlockPos(pos.getX(), island.getY(), pos.getZ());
            for (BlockPos through : new BlockPos[]{base, base.relative(face.getOpposite())}) {
                for (int y = 1; y <= 3; y++) {
                    KernDressing.put(level, player.getUUID(), through.above(y), Blocks.AIR.defaultBlockState());
                }
            }
            held.shrink(1);
            player.playNotifySound(net.minecraft.sounds.SoundEvents.WOODEN_DOOR_OPEN,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
    };

    /** A sign on a wall, facing out of it, saying {@code text}. */
    private static void sign(ServerLevel level, UUID mage, BlockPos at, Direction facing, String text) {
        if (!level.getBlockState(at).isAir()) {
            return;
        }
        KernDressing.put(level, mage, at, Blocks.OAK_WALL_SIGN.defaultBlockState()
                .setValue(WallSignBlock.FACING, facing));
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(1, Component.literal(text)), true);
            sign.setChanged();
            level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ surgit

    /** surgit: the dark; only what is read is seen: with a book in hand, the sight comes back. */
    static final Rule DARK_RULE = new Rule() {
        @Override
        public void tick(ServerPlayer player, BlockPos island, CompoundTag dream, CompoundTag state) {
            if (player.tickCount % 10 != 0) {
                return;
            }
            boolean reading = book(player.getMainHandItem()) || book(player.getOffhandItem());
            if (reading) {
                player.removeEffect(MobEffects.BLINDNESS);
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
            } else {
                player.removeEffect(MobEffects.NIGHT_VISION);
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
            }
        }
    };

    private static boolean book(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK)
                || stack.is(Items.ENCHANTED_BOOK);
    }

    // ------------------------------------------------------------------ shared

    /** The rift's room walled round: no way in but what the scene's verb opens. */
    private static void sealRift(ServerLevel level, UUID mage, BlockPos island, BlockPos rift, FeverHalls.Stuff stuff) {
        int[] room = FeverHalls.roomAt(island, Vec3.atCenterOf(rift));
        if (room != null) {
            FeverHalls.seal(level, mage, island, room[0], room[1], stuff);
        }
    }

    /** What the dreamer looks at, within {@code reach}. */
    private static HitResult look(ServerPlayer player, double reach) {
        Vec3 eyes = player.getEyePosition();
        return player.level().clip(new ClipContext(eyes, eyes.add(player.getLookAngle().scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    /** A name tag called {@code name}. */
    static ItemStack tag(String name) {
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.setHoverName(Component.literal(name));
        return tag;
    }

}
