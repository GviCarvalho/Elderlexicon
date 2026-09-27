package com.elderlexicon.mod.spell.nature;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.AirPressure;
import com.elderlexicon.mod.spell.Pressure;
import com.elderlexicon.mod.spell.function.AirSpots;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Holds the {@link NatureField} of each world, reads the world's blocks as {@link Material}s for it, runs its laws
 * every tick and does in the world what they decide: blocks that change phase, steam, and bursts.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class NatureWorld {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** At most this many steam puffs are shown each tick; the rest still happen. */
    private static final int STEAM_SHOWN = 48;
    private static final Map<ServerLevel, NatureField> FIELDS = new WeakHashMap<>();
    /** The share of a pressure's energy that its air, let out, loses as it expands: it cools the air it rushes into. */
    private static final double EXPANSION_COOLING = 0.5D;
    /** How far down from the charge lightning looks for the ground to strike. */
    private static final int STRIKE_DEPTH = 64;

    private NatureWorld() {
    }

    private static NatureField fieldOf(ServerLevel level) {
        return FIELDS.computeIfAbsent(level, key -> new NatureField());
    }

    private static NatureField.Matter matterOf(ServerLevel level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return key -> {
            cursor.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
            if (!level.isLoaded(cursor) || level.isOutsideBuildHeight(cursor)) {
                return Material.VOID;
            }
            return materialOf(level.getBlockState(cursor));
        };
    }

    // ------------------------------------------------------------------ what spells put in

    /** Warms the block at {@code pos} toward {@code target}, spending at most {@code budget} UMU; returns what it spent. */
    public static double warm(ServerLevel level, BlockPos pos, double target, double budget) {
        return fieldOf(level).warm(matterOf(level), pos.asLong(), target, budget);
    }

    /** Wakes the block at {@code pos} with the heat it has by nature (lava a spell has just melted). */
    public static void wake(ServerLevel level, BlockPos pos) {
        fieldOf(level).wake(matterOf(level), pos.asLong());
    }

    /** Puts {@code energy} UMU of heat into the block at {@code pos}. */
    public static void heat(ServerLevel level, BlockPos pos, double energy) {
        fieldOf(level).heat(matterOf(level), pos.asLong(), energy);
    }

    /**
     * Pressed air let out at {@code at}: it rushes out, stirring the air within {@code reach}, and, expanding from so
     * great a pressure, cools it (air let out of a tank comes out freezing).
     */
    public static void releaseAir(ServerLevel level, Vec3 at, double pressure, double reach) {
        NatureField field = fieldOf(level);
        NatureField.Matter matter = matterOf(level);
        long center = BlockPos.containing(at).asLong();
        field.stir(matter, center, reach, pressure);
        field.heatAir(matter, center, reach, -EXPANSION_COOLING * pressure);
    }

    /**
     * Pressed water let out at {@code at}: it springs back to its own volume, thrown about as droplets that fall and
     * pool, and the burst stirs the air around.
     */
    public static void releaseWater(ServerLevel level, Vec3 at, double pressure) {
        NatureField field = fieldOf(level);
        NatureField.Matter matter = matterOf(level);
        long center = BlockPos.containing(at).asLong();
        double reach = Pressure.burstReach(pressure);
        field.spray(matter, center, reach / 2.0D, pressure);
        field.stir(matter, center, reach, pressure);
    }

    /** {@code water} UMU of water thrown into the air around {@code at} as droplets, over {@code reach}: they fall and pool. */
    public static void spray(ServerLevel level, Vec3 at, double reach, double water) {
        fieldOf(level).spray(matterOf(level), BlockPos.containing(at).asLong(), reach, water);
    }

    public static boolean isMatter(ServerLevel level, BlockPos pos) {
        Material material = materialOf(level.getBlockState(pos));
        return !material.gas && material != Material.VOID;
    }

    // ------------------------------------------------------------------ the laws, each tick

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        NatureField field = FIELDS.get(level);
        if (field == null || field.isEmpty()) {
            return;
        }
        try {
            NatureField.Step step = field.step(matterOf(level));
            apply(level, step);
        } catch (RuntimeException exception) {
            LOGGER.error("Nature step failed", exception);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FIELDS.remove(level);
        }
    }

    private static void apply(ServerLevel level, NatureField.Step step) {
        for (NatureField.Change change : step.changes()) {
            BlockPos pos = BlockPos.of(change.key());
            BlockState state = level.getBlockState(pos);
            if (materialOf(state) != change.from()) {
                continue; // it was changed by someone else meanwhile
            }
            BlockState into = blockOf(change.to());
            if (into != null) {
                level.setBlock(pos, into, Block.UPDATE_ALL);
            }
            if (change.from() == Material.LAVA) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                        3, 0.3D, 0.1D, 0.3D, 0.01D);
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.0F);
            }
        }
        steam(level, step.steams());
        for (NatureField.Burst burst : step.bursts()) {
            burst(level, BlockPos.of(burst.key()), burst.pressure());
        }
        for (NatureField.Discharge discharge : step.discharges()) {
            strike(level, BlockPos.of(discharge.key()), discharge.charge());
        }
    }

    private static void steam(ServerLevel level, List<NatureField.Steam> steams) {
        if (steams.isEmpty()) {
            return;
        }
        int every = Math.max(1, steams.size() / STEAM_SHOWN);
        for (int i = 0; i < steams.size(); i += every) {
            NatureField.Steam steam = steams.get(i);
            BlockPos pos = BlockPos.of(steam.key());
            int count = (int) Math.min(8.0D, 1.0D + steam.amount() * 4.0D);
            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D, count,
                    0.3D, 0.2D, 0.3D, 0.03D);
        }
        if (level.getGameTime() % 10 == 0) {
            BlockPos pos = BlockPos.of(steams.get(level.random.nextInt(steams.size())).key());
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                    (float) Math.min(1.5D, 0.3D + steams.size() / 20.0D), 0.8F);
        }
    }

    /**
     * Vapor that could not get out bursts: the water around is blown to spray, and the pressure goes off like any
     * pressed air (water swallows an explosion's force, so it has to be thrown clear first).
     */
    private static void burst(ServerLevel level, BlockPos center, double pressure) {
        int spray = (int) Math.min(8.0D, Math.ceil(AirPressure.bomb(pressure) / 2.0D));
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-spray, -spray, -spray),
                center.offset(spray, spray, spray))) {
            if (pos.distSqr(center) <= spray * spray && level.getBlockState(pos).is(Blocks.WATER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        Vec3 at = Vec3.atCenterOf(center);
        AirSpots.bomb(level, null, at, pressure);
        fieldOf(level).stir(matterOf(level), center.asLong(), AirPressure.reach(pressure), pressure);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0D, at.z, 80, spray, spray, spray, 0.3D);
    }

    /**
     * Charge that grew too strong strikes the ground below it as lightning, harder the more charge it held; with no
     * ground near, it flashes in the air.
     */
    private static void strike(ServerLevel level, BlockPos from, double charge) {
        BlockPos.MutableBlockPos ground = from.mutable();
        boolean found = false;
        for (int down = 0; down < STRIKE_DEPTH && ground.getY() > level.getMinBuildHeight(); down++) {
            if (!materialOf(level.getBlockState(ground.below())).gas) {
                found = true;
                break;
            }
            ground.move(0, -1, 0);
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(Vec3.atBottomCenterOf(found ? ground : from));
        bolt.setVisualOnly(!found);
        bolt.setDamage((float) (5.0D + 5.0D * Math.log10(charge / NatureField.DISCHARGE)));
        level.addFreshEntity(bolt);
    }

    // ------------------------------------------------------------------ blocks and materials

    static Material materialOf(BlockState state) {
        if (state.isAir()) {
            return Material.AIR;
        }
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return Material.LAVA;
        }
        if (state.is(Blocks.WATER)) {
            return Material.WATER;
        }
        if (state.is(BlockTags.ICE)) {
            return Material.ICE;
        }
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            return Material.SNOW;
        }
        if (state.is(BlockTags.FIRE)) {
            return Material.AIR; // a flame is hot gas
        }
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) {
            return Material.OBSIDIAN;
        }
        if (state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.POLISHED_BASALT)) {
            return Material.BASALT;
        }
        if (state.is(BlockTags.SAND)) {
            return Material.SAND;
        }
        if (state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL)) {
            return Material.SOIL;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE)) {
            return Material.STONE;
        }
        if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
            return Material.AIR; // grass and flowers: the air around them
        }
        return Material.SOLID;
    }

    /** The block a change of phase leaves, or null when the block should stay as it is. */
    static BlockState blockOf(Material material) {
        return switch (material) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case ICE -> Blocks.ICE.defaultBlockState();
            case SNOW -> Blocks.SNOW.defaultBlockState();
            case OBSIDIAN -> Blocks.OBSIDIAN.defaultBlockState();
            case BASALT -> Blocks.BASALT.defaultBlockState();
            default -> null;
        };
    }
}
