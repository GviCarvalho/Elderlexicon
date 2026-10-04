package com.elderlexicon.mod.spell.matter;

import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.physics.Field;
import com.elderlexicon.mod.spell.nature.NatureWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * Formless matter (docs/particulas-design.md, stage 9): matter near the code of no natural thing, or a thing in a state
 * the table gives it no look in (molten earth). The block holds its particles to the last one
 * ({@link FormlessMatterBlockEntity}), coloured by what they are, and does what they do, never what a name says:
 * <ul>
 *   <li>its agitation is the drives': as the world has it by its nature while it rests, as the drives move it while it
 *       is awake. Past the glow it glows, as lava does;</li>
 *   <li>past the agitation that lets fire go, it sets on fire what touches it; a liquid short of that cuts the air from
 *       what burns in it, and the fire goes out;</li>
 *   <li>a liquid holds back what wades in it as its particles hold together: earth hard, water hardly;</li>
 *   <li>matter that cannot stay as it is (fire no mass holds, air a liquid lets go, water boiling out of a melt) wakes
 *       the drives where it is, and they settle it; matter that can rests as the world does.</li>
 * </ul>
 * Liquid, it is like a fluid that does not flow: nothing collides with it, nothing is aimed at it, fluids do not wash it
 * away and blocks are not placed over it. Broken or blown up, nothing holds it together any more: its particles go into
 * the air there, earth as dust carrying its fire, water as mist.
 */
public final class FormlessMatterBlock extends Block implements EntityBlock {

    /** How brightly it glows, with its agitation. */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 15);
    /** What a hot one does to what touches it, at the most, each time. */
    private static final float MAX_BURN = 6.0F;
    /** How long what it sets on fire burns, in seconds. */
    private static final int BURN_SECONDS = 8;
    /** Ticks after it is laid before it looks at whether it can stay as it is: its particles are given it meanwhile. */
    private static final int LOOK_TICKS = 1;

    private final boolean liquid;

    public FormlessMatterBlock(Properties properties, boolean liquid) {
        super(properties);
        this.liquid = liquid;
        registerDefaultState(stateDefinition.any().setValue(GLOW, 0));
    }

    /** Whether it holds liquid matter. */
    public boolean liquid() {
        return liquid;
    }

    /** The state of the matter it holds. */
    public State state() {
        return liquid ? State.LIQUID : State.SOLID;
    }

    /**
     * How brightly matter as agitated as {@code temperature} glows, 0 to 15: not at all short of the glow (about
     * 525 °C), dimly there, fully at twice it.
     */
    public static int glowOf(double temperature) {
        double glow = Field.glow();
        if (temperature < glow) {
            return 0;
        }
        return (int) Math.min(15L, 1L + Math.round(14.0D * (temperature - glow) / glow));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLOW);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FormlessMatterBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return liquid ? Shapes.empty() : Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return liquid ? Shapes.empty() : Shapes.block();
    }

    @Override
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return liquid && adjacent.getBlock() == this || super.skipRendering(state, adjacent, side);
    }

    /** The particles it holds; empty while it holds nothing. */
    static Optional<Particles> particles(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FormlessMatterBlockEntity formless
                ? formless.particles() : Optional.empty();
    }

    // ------------------------------------------------------------------ what touches it

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        particles(level, pos).ifPresent(held -> {
            touch(server, pos, entity, liquid);
            if (liquid) {
                // It holds back what wades in it as its particles hold together: molten earth hard, water hardly.
                double slow = 1.0D - 0.7D * Field.holding(held);
                entity.makeStuckInBlock(state, new Vec3(slow, 0.8D, slow));
            }
        });
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel server && !entity.isSteppingCarefully()
                && particles(level, pos).isPresent()) {
            touch(server, pos, entity, false);
        }
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Past the agitation that lets fire go, it sets on fire what touches it, the hotter the worse. A liquid short of it
     * that something burning is in cuts its air: the fire goes out.
     */
    private static void touch(ServerLevel level, BlockPos pos, Entity entity, boolean immersed) {
        double temperature = NatureWorld.temperature(level, pos);
        double ignition = Field.ignition();
        if (temperature > ignition) {
            double past = (temperature - ignition) / ignition;
            entity.setSecondsOnFire(BURN_SECONDS);
            entity.hurt(level.damageSources().inFire(), (float) Math.min(MAX_BURN, 1.0D + 2.5D * past));
        } else if (immersed && entity.isOnFire()) {
            entity.clearFire();
        }
    }

    // ------------------------------------------------------------------ whether it can stay as it is

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(this) && level instanceof ServerLevel server) {
            server.scheduleTick(pos, this, LOOK_TICKS);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        stir(level, pos);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** Now and then it looks again: a world loaded with it halfway through a change wakes it once more. */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        stir(level, pos);
    }

    /** What cannot stay as it is wakes the drives where it is, and they settle it. */
    private void stir(ServerLevel level, BlockPos pos) {
        particles(level, pos).filter(held -> Field.restless(held, state()))
                .ifPresent(held -> NatureWorld.wake(level, pos));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(GLOW) <= 0 || random.nextInt(3) != 0) {
            return;
        }
        // Glowing: it smokes, and flames lick its top.
        level.addParticle(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
                pos.getX() + random.nextDouble(), pos.getY() + (liquid ? 0.9D : 1.02D), pos.getZ() + random.nextDouble(),
                0.0D, 0.02D, 0.0D);
    }

    // ------------------------------------------------------------------ broken

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity entity,
                              ItemStack tool) {
        super.playerDestroy(level, player, pos, state, entity, tool);
        if (level instanceof ServerLevel server && entity instanceof FormlessMatterBlockEntity formless) {
            formless.particles().ifPresent(held -> WorldMatter.scatter(server, pos, held));
        }
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        BlockEntity entity = level.getBlockEntity(pos);
        super.onBlockExploded(state, level, pos, explosion);
        if (level instanceof ServerLevel server && entity instanceof FormlessMatterBlockEntity formless) {
            formless.particles().ifPresent(held -> WorldMatter.scatter(server, pos, held));
        }
    }
}
