package com.elderlexicon.mod.spell.matter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Formless matter (docs/plano-materia-e-forca.md, stage 5): matter in a state the material table gives no look (molten
 * earth, solid fire) or an amalgam, a mixture that matches no recipe. The block holds it exactly as it is
 * ({@link FormlessMatterBlockEntity}), coloured by what it is made of.
 * <ul>
 *   <li>Liquid, it is like a fluid that does not flow: nothing collides with it, nothing is aimed at it, fluids do not
 *       wash it away and blocks are not placed over it.</li>
 *   <li>An amalgam is {@link #UNSTABLE}: it trembles, and when its time is up it falls apart into its primordials (L5).</li>
 *   <li>Broken or blown up, it settles into what it is.</li>
 * </ul>
 */
public final class FormlessMatterBlock extends Block implements EntityBlock {

    /** It is an amalgam, which falls apart with time. */
    public static final BooleanProperty UNSTABLE = BooleanProperty.create("unstable");

    private final boolean liquid;

    public FormlessMatterBlock(Properties properties, boolean liquid) {
        super(properties);
        this.liquid = liquid;
        registerDefaultState(stateDefinition.any().setValue(UNSTABLE, false));
    }

    /** Whether it holds liquid matter. */
    public boolean liquid() {
        return liquid;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UNSTABLE);
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
    public boolean skipRendering(BlockState state, BlockState adjacent, net.minecraft.core.Direction side) {
        return liquid && adjacent.getBlock() == this || super.skipRendering(state, adjacent, side);
    }

    /** An amalgam's time is up (or was when this tick was asked for): it falls apart, unless it was mixed since. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        WorldMatter.ripen(level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(UNSTABLE) || random.nextInt(3) != 0) {
            return;
        }
        // An amalgam trembles: bubbles break on it as it pulls apart.
        level.addParticle(liquid ? ParticleTypes.BUBBLE_POP : ParticleTypes.SMOKE,
                pos.getX() + random.nextDouble(), pos.getY() + (liquid ? 0.9D : 1.02D), pos.getZ() + random.nextDouble(),
                0.0D, 0.02D, 0.0D);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity entity,
                              ItemStack tool) {
        super.playerDestroy(level, player, pos, state, entity, tool);
        if (level instanceof ServerLevel server && entity instanceof FormlessMatterBlockEntity formless) {
            formless.matter().ifPresent(matter -> WorldMatter.settle(server, pos, matter));
        }
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        BlockEntity entity = level.getBlockEntity(pos);
        super.onBlockExploded(state, level, pos, explosion);
        if (level instanceof ServerLevel server && entity instanceof FormlessMatterBlockEntity formless) {
            formless.matter().ifPresent(matter -> WorldMatter.settle(server, pos, matter));
        }
    }
}
