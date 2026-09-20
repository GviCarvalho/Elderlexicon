package com.elderlexicon.mod.vita.recovery;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VitaRecoveryProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    private final VitaRecoveryTracker tracker = new VitaRecoveryTracker();
    private final LazyOptional<VitaRecoveryTracker> optional = LazyOptional.of(() -> tracker);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull net.minecraftforge.common.capabilities.Capability<T> cap,
                                                      @Nullable Direction side) {
        if (cap == VitaRecoveryCapability.CAPABILITY) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return tracker.save();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        tracker.load(nbt);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
