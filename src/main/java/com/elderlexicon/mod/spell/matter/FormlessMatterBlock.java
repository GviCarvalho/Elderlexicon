package com.elderlexicon.mod.spell.matter;

import com.elderlexicon.mod.magic.matter.Qualities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
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
 * Formless matter (docs/plano-materia-e-forca.md, stage 5, and docs/plano-materia-emergente.md): matter near the code of
 * no natural thing, or a thing in a state the table gives it no look in (molten earth, solid fire). The block holds it
 * exactly as it is ({@link FormlessMatterBlockEntity}), coloured by what it is made of, and it acts by what it is like
 * ({@link Qualities}), never by a name:
 * <ul>
 *   <li>hot, it glows, burns what touches it and kindles what burns around it;</li>
 *   <li>wet, it puts out the fire in what is in it and around it;</li>
 *   <li>heavy, as a liquid it is thick, and what wades into it is slowed.</li>
 * </ul>
 * Liquid, it is like a fluid that does not flow: nothing collides with it, nothing is aimed at it, fluids do not wash it
 * away and blocks are not placed over it. Broken or blown up, it is scattered into the air as a gas.
 */
public final class FormlessMatterBlock extends Block implements EntityBlock {

    /** How brightly it glows, with its heat. */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 15);
    /** What a hot one does to what touches it, at the most, each time. */
    private static final float MAX_BURN = 6.0F;

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

    /** What it is like, from what it holds; empty while it holds nothing. */
    static Optional<Qualities> qualities(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FormlessMatterBlockEntity formless
                ? formless.matter().map(Qualities::of) : Optional.empty();
    }

    // ------------------------------------------------------------------ what touches it

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) {
            return;
        }
        qualities(level, pos).ifPresent(qualities -> {
            touch(level, entity, qualities);
            if (liquid && qualities.heavy()) {
                // Thick: the heavier it is, the slower one wades through it.
                double slow = 1.0D - 0.7D * qualities.weight();
                entity.makeStuckInBlock(state, new Vec3(slow, 0.8D, slow));
            }
        });
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && !entity.isSteppingCarefully()) {
            qualities(level, pos).ifPresent(qualities -> touch(level, entity, qualities));
        }
        super.stepOn(level, pos, state, entity);
    }

    /** Hot, it burns what touches it; wet, it puts out the fire on it. */
    private static void touch(Level level, Entity entity, Qualities qualities) {
        if (qualities.hot()) {
            entity.setSecondsOnFire((int) Math.max(1L, Math.round(qualities.heat() * 8.0D)));
            entity.hurt(level.damageSources().inFire(), (float) Math.min(MAX_BURN, qualities.heat() * MAX_BURN));
        } else if (qualities.wet()) {
            entity.clearFire();
        }
    }

    // ------------------------------------------------------------------ what is around it

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** Now and then it acts on what is around it: hot, it kindles what can burn; wet, it puts out fire. */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Optional<Qualities> found = qualities(level, pos);
        if (found.isEmpty()) {
            return;
        }
        Qualities qualities = found.get();
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            BlockState there = level.getBlockState(next);
            if (qualities.wet() && there.is(BlockTags.FIRE)) {
                level.removeBlock(next, false);
            } else if (qualities.hot() && there.isAir() && random.nextDouble() < qualities.heat()) {
                BlockState fire = BaseFireBlock.getState(level, next);
                if (fire.canSurvive(level, next)) {
                    level.setBlock(next, fire, Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(GLOW) <= 0 || random.nextInt(3) != 0) {
            return;
        }
        // Hot: it smokes, and flames lick its top.
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
            formless.matter().ifPresent(matter -> WorldMatter.scatter(server, pos, matter));
        }
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        BlockEntity entity = level.getBlockEntity(pos);
        super.onBlockExploded(state, level, pos, explosion);
        if (level instanceof ServerLevel server && entity instanceof FormlessMatterBlockEntity formless) {
            formless.matter().ifPresent(matter -> WorldMatter.scatter(server, pos, matter));
        }
    }
}
