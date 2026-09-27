package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.Mana;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Condensed Vis released (docs/condensacao-design.md): pure energy, which for now is only light. Where it lands there is
 * a flash, and the place stays lit for a while, longer the more Vis there was.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class VisSpots {

    private record Glow(ServerLevel level, BlockPos pos, long untilTick) {
    }

    private static final List<Glow> GLOWS = new ArrayList<>();

    private VisSpots() {
    }

    /** {@code umu} of Vis released at {@code at}: a flash, and light that lingers there. */
    static void light(ServerLevel level, Vec3 at, double umu) {
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, (int) Math.min(120.0D, 20.0D + umu), 0.6D, 0.6D, 0.6D,
                0.15D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 1.6F);
        BlockPos pos = BlockPos.containing(at);
        for (BlockPos spot : new BlockPos[]{pos, pos.above(), pos.below()}) {
            if (level.isLoaded(spot) && level.getBlockState(spot).isAir()) {
                level.setBlock(spot, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), Block.UPDATE_ALL);
                GLOWS.add(new Glow(level, spot, level.getGameTime() + Math.round(Mana.lightSeconds(umu) * 20.0D)));
                return;
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || GLOWS.isEmpty()) {
            return;
        }
        for (Glow glow : new ArrayList<>(GLOWS)) {
            ServerLevel level = glow.level();
            if (level.getGameTime() < glow.untilTick()) {
                if (level.getGameTime() % 10 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, glow.pos().getX() + 0.5D, glow.pos().getY() + 0.5D,
                            glow.pos().getZ() + 0.5D, 2, 0.3D, 0.3D, 0.3D, 0.01D);
                }
                continue;
            }
            GLOWS.remove(glow);
            if (level.getBlockState(glow.pos()).is(Blocks.LIGHT)) {
                level.setBlock(glow.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
