package com.elderlexicon.mod.spelling.data;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Capability hook for attaching a {@link SpellingRepertoire} to players.
 */
public final class SpellingRepertoireCapability {

    public static final Capability<SpellingRepertoire> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "spelling_repertoire");

    private SpellingRepertoireCapability() {
    }

    public static LazyOptional<SpellingRepertoire> get(Player player) {
        return player.getCapability(CAPABILITY);
    }
}
