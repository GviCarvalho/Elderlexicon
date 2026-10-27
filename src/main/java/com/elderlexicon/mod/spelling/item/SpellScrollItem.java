package com.elderlexicon.mod.spelling.item;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A page torn out of a grimoire (a loose page): it keeps its text (DetachedPageText), and is drawn as the grimoire's
 * page it is, with its runes, on a block, in a frame and in the hand (ScrollPage).
 */
public class SpellScrollItem extends Item {

    public SpellScrollItem(Properties properties) {
        super(properties);
    }

    /** A loose page with {@code text} written on it. */
    public static ItemStack of(String text) {
        ItemStack stack = new ItemStack(ElderLexicon.DETACHED_PAGE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TEXT_TAG, text == null ? "" : text);
        com.elderlexicon.mod.spell.block.WrittenTexts.mark(tag);
        return stack;
    }

    /** What is written on a loose page. */
    public static final String TEXT_TAG = "DetachedPageText";

    /** In the hand it is drawn as the page it is, its runes on it (LoosePageItemRenderer). */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.elderlexicon.mod.spelling.client.LoosePageItemRenderer.get();
            }
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (stack.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            PlacedScrollEntity entity = new PlacedScrollEntity(level, context.getClickedPos(), context.getClickedFace(), stack);
            level.addFreshEntity(entity);
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
