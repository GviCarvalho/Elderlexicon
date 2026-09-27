package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.AirPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Air in the world (docs/condensacao-design.md): the vacuum left where air was pulled out, and pressed air released.
 * <ul>
 *   <li>A vacuum holds for a moment: creatures in it lose their breath and choke, and nothing burns there. Then the
 *       air rushes back all at once, pulling everything toward where it was, and closes with a clap of thunder (the
 *       same thing a lightning bolt does to the air it tears).</li>
 *   <li>Pressed air, released: a breeze, a gust, a gale that tears light things away and blows fire out, and past that
 *       a bomb, the pressure going all at once as an explosion with no fire.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class AirSpots {

    private static final double BLAST_REACH = 20.0D;
    private static final List<Vacuum> VACUUMS = new ArrayList<>();

    private AirSpots() {
    }

    private record Vacuum(ServerLevel level, Set<BlockPos> blocks, Vec3 center, double radius, long untilTick) {

        boolean holds(Vec3 at) {
            return blocks.contains(BlockPos.containing(at));
        }
    }

    // ------------------------------------------------------------------ the vacuum

    /** Air was pulled out of {@code blocks}: a vacuum there, for {@code ticks}, before the air rushes back. */
    static void vacuum(ServerLevel level, Set<BlockPos> blocks, int ticks) {
        if (blocks.isEmpty()) {
            return;
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (BlockPos pos : blocks) {
            x += pos.getX() + 0.5D;
            y += pos.getY() + 0.5D;
            z += pos.getZ() + 0.5D;
        }
        Vec3 center = new Vec3(x / blocks.size(), y / blocks.size(), z / blocks.size());
        double radius = 0.0D;
        for (BlockPos pos : blocks) {
            radius = Math.max(radius, Vec3.atCenterOf(pos).distanceTo(center));
        }
        VACUUMS.add(new Vacuum(level, Set.copyOf(blocks), center, radius + 0.5D, level.getGameTime() + ticks));
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.6F, 2.0F);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || VACUUMS.isEmpty()) {
            return;
        }
        for (Vacuum vacuum : new ArrayList<>(VACUUMS)) {
            ServerLevel level = vacuum.level();
            if (level.getGameTime() >= vacuum.untilTick()) {
                VACUUMS.remove(vacuum);
                rushBack(vacuum);
                continue;
            }
            holdEmpty(vacuum);
        }
    }

    /** While the vacuum holds: no breath and no fire in it. */
    private static void holdEmpty(Vacuum vacuum) {
        ServerLevel level = vacuum.level();
        AABB box = new AABB(vacuum.center(), vacuum.center()).inflate(vacuum.radius() + 1.0D);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (!vacuum.holds(living.getEyePosition())) {
                continue;
            }
            living.clearFire();
            // No air to breathe: breath runs out faster than it comes back, then it chokes as if drowning.
            int breath = living.getAirSupply() - AirPressure.BREATH_LOST;
            living.setAirSupply(Math.max(-20, breath));
            if (breath <= -20) {
                living.setAirSupply(0);
                living.hurt(level.damageSources().drown(), 2.0F);
            }
        }
        if (level.getGameTime() % 5 == 0) {
            for (BlockPos pos : vacuum.blocks()) {
                if (level.getBlockState(pos).is(BlockTags.FIRE)) {
                    level.removeBlock(pos, false); // nothing burns without air
                }
            }
            // The edge of it shimmers as the air strains to come back in.
            for (int i = 0; i < 6; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0D;
                double pitch = (level.random.nextDouble() - 0.5D) * Math.PI;
                Vec3 edge = vacuum.center().add(Math.cos(angle) * Math.cos(pitch) * vacuum.radius(),
                        Math.sin(pitch) * vacuum.radius(), Math.sin(angle) * Math.cos(pitch) * vacuum.radius());
                Vec3 inward = vacuum.center().subtract(edge).normalize().scale(0.15D);
                level.sendParticles(ParticleTypes.WHITE_ASH, edge.x, edge.y, edge.z, 0, inward.x, inward.y, inward.z, 1.0D);
            }
        }
    }

    /** The air rushes back all at once, pulling everything toward where the vacuum was, and claps like thunder. */
    private static void rushBack(Vacuum vacuum) {
        ServerLevel level = vacuum.level();
        Vec3 center = vacuum.center();
        double reach = vacuum.radius() + 3.0D;
        double strength = Math.min(1.5D, 0.3D + vacuum.blocks().size() / 60.0D);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(reach),
                candidate -> (candidate instanceof LivingEntity || candidate instanceof ItemEntity) && candidate.isAlive()
                        && !candidate.isSpectator())) {
            Vec3 toward = center.subtract(entity.position());
            double distance = toward.length();
            if (distance > reach || distance < 0.3D) {
                continue;
            }
            entity.setDeltaMovement(entity.getDeltaMovement().add(toward.normalize().scale(strength)));
            entity.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 30, vacuum.radius() * 0.3D,
                vacuum.radius() * 0.3D, vacuum.radius() * 0.3D, 0.05D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                (float) Math.min(4.0D, 0.5D + vacuum.blocks().size() / 40.0D), 1.4F);
    }

    // ------------------------------------------------------------------ pressed air

    /**
     * Pressed air released toward the aim: it throws what is in its path along it and, as a gale, tears light things
     * away and blows fire out; pressed into a bomb, it goes off where the aim lands.
     */
    static void blast(ServerLevel level, ServerPlayer caster, double pressure) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0F);
        boolean tears = AirPressure.band(pressure).compareTo(AirPressure.Band.GALE) >= 0;
        Vec3 end = eye.add(look.scale(BLAST_REACH));
        for (double travelled = 1.0D; travelled <= BLAST_REACH; travelled += 0.5D) {
            Vec3 at = eye.add(look.scale(travelled));
            BlockPos pos = BlockPos.containing(at);
            BlockState state = level.getBlockState(pos);
            if (tears && (state.is(BlockTags.FIRE) || isLight(state))) {
                level.destroyBlock(pos, !state.is(BlockTags.FIRE), caster);
                continue;
            }
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                end = at;
                break;
            }
            if (((int) (travelled * 2)) % 3 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0.1D, 0.1D, 0.1D, 0.05D);
            }
        }
        if (AirPressure.band(pressure) == AirPressure.Band.BOMB) {
            bomb(level, caster, end, pressure);
            return;
        }
        Vec3 direction = end.subtract(eye).normalize();
        double push = AirPressure.push(pressure);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(eye, end).inflate(1.5D),
                candidate -> candidate != caster && (candidate instanceof LivingEntity || candidate instanceof ItemEntity)
                        && candidate.isAlive() && !candidate.isSpectator())) {
            Vec3 offset = entity.position().subtract(eye);
            double along = offset.dot(direction);
            if (along < 0.0D || offset.subtract(direction.scale(along)).length() > 2.0D) {
                continue;
            }
            if (tears) {
                entity.clearFire();
            }
            entity.setDeltaMovement(entity.getDeltaMovement().add(direction.scale(push)).add(0.0D, 0.25D, 0.0D));
            entity.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, eye.x + look.x * 2.0D, eye.y + look.y * 2.0D,
                eye.z + look.z * 2.0D, 3, 0.3D, 0.3D, 0.3D, 0.0D);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS,
                (float) Math.min(2.0D, 0.6D + pressure / 15.0D), 1.2F);
    }

    /** Pressed air released at a point: it bursts out all around it, or, pressed into a bomb, goes off there. */
    static void burst(ServerLevel level, ServerPlayer caster, Vec3 at, double pressure) {
        if (AirPressure.band(pressure) == AirPressure.Band.BOMB) {
            bomb(level, caster, at, pressure);
            return;
        }
        double reach = AirPressure.reach(pressure);
        double push = AirPressure.push(pressure);
        boolean tears = AirPressure.band(pressure).compareTo(AirPressure.Band.GALE) >= 0;
        throwAway(level, caster, at, reach, push, tears);
        if (tears) {
            int r = (int) Math.ceil(reach / 2.0D);
            BlockPos center = BlockPos.containing(at);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.FIRE) || isLight(state)) {
                    level.destroyBlock(pos, !state.is(BlockTags.FIRE), caster);
                }
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 40, reach * 0.3D, 0.3D, reach * 0.3D, 0.2D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS,
                (float) Math.min(2.0D, 0.6D + pressure / 15.0D), 0.9F);
    }

    /** The pressure going all at once: an explosion with no fire and a shock that throws everything away. */
    static void bomb(ServerLevel level, ServerPlayer caster, Vec3 at, double pressure) {
        level.explode(caster, at.x, at.y, at.z, AirPressure.bomb(pressure), false, Level.ExplosionInteraction.BLOCK);
        throwAway(level, caster, at, AirPressure.reach(pressure), AirPressure.push(pressure), true);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 60, 1.5D, 1.0D, 1.5D, 0.4D);
    }

    /** Everything within {@code reach} of {@code at} is thrown straight away from it, harder the closer. */
    private static void throwAway(ServerLevel level, ServerPlayer caster, Vec3 at, double reach, double push,
                                  boolean blowsFireOut) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(at, at).inflate(reach),
                candidate -> (candidate instanceof LivingEntity || candidate instanceof ItemEntity) && candidate.isAlive()
                        && !candidate.isSpectator())) {
            Vec3 away = entity.position().add(0.0D, entity.getBbHeight() / 2.0D, 0.0D).subtract(at);
            double distance = away.length();
            if (distance > reach) {
                continue;
            }
            Vec3 direction = distance < 1.0E-3D ? new Vec3(0.0D, 1.0D, 0.0D) : away.normalize();
            if (blowsFireOut) {
                entity.clearFire();
            }
            entity.setDeltaMovement(entity.getDeltaMovement()
                    .add(direction.scale(push * (1.0D - distance / (reach + 1.0D)))).add(0.0D, 0.3D, 0.0D));
            entity.hurtMarked = true;
        }
    }

    /** What a gale tears away: leaves, flowers and plants, glass, torches. */
    private static boolean isLight(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES) || state.getBlock() instanceof TorchBlock
                || state.is(BlockTags.CROPS) || state.is(Blocks.SNOW);
    }
}
