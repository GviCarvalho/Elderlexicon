package com.elderlexicon.mod.invocation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The invocation bench (docs/invocacao-design.md, section 1): a crafting table with its nine squares scratched by the
 * burin, each bearing a mark. Worked by hand it is a crafting table that keeps its grid.
 */
public final class InvocationBenchBlock extends Block implements EntityBlock {

    private static final Component TITLE = Component.translatable("container.elderlexicon.invocation_bench");

    public InvocationBenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InvocationBenchBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != Invocations.BENCH_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, bench) ->
                InvocationBenchBlockEntity.serverTick(tickLevel, pos, tickState, (InvocationBenchBlockEntity) bench);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof InvocationBenchBlockEntity) {
            NetworkHooks.openScreen(server, new SimpleMenuProvider((id, inventory, opener) ->
                    new InvocationBenchMenu(id, inventory, pos), TITLE), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof InvocationBenchBlockEntity bench) {
                bench.contents().forEach(stack -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        stack.copy()));
            }
            if (level instanceof ServerLevel server) {
                BenchSlots.get(server.getServer()).forget(level.dimension(), pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
