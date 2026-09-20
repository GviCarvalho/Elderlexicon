package com.elderlexicon.mod.spelling.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpellingRepertoireProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    private final SpellingRepertoire repertoire = new SpellingRepertoire();
    private final LazyOptional<SpellingRepertoire> optional = LazyOptional.of(() -> repertoire);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
        if (cap == SpellingRepertoireCapability.CAPABILITY) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return repertoire.save();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        repertoire.load(nbt);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
