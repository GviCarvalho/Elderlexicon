package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.Density;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Earth pressed past anything a rock can hold (docs/condensacao-design.md): it falls into itself. The black hole is a
 * vortex: everything within its reach is drawn in, spiralling, harder the closer it gets (with the inverse square);
 * the ground is torn loose from the heart outward, the softest first and the hardest only close in, since the tide
 * that tears it falls with the distance; creatures are drawn from farther than the ground. What reaches the heart is
 * swallowed, and it feeds the hole with its UMU. It evaporates all the while, fast as small ones do, and when it has
 * lost more than it ate and is light enough for a rock to hold it again, it is a well of gravity, settling into
 * obsidian.
 */
public class BlackHole extends Entity {

    private static final EntityDataAccessor<Float> HORIZON = SynchedEntityData.defineId(BlackHole.class, EntityDataSerializers.FLOAT);
    /** How often it tears blocks loose, in ticks. */
    private static final int FEEDING_TICKS = 2;
    /** How much of the swirl goes around rather than in: things spiral down instead of falling straight. */
    private static final double SWIRL = 0.8D;
    /** UMU a living thing gives for each point of health torn from it (a point of health is 5 UMU of Vita). */
    private static final double UMU_PER_HEALTH = 5.0D;

    // Server side only.
    private double density;
    /** The shell of ground it is eating now (its distance from the heart), and the blocks of that shell left to try. */
    private int shell = 1;
    private final List<BlockPos> shellBlocks = new ArrayList<>();

    public BlackHole(EntityType<? extends BlackHole> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** A black hole of {@code density} forms at {@code at}. */
    static void form(ServerLevel level, Vec3 at, double density) {
        BlackHole hole = new BlackHole(ElderLexicon.BLACK_HOLE.get(), level);
        hole.density = density;
        hole.entityData.set(HORIZON, Density.horizon(density));
        hole.setPos(at.x, at.y, at.z);
        level.addFreshEntity(hole);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 2.0F, 0.5F);
    }

    /** How big it looks, in blocks across. */
    public float horizon() {
        return entityData.get(HORIZON);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(HORIZON, 0.35F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            swirl();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        density = Density.evaporateStep(density);
        if (density < Density.BLACK_HOLE) {
            // Light enough again for a rock to hold: a well of gravity, which settles into obsidian.
            level.sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            EarthSpots.place(level, null, BlockPos.containing(position()), density, 0);
            discard();
            return;
        }
        entityData.set(HORIZON, Density.horizon(density));
        pull(level);
        if (tickCount % FEEDING_TICKS == 0) {
            tear(level);
        }
        if (tickCount % 40 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 1.5F, 0.4F);
        }
    }

    /**
     * Everything within reach spirals in, harder the closer; what reaches the heart is swallowed and feeds the hole,
     * and what lives there is torn apart, feeding it with each wound.
     */
    private void pull(ServerLevel level) {
        Vec3 center = position();
        double reach = Density.wellReach(density);
        double heart = horizon() * 0.5D + 0.6D;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(reach),
                candidate -> candidate != this && candidate.isAlive() && !candidate.isSpectator()
                        && (candidate instanceof LivingEntity || candidate instanceof ItemEntity
                        || candidate instanceof FallingBlockEntity || candidate instanceof ExperienceOrb))) {
            Vec3 toward = center.subtract(entity.position().add(0.0D, entity.getBbHeight() / 2.0D, 0.0D));
            double distance = toward.length();
            if (distance > reach) {
                continue;
            }
            if (distance < heart) {
                if (entity instanceof LivingEntity living) {
                    if (tickCount % 10 == 0) {
                        float before = living.getHealth();
                        living.hurt(level.damageSources().fellOutOfWorld(), 4.0F); // torn apart
                        density += Math.max(0.0F, before - living.getHealth()) * UMU_PER_HEALTH;
                    }
                } else {
                    density += swallowed(level, entity);
                    entity.discard();
                    continue;
                }
            }
            Vec3 inward = toward.normalize();
            // Around it, as well as into it: across the line to the heart, level with the ground.
            Vec3 around = new Vec3(-inward.z, 0.0D, inward.x);
            if (around.lengthSqr() < 1.0E-6D) {
                around = new Vec3(1.0D, 0.0D, 0.0D);
            }
            double pull = Density.holePull(density, distance);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.85D)
                    .add(inward.scale(pull)).add(around.normalize().scale(pull * SWIRL)));
            entity.hasImpulse = true;
            entity.hurtMarked = true;
            entity.resetFallDistance();
        }
    }

    /** The UMU what reached the heart gives the hole: a block its hardness, an item a tenth each, experience its worth. */
    private static double swallowed(ServerLevel level, Entity entity) {
        if (entity instanceof FallingBlockEntity block) {
            float hardness = block.getBlockState().getDestroySpeed(level, block.blockPosition());
            return Math.max(0.1D, hardness);
        }
        if (entity instanceof ItemEntity item) {
            return 0.1D * item.getItem().getCount();
        }
        if (entity instanceof ExperienceOrb orb) {
            return orb.getValue() / 10.0D;
        }
        return 0.0D;
    }

    /**
     * The ground is torn loose from the heart outward, a shell at a time: in each shell only the blocks the tide there can
     * tear (the softest farther out, the hardest only close in); the rest holds. Once a shell is done it goes on to the
     * next, and back to the heart after the farthest.
     */
    private void tear(ServerLevel level) {
        int wanted = Density.swallows(density);
        double blockReach = Density.holeBlockReach(density);
        int torn = 0;
        int tried = 0;
        while (torn < wanted && tried < 4096) {
            if (shellBlocks.isEmpty()) {
                shell = shell >= blockReach ? 1 : shell + 1;
                fillShell();
                if (shellBlocks.isEmpty()) {
                    return;
                }
            }
            BlockPos pos = shellBlocks.remove(shellBlocks.size() - 1);
            tried++;
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.CRYING_OBSIDIAN)) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                // Water and lava have no hardness to hold them: they are drawn in, streaming, and feed it.
                if (drink(level, pos, state)) {
                    torn++;
                }
                continue;
            }
            double distance = Math.sqrt(pos.distToCenterSqr(position()));
            if (!Density.tears(density, state.getDestroySpeed(level, pos), distance)) {
                continue; // it holds, this far out
            }
            FallingBlockEntity loose = FallingBlockEntity.fall(level, pos, state);
            loose.dropItem = false;
            loose.setNoGravity(true);
            loose.noPhysics = true; // drawn in through whatever lies between
            Vec3 inward = position().subtract(Vec3.atCenterOf(pos)).normalize();
            loose.setDeltaMovement(inward.scale(0.2D).add(new Vec3(-inward.z, 0.0D, inward.x).scale(0.2D)));
            torn++;
        }
    }

    /**
     * A fluid drawn in: it leaves the world (a block that held water keeps only itself), streams to the heart and feeds
     * the hole with its UMU (a water source 3, lava 10); what is only flowing gives nothing but is taken too.
     */
    private boolean drink(ServerLevel level, BlockPos pos, BlockState state) {
        boolean lava = state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
        boolean source = state.getFluidState().isSource();
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                    false), net.minecraft.world.level.block.Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        if (source) {
            density += lava ? 10.0D : 3.0D;
        }
        // It streams in, spiralling: the glints a conduit draws, or flames.
        Vec3 from = Vec3.atCenterOf(pos);
        Vec3 path = position().subtract(from);
        for (int i = 0; i < 4; i++) {
            if (lava) {
                Vec3 v = path.scale(1.0D / 16.0D);
                level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0D);
            } else {
                level.sendParticles(ParticleTypes.NAUTILUS, getX(), getY(), getZ(), 0, -path.x, -path.y, -path.z, 1.0D);
            }
        }
        return source;
    }

    /** The blocks of the current shell, around the heart at that distance, the ones nearest the heart tried last. */
    private void fillShell() {
        BlockPos origin = blockPosition();
        Vec3 heart = position();
        for (int dx = -shell; dx <= shell; dx++) {
            for (int dy = -shell; dy <= shell; dy++) {
                for (int dz = -shell; dz <= shell; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    double distance = Math.sqrt(pos.distToCenterSqr(heart));
                    if (distance > shell - 1 && distance <= shell) {
                        shellBlocks.add(pos);
                    }
                }
            }
        }
        shellBlocks.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distToCenterSqr(heart)).reversed());
    }

    private void swirl() {
        float size = horizon();
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = size * (1.5D + random.nextDouble());
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(angle) * radius,
                    getY() + (random.nextDouble() - 0.5D) * 0.3D, getZ() + Math.sin(angle) * radius,
                    -Math.sin(angle) * 0.15D, 0.0D, Math.cos(angle) * 0.15D);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
