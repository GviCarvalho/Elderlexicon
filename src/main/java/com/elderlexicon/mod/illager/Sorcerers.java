package com.elderlexicon.mod.illager;

import com.elderlexicon.mod.Config;
import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.SpellTicks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Where sorcerers come from (docs/feiticeiro-design.md, 7): some of the evokers the world makes are sorcerers. Each
 * vanilla evoker is looked at once, as it first comes into the world (a mansion's when its chunk first loads, a raid's
 * when its wave comes): with a chance, a sorcerer takes its place, where it stood, in the raid and wave it was in.
 * The chances are in the config: half the mansions' evokers, and in a raid more the later the wave.
 * <p>
 * A mansion's sorcerer has a corner to study in where it stands: a lectern and shelves of books, put only where
 * there is air over a firm floor.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Sorcerers {

    /** Marks a vanilla evoker that was looked at already, so it is looked at once. */
    private static final String LOOKED_AT = ElderLexicon.MODID + ":sorcerer_looked_at";

    private Sorcerers() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEntity().getType() != EntityType.EVOKER
                || !(event.getEntity() instanceof Evoker evoker)) {
            return;
        }
        if (evoker.getPersistentData().getBoolean(LOOKED_AT)) {
            return;
        }
        evoker.getPersistentData().putBoolean(LOOKED_AT, true);
        Raid raid = evoker.getCurrentRaid();
        boolean mansion = raid == null && evoker.isPersistenceRequired();
        double chance = raid != null
                ? Config.sorcererRaidChance + Config.sorcererRaidChancePerWave * Math.max(0, evoker.getWave() - 1)
                : mansion ? Config.sorcererMansionChance : Config.sorcererOtherChance;
        if (level.getRandom().nextDouble() >= chance) {
            return;
        }
        // Not while the world is adding it (a chunk may be loading): the next tick.
        SpellTicks.schedule(level.getServer(), 1, () -> replace(level, evoker, mansion));
    }

    /** A sorcerer takes {@code evoker}'s place: where it stood, as it looked, in its raid and wave. */
    static void replace(ServerLevel level, Evoker evoker, boolean mansion) {
        if (evoker.isRemoved() || !evoker.isAlive() || evoker.level() != level) {
            return;
        }
        Sorcerer sorcerer = ElderLexicon.SORCERER.get().create(level);
        if (sorcerer == null) {
            return;
        }
        sorcerer.moveTo(evoker.getX(), evoker.getY(), evoker.getZ(), evoker.getYRot(), evoker.getXRot());
        sorcerer.setYHeadRot(evoker.getYHeadRot());
        if (evoker.hasCustomName()) {
            sorcerer.setCustomName(evoker.getCustomName());
            sorcerer.setCustomNameVisible(evoker.isCustomNameVisible());
        }
        if (evoker.isPersistenceRequired()) {
            sorcerer.setPersistenceRequired();
        }
        Raid raid = evoker.getCurrentRaid();
        int wave = evoker.getWave();
        evoker.discard();
        level.addFreshEntity(sorcerer);
        if (raid != null) {
            raid.joinRaid(wave, sorcerer, null, false);
        }
        if (mansion) {
            study(level, sorcerer.blockPosition());
        }
    }

    /**
     * A corner to study in, around {@code at}: a lectern turned to it and up to two shelves of books, each on a free
     * place beside it (air, over a firm floor). Nothing is put where something stands.
     */
    static void study(ServerLevel level, BlockPos at) {
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        java.util.Collections.shuffle(sides, new java.util.Random(level.getRandom().nextLong()));
        boolean lectern = false;
        int shelves = 0;
        for (Direction side : sides) {
            BlockPos place = at.relative(side);
            if (!level.getBlockState(place).isAir() || !level.getBlockState(place.below()).isFaceSturdy(level,
                    place.below(), Direction.UP)) {
                continue;
            }
            if (!lectern) {
                BlockState state = Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, side.getOpposite());
                level.setBlock(place, state, 3);
                lectern = true;
            } else if (shelves < 2) {
                level.setBlock(place, Blocks.BOOKSHELF.defaultBlockState(), 3);
                shelves++;
            }
        }
    }
}
