package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The tower does not give way: its stone cannot be broken by a climber, so its trials and its stairs cannot be gone
 * around. Only what a climber may have set down themselves (cobblestone, torches) can be taken up again. Checked on both
 * sides, so the block does not even start to crack. A blow on a block still counts, for the rooms that listen for one;
 * and nothing that bursts there breaks it.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class TowerGuard {

    private TowerGuard() {
    }

    /** Whether the block at {@code pos} is the tower's, beyond a climber's reach. */
    public static boolean guarded(Player player, Level level, BlockPos pos, BlockState state) {
        return !player.isCreative() && Nahva.isNahva(level) && Realms.TOWER.equals(RealmLayout.realmAt(pos.getZ()))
                && !state.is(Blocks.COBBLESTONE) && !state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.getPosition().ifPresent(pos -> {
            if (guarded(event.getEntity(), event.getEntity().level(), pos, event.getState())) {
                event.setCanceled(true);
            }
        });
    }

    /** A climber's blow on a block: some rooms answer it (the memory). */
    @SubscribeEvent
    public static void onStrike(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            TowerRealm.onStrike(player, event.getPos());
        }
    }

    /** Nothing that bursts in the tower breaks it (a bed in Nahvá does). */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (Nahva.isNahva(event.getLevel())
                && Realms.TOWER.equals(RealmLayout.realmAt(event.getExplosion().getPosition().z))) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && guarded(event.getPlayer(), level, event.getPos(),
                event.getState())) {
            event.setCanceled(true);
        }
    }
}
