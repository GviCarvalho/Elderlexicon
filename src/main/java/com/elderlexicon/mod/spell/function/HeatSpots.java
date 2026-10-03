package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.nature.NatureWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Condensed heat in the world (docs/condensacao-design.md): where a condensed fire strikes or is invoked, a hot spot
 * stays and cools down little by little, doing what its heat can while it lasts: a white fire turns sand to glass, a
 * melting one turns stone into lava, and plasma bursts where it first strikes.
 * <p>
 * Its heat also goes into the world's nature (docs/interacoes-design.md): half of it into what it touches when it
 * strikes, the other half radiated into the matter around as it cools. What that heat does to water, ice and snow (and
 * to whatever else meets it) is up to the laws of nature, not to the fire.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class HeatSpots {

    private static final int SWEEP_TICKS = 5;
    /** Past this many blocks in its reach, a sweep goes through a spread of spots instead of every block. */
    private static final int SWEEP_LIMIT = 20_000;
    private static final List<Spot> SPOTS = new ArrayList<>();

    private HeatSpots() {
    }

    /** The share of a strike's heat that goes into what it touches at once; the rest is radiated as it cools. */
    private static final double CONTACT = 0.5D;
    /** How far from where it strikes the heat is in contact, in blocks. */
    private static final double CONTACT_REACH = 1.5D;

    private static final class Spot {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 at;
        final double heat;
        final long born;
        /** The heat it had when it last radiated: what it lost since is what it radiates now. */
        double radiated;

        Spot(ServerLevel level, ServerPlayer caster, Vec3 at, double heat, long born) {
            this.level = level;
            this.caster = caster;
            this.at = at;
            this.heat = heat;
            this.born = born;
            this.radiated = heat;
        }

        ServerLevel level() {
            return level;
        }

        ServerPlayer caster() {
            return caster;
        }

        Vec3 at() {
            return at;
        }

        double now() {
            return Heat.cooled(heat, level.getGameTime() - born);
        }
    }

    /** A block of matter the heat reaches, and how much of it reaches there (1 at the heart, less farther out). */
    private record Reached(BlockPos pos, double share) {
    }

    /** A condensed fire of {@code heat} strikes at {@code at}: plasma bursts there, and a hot spot is left to cool. */
    static void strike(ServerLevel level, ServerPlayer caster, Vec3 at, double heat) {
        if (heat <= Heat.SPENT) {
            return;
        }
        if (Heat.band(heat) == Heat.Band.PLASMA) {
            // Plasma: a burst with the crack of lightning, as hot as it is.
            LightningBolt flash = EntityType.LIGHTNING_BOLT.create(level);
            if (flash != null) {
                flash.moveTo(at);
                flash.setVisualOnly(true);
                level.addFreshEntity(flash);
            }
            level.explode(caster, at.x, at.y, at.z, Heat.burst(heat), true, Impacts.explosions());
        }
        Spot spot = new Spot(level, caster, at, heat, level.getGameTime());
        touch(level, at, heat);
        sweep(spot, heat);
        SPOTS.add(spot);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SPOTS.isEmpty()) {
            return;
        }
        Iterator<Spot> iterator = SPOTS.iterator();
        while (iterator.hasNext()) {
            Spot spot = iterator.next();
            if (spot.level().getGameTime() % SWEEP_TICKS != 0) {
                continue;
            }
            double heat = spot.now();
            if (heat <= Heat.SPENT) {
                iterator.remove(); // cooled back to common fire
                continue;
            }
            sweep(spot, heat);
        }
    }

    /** Where it strikes, what it touches takes half its heat at once (water there may flash to vapor). */
    private static void touch(ServerLevel level, Vec3 at, double heat) {
        List<Reached> touched = new ArrayList<>();
        BlockPos center = BlockPos.containing(at);
        int r = (int) Math.ceil(CONTACT_REACH);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            double distance = at.distanceTo(Vec3.atCenterOf(pos));
            if (distance <= CONTACT_REACH + 0.5D && level.isLoaded(pos) && NatureWorld.isMatter(level, pos)) {
                touched.add(new Reached(pos.immutable(), 1.0D - distance / (CONTACT_REACH + 1.0D)));
            }
        }
        radiate(level, touched, heat, heat * CONTACT);
    }

    /**
     * Gives {@code budget} UMU of heat to the matter reached, each block its part by how much of the heat reaches it,
     * warming none past the heat that reaches it there. What none of them takes is lost to the sky.
     */
    private static void radiate(ServerLevel level, List<Reached> reached, double heat, double budget) {
        if (reached.isEmpty() || budget <= 0.0D) {
            return;
        }
        double shares = 0.0D;
        for (Reached block : reached) {
            shares += block.share();
        }
        for (Reached block : reached) {
            NatureWorld.warm(level, block.pos(), heat * block.share(), budget * block.share() / shares);
        }
    }

    /** What the heat does around the spot right now; it reaches less far as it cools. */
    private static void sweep(Spot spot, double heat) {
        ServerLevel level = spot.level();
        Vec3 at = spot.at();
        double reach = Heat.reach(heat);
        Heat.Band band = Heat.band(heat);
        show(level, at, heat, band, reach);
        burnCreatures(spot, heat, reach);
        if (band == Heat.Band.COMMON) {
            return;
        }
        BlockPos center = BlockPos.containing(at);
        int r = (int) Math.ceil(reach);
        double reachSq = reach * reach;
        double meltSq = reach * reach * 0.36D; // stone melts only near the heart of it
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<Reached> reached = new ArrayList<>();
        long volume = (2L * r + 1) * (2L * r + 1) * (2L * r + 1);
        if (volume > SWEEP_LIMIT) {
            // Too much to go through every block each time: a spread of spots, different each sweep, does it by degrees.
            for (int i = 0; i < SWEEP_LIMIT; i++) {
                cursor.set(center.getX() + level.random.nextInt(2 * r + 1) - r,
                        center.getY() + level.random.nextInt(2 * r + 1) - r,
                        center.getZ() + level.random.nextInt(2 * r + 1) - r);
                double distanceSq = at.distanceToSqr(Vec3.atCenterOf(cursor));
                if (distanceSq <= reachSq && level.isLoaded(cursor)) {
                    heatBlock(level, cursor.immutable(), level.getBlockState(cursor), band, distanceSq <= meltSq);
                    reach(level, cursor, distanceSq, reach, reached);
                }
            }
        } else {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        double distanceSq = at.distanceToSqr(Vec3.atCenterOf(cursor));
                        if (distanceSq > reachSq || !level.isLoaded(cursor)) {
                            continue;
                        }
                        heatBlock(level, cursor.immutable(), level.getBlockState(cursor), band, distanceSq <= meltSq);
                        reach(level, cursor, distanceSq, reach, reached);
                    }
                }
            }
        }
        // What it lost as it cooled since it last radiated goes into the matter around (the rest went in when it struck).
        radiate(level, reached, heat, (spot.radiated - heat) * (1.0D - CONTACT));
        spot.radiated = heat;
        cookItems(level, at, reach);
        // The heart keeps a flame burning while it is hot.
        if (level.getBlockState(center).canBeReplaced() && !level.getBlockState(center).getFluidState().is(FluidTags.LAVA)) {
            BlockState flame = BaseFireBlock.getState(level, center);
            if (flame.canSurvive(level, center)) {
                level.setBlock(center, flame, Block.UPDATE_ALL);
            }
        }
    }

    private static void reach(ServerLevel level, BlockPos pos, double distanceSq, double reach, List<Reached> reached) {
        if (NatureWorld.isMatter(level, pos)) {
            reached.add(new Reached(pos.immutable(), 1.0D - Math.sqrt(distanceSq) / (reach + 0.5D)));
        }
    }

    /** What the fire itself does to rock and sand; water, ice and snow are left to the laws of nature. */
    private static void heatBlock(ServerLevel level, BlockPos pos, BlockState state, Heat.Band band, boolean heart) {
        if (state.is(BlockTags.SAND)) {
            level.setBlock(pos, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
        } else if (band.compareTo(Heat.Band.MELTING) >= 0 && heart && melts(state)) {
            // Stone melts: the lava is the ground molten, not the fire. It cools and sets again once the fire is gone.
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
            NatureWorld.wake(level, pos);
        }
    }

    private static boolean melts(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(BlockTags.DIRT)
                || state.is(Blocks.GRAVEL);
    }

    /** What lies on the ground is cooked, as in a furnace. */
    private static void cookItems(ServerLevel level, Vec3 at, double reach) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(reach))) {
            ItemStack stack = item.getItem();
            level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level)
                    .ifPresent(recipe -> {
                        ItemStack cooked = recipe.getResultItem(level.registryAccess()).copy();
                        cooked.setCount(cooked.getCount() * stack.getCount());
                        item.setItem(cooked);
                    });
        }
    }

    /** Creatures in reach burn, harder and longer the hotter it is; the caster is spared, as by any fire it makes. */
    private static void burnCreatures(Spot spot, double heat, double reach) {
        ServerLevel level = spot.level();
        Vec3 at = spot.at();
        float damage = (float) Math.min(Float.MAX_VALUE, 0.5D + heat * 0.15D);
        int seconds = (int) Math.min(1_000_000.0D, Math.ceil(heat / 2.0D));
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach),
                candidate -> candidate != spot.caster() && candidate.isAlive() && !candidate.isSpectator()
                        && candidate.position().distanceTo(at) <= reach + 0.5D)) {
            living.setSecondsOnFire(Math.max(seconds, living.getRemainingFireTicks() / 20));
            living.hurt(level.damageSources().inFire(), damage);
        }
    }

    private static void show(ServerLevel level, Vec3 at, double heat, Heat.Band band, double reach) {
        ParticleOptions flame = band == Heat.Band.COMMON ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME;
        int count = (int) Math.min(40.0D, 6.0D + heat);
        level.sendParticles(flame, at.x, at.y + 0.3D, at.z, count, reach * 0.4D, 0.4D, reach * 0.4D, 0.02D);
        if (band.compareTo(Heat.Band.MELTING) >= 0) {
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3D, at.z, 4, reach * 0.3D, 0.2D, reach * 0.3D, 0.0D);
        }
        if (band == Heat.Band.PLASMA) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5D, at.z, 20, reach * 0.4D, 0.5D,
                    reach * 0.4D, 0.2D);
        }
        if (level.getGameTime() % 20 == 0) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                    (float) Math.min(2.0D, 0.5D + heat / 20.0D), 0.8F);
        }
    }
}
