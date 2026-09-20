package com.elderlexicon.mod.spelling.item;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

 /**
 * A detached grimoire page backed by vanilla map saved data.
 */
public class SpellScrollItem extends Item {

    public SpellScrollItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Level level, int levelX, int levelZ, byte scale) {
        ItemStack stack = new ItemStack(ExampleMod.DETACHED_PAGE.get());
        MapItemSavedData data = MapItemSavedData.createFresh(levelX, levelZ, scale, false, false, level.dimension());
        int mapId = level.getFreeMapId();
        level.setMapData(MapItem.makeKey(mapId), data);
        stack.getOrCreateTag().putInt("map", mapId);
        return stack;
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
