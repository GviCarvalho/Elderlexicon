package com.elderlexicon.mod.vita.damage;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Capability handle for per-player {@link DamageAccumulator} instances.
 */
public final class DamageAccumulatorCapability {

    public static final Capability<DamageAccumulator> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });
    public static final ResourceLocation ID = new ResourceLocation(ExampleMod.MODID, "damage_accumulator");

    private DamageAccumulatorCapability() {
    }

    public static LazyOptional<DamageAccumulator> get(Player player) {
        return player.getCapability(CAPABILITY);
    }
}
