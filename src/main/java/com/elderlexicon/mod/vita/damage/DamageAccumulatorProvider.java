package com.elderlexicon.mod.vita.damage;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Capability provider binding DamageAccumulator to player entities.
 */
public final class DamageAccumulatorProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    private final DamageAccumulator accumulator = new DamageAccumulator();
    private final LazyOptional<DamageAccumulator> optional = LazyOptional.of(() -> accumulator);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull net.minecraftforge.common.capabilities.Capability<T> cap,
                                                      @Nullable Direction side) {
        if (cap == DamageAccumulatorCapability.CAPABILITY) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return accumulator.save();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        accumulator.load(nbt);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
