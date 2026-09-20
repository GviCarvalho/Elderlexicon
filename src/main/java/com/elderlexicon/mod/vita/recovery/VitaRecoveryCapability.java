package com.elderlexicon.mod.vita.recovery;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

public final class VitaRecoveryCapability {

    public static final Capability<VitaRecoveryTracker> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = new ResourceLocation(ExampleMod.MODID, "recovery_tracker");

    private VitaRecoveryCapability() {}

    public static LazyOptional<VitaRecoveryTracker> get(Player player) {
        return player.getCapability(CAPABILITY);
    }
}
