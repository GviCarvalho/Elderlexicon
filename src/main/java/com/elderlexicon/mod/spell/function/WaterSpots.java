package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.Pressure;
import com.elderlexicon.mod.spell.nature.NatureWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Condensed water in the world (docs/condensacao-design.md): a jet that pushes and puts fire out, a jet that cuts soft
 * ground away, and, pressed hard enough, ice VII: a block of ice that exists only while the spell holds the pressure,
 * and bursts back into all of its water when the pressure goes or it is broken: it shatters what is around, throws
 * things far and floods the hole it made.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class WaterSpots {

    private static final double JET_REACH = 20.0D;
    private static final double THROW_SPEED = 3.5D;
    /** How long the spirit holds thrown ice pressed: if it has hit nothing by then, it bursts where it is. */
    private static final int FLIGHT_TICKS = 40;

    private static final List<HeldIce> ICE = new ArrayList<>();
    private static final List<Flight> FLIGHTS = new ArrayList<>();

    private WaterSpots() {
    }

    private record HeldIce(ServerLevel level, BlockPos pos, double pressure, long untilTick) {
    }

    private record Flight(FallingBlockEntity block, double pressure, long born) {
    }

    // ------------------------------------------------------------------ the jet

    /** Condensed water released as a jet toward the aim: it pushes, puts fire out and, pressed enough, cuts. */
    static void jet(ServerLevel level, ServerPlayer caster, double pressure) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0F);
        int cuts = Pressure.cuts(pressure);
        double push = Pressure.push(pressure);
        double jetReach = Pressure.jetReach(pressure);
        Vec3 end = eye.add(look.scale(jetReach));
        for (double travelled = 1.0D; travelled <= jetReach; travelled += 0.5D) {
            Vec3 at = eye.add(look.scale(travelled));
            BlockPos pos = BlockPos.containing(at);
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE)) {
                level.removeBlock(pos, false);
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.2D, 0.2D, 0.2D, 0.02D);
            } else if (!state.getCollisionShape(level, pos).isEmpty() || !state.canBeReplaced()) {
                if (cuts > 0 && washesAway(state)) {
                    level.destroyBlock(pos, true, caster);
                    cuts--;
                } else {
                    end = at;
                    break; // the jet splashes against what it cannot cut
                }
            }
            if (((int) (travelled * 2)) % 2 == 0) {
                level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 6, 0.1D, 0.1D, 0.1D, 0.2D);
                level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 2, 0.05D, 0.05D, 0.05D, 0.05D);
            }
        }
        // Creatures in its path are thrown along it and their fire is put out.
        AABB path = new AABB(eye, end).inflate(1.0D);
        Vec3 from = eye;
        Vec3 direction = end.subtract(eye).normalize();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, path, candidate -> candidate != caster
                && (candidate instanceof LivingEntity || candidate instanceof ItemEntity) && candidate.isAlive())) {
            Vec3 offset = entity.position().subtract(from);
            double along = offset.dot(direction);
            if (along < 0.0D || offset.subtract(direction.scale(along)).length() > 1.5D) {
                continue;
            }
            entity.clearFire();
            entity.setDeltaMovement(entity.getDeltaMovement().add(direction.scale(push)).add(0.0D, 0.2D, 0.0D));
            entity.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.SPLASH, end.x, end.y, end.z, 30, 0.5D, 0.5D, 0.5D, 0.3D);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS,
                1.5F, (float) Math.max(0.5D, 1.4D - pressure / 40.0D));
    }

    /** Soft ground a pressed jet washes away: soil, sand, gravel, snow, plants and leaves. */
    private static boolean washesAway(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD) || state.is(BlockTags.LEAVES) || state.is(BlockTags.SNOW)
                || state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS) || state.is(Blocks.FARMLAND)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }

    // ------------------------------------------------------------------ ice VII

    /**
     * Water pressed into ice at {@code pos}, held for {@code holdTicks}: when the time is up, or the ice is broken, the
     * pressure goes and it bursts back into all of its water.
     */
    static void ice(ServerLevel level, BlockPos pos, double pressure, int holdTicks) {
        BlockPos spot = pos;
        for (int up = 0; up < 3 && !level.getBlockState(spot).canBeReplaced(); up++) {
            spot = pos.above(up + 1);
        }
        if (!level.getBlockState(spot).canBeReplaced()) {
            burst(level, Vec3.atCenterOf(pos), pressure);
            return;
        }
        level.setBlock(spot, Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, spot, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.5F, 0.5F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D, 20,
                0.4D, 0.4D, 0.4D, 0.02D);
        ICE.add(new HeldIce(level, spot, pressure, level.getGameTime() + Math.max(1, holdTicks)));
    }

    /** Pressed ice thrown: it flies like a stone and bursts where it strikes. */
    static void launchIce(ServerLevel level, ServerPlayer caster, double pressure) {
        Vec3 look = caster.getViewVector(1.0F);
        BlockPos start = BlockPos.containing(caster.getEyePosition().add(look.scale(1.5D)));
        if (!level.getBlockState(start).canBeReplaced()) {
            burst(level, Vec3.atCenterOf(start), pressure);
            return;
        }
        level.setBlock(start, Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
        FallingBlockEntity block = FallingBlockEntity.fall(level, start, Blocks.BLUE_ICE.defaultBlockState());
        block.setDeltaMovement(look.scale(THROW_SPEED));
        block.dropItem = false;
        block.hurtMarked = true;
        FLIGHTS.add(new Flight(block, pressure, level.getGameTime()));
        level.playSound(null, start, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 0.4F);
    }

    /**
     * The pressure goes: the water springs back to its own volume all at once. What is near is thrown away, and all the
     * water that was pressed there is thrown about as droplets, which fall and pool where they land (docs/
     * interacoes-design.md): on the ground they fill the hole, in the air they rain down.
     */
    static void burst(ServerLevel level, Vec3 at, double pressure) {
        float shatter = Pressure.shatter(pressure);
        if (shatter > 0.0F) {
            // Ice VII springing back to water: the pressure held in it shatters what is around, like a boiler bursting,
            // and the water then fills the hole it made.
            level.explode(null, at.x, at.y, at.z, shatter, false, Impacts.explosions());
        }
        double reach = Pressure.burstReach(pressure);
        double push = Pressure.push(pressure);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(at, at).inflate(reach),
                candidate -> (candidate instanceof LivingEntity || candidate instanceof ItemEntity) && candidate.isAlive()
                        && !candidate.isSpectator())) {
            Vec3 away = entity.position().add(0.0D, entity.getBbHeight() / 2.0D, 0.0D).subtract(at);
            double distance = away.length();
            if (distance > reach) {
                continue;
            }
            Vec3 direction = distance < 1.0E-3D ? new Vec3(0.0D, 1.0D, 0.0D) : away.normalize();
            double strength = push * (1.0D - distance / (reach + 1.0D));
            entity.clearFire();
            entity.setDeltaMovement(entity.getDeltaMovement().add(direction.scale(strength)).add(0.0D, 0.3D, 0.0D));
            entity.hurtMarked = true;
        }
        NatureWorld.releaseWater(level, at, pressure);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, (int) Math.min(200.0D, 40.0D + pressure * 2.0D),
                reach * 0.4D, reach * 0.3D, reach * 0.4D, 0.5D);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 20, reach * 0.3D, 0.3D, reach * 0.3D, 0.05D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 1.3F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.BLOCKS, 2.0F, 0.6F);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (HeldIce held : new ArrayList<>(ICE)) {
            ServerLevel level = held.level();
            boolean broken = !level.getBlockState(held.pos()).is(Blocks.BLUE_ICE);
            if (broken || level.getGameTime() >= held.untilTick()) {
                ICE.remove(held);
                if (!broken) {
                    level.setBlock(held.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                burst(level, Vec3.atCenterOf(held.pos()), held.pressure());
            } else if (level.getGameTime() % 10 == 0) {
                // The strain of it: the ice creaks and frosts while it is held.
                level.sendParticles(ParticleTypes.SNOWFLAKE, held.pos().getX() + 0.5D, held.pos().getY() + 0.5D,
                        held.pos().getZ() + 0.5D, 4, 0.4D, 0.4D, 0.4D, 0.01D);
                if (level.getGameTime() % 20 == 0) {
                    level.playSound(null, held.pos(), SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 1.0F, 0.4F);
                }
            }
        }
        for (Flight flight : new ArrayList<>(FLIGHTS)) {
            FallingBlockEntity block = flight.block();
            ServerLevel level = (ServerLevel) block.level();
            boolean hit = block.onGround() || block.horizontalCollision || block.verticalCollision || block.isInWater()
                    || level.getGameTime() - flight.born() > FLIGHT_TICKS;
            if (block.isRemoved()) {
                // It landed in its own tick: take back the ice it became and burst there.
                FLIGHTS.remove(flight);
                BlockPos landed = block.blockPosition();
                for (BlockPos spot : new BlockPos[]{landed, landed.below(), landed.above()}) {
                    if (level.getBlockState(spot).is(Blocks.BLUE_ICE)) {
                        level.setBlock(spot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        landed = spot;
                        break;
                    }
                }
                burst(level, Vec3.atCenterOf(landed), flight.pressure());
            } else if (hit) {
                FLIGHTS.remove(flight);
                Vec3 at = block.position();
                block.discard();
                burst(level, at, flight.pressure());
            } else {
                level.sendParticles(ParticleTypes.SNOWFLAKE, block.getX(), block.getY() + 0.5D, block.getZ(), 3,
                        0.15D, 0.15D, 0.15D, 0.01D);
            }
        }
    }
}
