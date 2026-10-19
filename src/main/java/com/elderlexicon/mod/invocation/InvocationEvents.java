package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A crafting table used with the burin at hand (in either hand or on the hotbar) opens as an invocation bench, whose
 * squares can then be marked (docs/invocacao-design.md, section 1). Closed with no mark, no line and nothing on it, it
 * is a crafting table again.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class InvocationEvents {

    private InvocationEvents() {
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (event.getHand() != InteractionHand.MAIN_HAND || !level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                || Burins.atHand(event.getEntity()).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        level.setBlock(pos, Invocations.BENCH.get().defaultBlockState(), 3);
        Invocations.BENCH.get().defaultBlockState().use(level, player, InteractionHand.MAIN_HAND, event.getHitVec());
    }
}
