package com.elderlexicon.mod.spelling;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * A grimoire on a lectern (docs/grimorio-design.md): it lies open at the last page someone left it at, and a mage who
 * aims {@code surgit} at the lectern reads that page (ServerSpellingController). Used, the lectern opens the grimoire to
 * be read and its pages turned, not written in; crouching, it is taken, as a book is. A grimoire chained to its
 * lectern (a house's, as the dormitory tower's) is not taken.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class LecternGrimoires {

    /** On a grimoire: it is chained to the lectern it lies on, and cannot be taken from it. */
    public static final String CHAINED_TAG = "Chained";

    /** How near a reader must be to turn a lectern's pages. */
    private static final double READING_REACH = 8.0D;

    private LecternGrimoires() {
    }

    /** The grimoire lying on the lectern at {@code pos}, or null if there is none. */
    @Nullable
    public static LecternBlockEntity holding(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.LECTERN)
                || !(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) {
            return null;
        }
        return lectern.getBook().is(ElderLexicon.GRIMOIRE.get()) ? lectern : null;
    }

    /** The text of the page the grimoire on {@code lectern} lies open at. */
    public static String openPage(LecternBlockEntity lectern) {
        CompoundTag tag = lectern.getBook().getTag();
        com.elderlexicon.mod.spell.block.WrittenTexts.upgrade(tag);
        if (tag == null || !tag.contains("pages", Tag.TAG_LIST)) {
            return "";
        }
        var pages = tag.getList("pages", Tag.TAG_STRING);
        return pages.isEmpty() ? "" : pages.getString(Mth.clamp(lectern.getPage(), 0, pages.size() - 1));
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        LecternBlockEntity lectern = holding(level, pos);
        if (lectern == null) {
            return;
        }
        boolean chained = lectern.getBook().getTag() != null && lectern.getBook().getTag().getBoolean(CHAINED_TAG);
        // Crouching, the lectern gives the book up, as it gives any book: unless it is chained.
        if (event.getEntity().isShiftKeyDown() && !chained) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getEntity().isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("O grimório está preso ao atril."), true);
            return;
        }
        SpellingNetwork.openLecternGrimoire(player, pos, lectern.getPage(), lectern.getBook().copy());
    }

    /** {@code reader} closed the grimoire on the lectern at {@code pos} at {@code page}: it lies open there now. */
    public static void leaveOpenAt(ServerPlayer reader, BlockPos pos, int page) {
        Level level = reader.level();
        if (reader.distanceToSqr(pos.getCenter()) > READING_REACH * READING_REACH) {
            return;
        }
        LecternBlockEntity lectern = holding(level, pos);
        if (lectern == null || lectern.getPage() == page) {
            return;
        }
        // The lectern keeps its page to itself: it is told as a saved one is read, and the page count bounds it.
        CompoundTag saved = lectern.saveWithoutMetadata();
        saved.putInt("Page", Math.max(0, page));
        lectern.load(saved);
        GrimoireItem.storeLastPage(lectern.getBook(), lectern.getPage());
        lectern.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
        LecternBlock.signalPageChange(level, pos, state);
    }
}
