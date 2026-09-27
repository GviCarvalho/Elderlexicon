package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.Density;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
 * Condensed earth in the world (docs/condensacao-design.md): one block as dense as all the earth that went into it,
 * from stone to obsidian; carbon pressed hard enough turns to diamond; thrown, it falls like a meteor; and past
 * obsidian it is a well of gravity, pulling creatures and things toward it while it relaxes back into obsidian.
 * The blocks have weight: thrown, they fly in an arc and strike like a meteor; left unsupported, they fall.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class EarthSpots {

    private static final List<Well> WELLS = new ArrayList<>();

    private EarthSpots() {
    }

    private record Well(ServerLevel level, BlockPos pos, double density, long born) {

        double now() {
            return Density.relaxed(density, level.getGameTime() - born);
        }
    }

    /** A condensed block on its way: flying (thrown) or falling, until it lands. */
    private static final class Flight {
        final FallingBlockEntity block;
        final ServerPlayer caster;
        final double density;
        final int coal;
        final boolean meteor;
        final long born;
        boolean struck;

        Flight(FallingBlockEntity block, ServerPlayer caster, double density, int coal, boolean meteor, long born) {
            this.block = block;
            this.caster = caster;
            this.density = density;
            this.coal = coal;
            this.meteor = meteor;
            this.born = born;
        }
    }

    private static final List<Flight> FLIGHTS = new ArrayList<>();
    /** A thrown block that has not struck anything by then falls where it is. */
    private static final int FLIGHT_TICKS = 200;
    /** Near what the network carries (3.9 blocks a tick): it strikes almost at once, like a meteor. */
    private static final double THROW_SPEED = 3.5D;

    /**
     * Condensed earth invoked at {@code pos}: the block its density makes, which falls if nothing holds it up, or, for
     * carbon pressed hard enough, diamonds (one for every nine coal).
     */
    static void place(ServerLevel level, ServerPlayer caster, BlockPos pos, double density, int coal) {
        Vec3 at = Vec3.atCenterOf(pos);
        if (makeDiamonds(level, at, density, coal)) {
            return;
        }
        Density.Rock rock = Density.rock(density);
        if (rock == Density.Rock.BLACK_HOLE) {
            // So dense it falls into itself: no block can hold it.
            BlackHole.form(level, at, density);
            return;
        }
        BlockPos spot = freeSpot(level, pos);
        if (spot == null) {
            return;
        }
        level.setBlock(spot, blockOf(rock), Block.UPDATE_ALL);
        level.playSound(null, spot, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 12, 0.4D, 0.4D, 0.4D, 0.02D);
        if (level.getBlockState(spot.below()).canBeReplaced()) {
            // Nothing holds it up: it falls like any heavy block, and a well starts pulling once it lands.
            FallingBlockEntity falling = FallingBlockEntity.fall(level, spot, blockOf(rock));
            falling.setHurtsEntities(2.0F, 40);
            FLIGHTS.add(new Flight(falling, caster, density, 0, false, level.getGameTime()));
            return;
        }
        if (rock == Density.Rock.WELL) {
            WELLS.add(new Well(level, spot, density, level.getGameTime()));
        }
    }

    /**
     * Condensed earth thrown: the block flies from before the mage toward where it looks, arcing as it falls, and
     * strikes like a meteor where it first hits, opening a crater as strong as it is heavy; then it falls into it.
     */
    static void launch(ServerLevel level, ServerPlayer caster, double density, int coal) {
        Vec3 look = caster.getViewVector(1.0F);
        BlockPos start = BlockPos.containing(caster.getEyePosition().add(look.scale(1.5D)));
        if (!level.getBlockState(start).canBeReplaced()) {
            // No room to throw it from: it strikes right there.
            meteor(level, caster, Vec3.atCenterOf(start), density, coal);
            return;
        }
        // Carbon flies as coal and turns to diamond when it strikes.
        BlockState flying = coal > 0 && Density.diamonds(coal, density) > 0
                ? Blocks.COAL_BLOCK.defaultBlockState() : blockOf(Density.rock(density));
        level.setBlock(start, flying, Block.UPDATE_CLIENTS);
        FallingBlockEntity block = FallingBlockEntity.fall(level, start, flying);
        block.setDeltaMovement(look.scale(THROW_SPEED));
        block.setHurtsEntities(2.0F, 40);
        block.hurtMarked = true;
        FLIGHTS.add(new Flight(block, caster, density, coal, true, level.getGameTime()));
        level.playSound(null, start, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.8F, 0.5F);
    }

    /** Where condensed earth strikes: a crater as strong as it is heavy (carbon turns to diamond there instead). */
    static void meteor(ServerLevel level, ServerPlayer caster, Vec3 at, double density, int coal) {
        float strength = Density.meteor(density);
        level.explode(caster, at.x, at.y, at.z, strength, false, Level.ExplosionInteraction.BLOCK);
        // The weight of it: a heavy thud and a cloud of its own dust thrown up around the crater.
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2.0F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 3.0F, 0.6F);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                        blockOf(Density.rock(density))), at.x, at.y + 0.5D, at.z, (int) (40 * strength),
                strength * 0.5D, strength * 0.3D, strength * 0.5D, 0.3D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5D, at.z, (int) (8 * strength),
                strength * 0.4D, 0.3D, strength * 0.4D, 0.02D);
        makeDiamonds(level, at, density, coal);
    }

    private static boolean makeDiamonds(ServerLevel level, Vec3 at, double density, int coal) {
        int diamonds = Density.diamonds(coal, density);
        if (diamonds <= 0) {
            return false;
        }
        // Carbon pressed hard enough: diamond, one for every nine coal; what was left over stays coal.
        Containers.dropItemStack(level, at.x, at.y, at.z, new ItemStack(Items.DIAMOND, diamonds));
        int left = coal - diamonds * Density.COAL_PER_DIAMOND;
        if (left > 0) {
            Containers.dropItemStack(level, at.x, at.y, at.z, new ItemStack(Items.COAL, left));
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 20, 0.3D, 0.3D, 0.3D, 0.05D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 0.5F);
        return true;
    }

    /** Where the block can go: the spot itself, or just above it when something is already there. */
    private static BlockPos freeSpot(ServerLevel level, BlockPos pos) {
        for (int up = 0; up < 3; up++) {
            BlockPos spot = pos.above(up);
            if (level.isLoaded(spot) && level.getBlockState(spot).canBeReplaced()) {
                return spot;
            }
        }
        return null;
    }

    static BlockState blockOf(Density.Rock rock) {
        return switch (rock) {
            case SOIL -> Blocks.DIRT.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
            case DEEPSLATE -> Blocks.DEEPSLATE.defaultBlockState();
            case OBSIDIAN -> Blocks.OBSIDIAN.defaultBlockState();
            // Denser than any rock: it looks like obsidian weeping, and settles into obsidian.
            case WELL, BLACK_HOLE -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
        };
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        fly();
        if (WELLS.isEmpty()) {
            return;
        }
        Iterator<Well> iterator = WELLS.iterator();
        while (iterator.hasNext()) {
            Well well = iterator.next();
            ServerLevel level = well.level();
            if (!level.isLoaded(well.pos()) || !level.getBlockState(well.pos()).is(Blocks.CRYING_OBSIDIAN)) {
                iterator.remove(); // broken or gone: the mass is no longer there
                continue;
            }
            double density = well.now();
            if (density <= Density.OBSIDIAN + 0.5D) {
                // Relaxed: plain obsidian, pulling nothing.
                level.setBlock(well.pos(), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                iterator.remove();
                continue;
            }
            pull(level, well.pos(), density);
        }
    }

    /** Thrown blocks strike where they first hit; landed blocks that are wells start pulling. */
    private static void fly() {
        // Over a copy: a block that lands in its crater is placed again, which may start a new flight.
        for (Flight flight : new ArrayList<>(FLIGHTS)) {
            FallingBlockEntity block = flight.block;
            ServerLevel level = (ServerLevel) block.level();
            boolean hit = block.onGround() || block.horizontalCollision || block.verticalCollision || block.isInWater()
                    || level.getGameTime() - flight.born > FLIGHT_TICKS;
            if (flight.meteor && !flight.struck && !block.isRemoved()) {
                if (!hit) {
                    // A trail of fire and smoke, as of something burning through the air.
                    Vec3 back = block.getDeltaMovement().scale(-0.5D);
                    for (int i = 0; i < 3; i++) {
                        double t = i / 3.0D;
                        level.sendParticles(ParticleTypes.FLAME, block.getX() + back.x * t, block.getY() + 0.5D + back.y * t,
                                block.getZ() + back.z * t, 2, 0.15D, 0.15D, 0.15D, 0.01D);
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, block.getX() + back.x * t,
                                block.getY() + 0.5D + back.y * t, block.getZ() + back.z * t, 1, 0.1D, 0.1D, 0.1D, 0.0D);
                    }
                    continue;
                }
                flight.struck = true;
                Vec3 at = block.position();
                boolean diamonds = Density.diamonds(flight.coal, flight.density) > 0;
                if (diamonds) {
                    block.discard(); // it becomes diamonds where it strikes
                }
                meteor(level, flight.caster, at, flight.density, flight.coal);
                if (diamonds) {
                    FLIGHTS.remove(flight);
                }
                continue;
            }
            if (!block.isRemoved()) {
                continue; // still falling into place
            }
            FLIGHTS.remove(flight);
            if (flight.meteor && !flight.struck) {
                // It landed on the ground in its own tick, before the strike was seen: the block it became is taken
                // back, the ground where it hit is blown open, and it falls again into its crater.
                BlockPos landed = block.blockPosition();
                BlockState expected = block.getBlockState();
                for (BlockPos spot : new BlockPos[]{landed, landed.below(), landed.above()}) {
                    if (level.getBlockState(spot).is(expected.getBlock())) {
                        level.setBlock(spot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        landed = spot;
                        break;
                    }
                }
                boolean diamonds = Density.diamonds(flight.coal, flight.density) > 0;
                meteor(level, flight.caster, Vec3.atCenterOf(landed), flight.density, flight.coal);
                if (!diamonds) {
                    place(level, flight.caster, landed, flight.density, 0);
                }
                continue;
            }
            BlockPos landed = block.blockPosition();
            for (BlockPos spot : new BlockPos[]{landed, landed.below(), landed.above()}) {
                if (level.getBlockState(spot).is(Blocks.CRYING_OBSIDIAN)) {
                    WELLS.add(new Well(level, spot, flight.density, level.getGameTime()));
                    break;
                }
            }
        }
    }

    /** Creatures and things around the well are drawn toward it, harder the denser it is and the closer they are. */
    private static void pull(ServerLevel level, BlockPos pos, double density) {
        Vec3 center = Vec3.atCenterOf(pos);
        double reach = Density.wellReach(density);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(reach),
                candidate -> (candidate instanceof LivingEntity || candidate instanceof ItemEntity)
                        && candidate.isAlive() && !candidate.isSpectator())) {
            Vec3 toward = center.subtract(entity.position().add(0.0D, entity.getBbHeight() / 2.0D, 0.0D));
            double distance = toward.length();
            if (distance > reach || distance < 0.6D) {
                continue;
            }
            double pull = Density.wellPull(density, distance);
            entity.setDeltaMovement(entity.getDeltaMovement().add(toward.normalize().scale(pull)));
            entity.hasImpulse = true;
            entity.hurtMarked = true;
            entity.resetFallDistance();
        }
        if (level.getGameTime() % 4 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 8, reach * 0.4D,
                    reach * 0.4D, reach * 0.4D, 0.0D);
        }
        if (level.getGameTime() % 40 == 0) {
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_AMBIENT, SoundSource.BLOCKS, 1.0F, 0.5F);
        }
    }
}
