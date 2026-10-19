package com.elderlexicon.mod.nahva;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** The realms of Nahvá: how each is laid out, and what it does to the one dreaming in it. */
final class Realms {

    /** The realm of the infinite tower, which a dead mage's spirit may climb to come back. */
    static final String TOWER = "tower";
    /** The mage's own island, their kern, where their spirit wakes when they die, to rest. */
    static final String KERN = "kern";

    /** A realm laid out: where the dreamer appears, and the rift they escape through. */
    record Built(Vec3 spawn, Vec3 rift) {
    }

    private Realms() {
    }

    /** Lays {@code realm} out at {@code origin}; what it keeps while the mage is there goes in {@code state}. */
    static Optional<Built> build(String realm, ServerLevel level, BlockPos origin, CompoundTag state) {
        return switch (realm) {
            case FeverDream.REALM -> Optional.of(FeverDream.begin(level, origin, state));
            case TOWER -> Optional.of(TowerRealm.build(level, origin, state));
            case KERN -> Optional.of(KernRealm.build(level, origin));
            default -> Optional.empty();
        };
    }

    /**
     * Whether the body of the dream flies there: in the realms of runes it does, and on the island, which is the mage's
     * own; in the tower, the climb is the trial.
     */
    static boolean flies(String realm) {
        return !TOWER.equals(realm);
    }

    /** Whether one there is a dead mage's spirit (the island, the tower), not a sleeper dreaming of a rune. */
    static boolean spirit(String realm) {
        return TOWER.equals(realm) || KERN.equals(realm);
    }

    /** Each second in the realm: what its air does to the one in it. */
    static void pulse(String realm, ServerPlayer dreamer, CompoundTag dream) {
        if (TOWER.equals(realm)) {
            TowerRealm.everySecond(dreamer, dream);
            Phantur.everySecond(dreamer, true);
            return;
        }
        if (KERN.equals(realm)) {
            Phantur.everySecond(dreamer, false);
            return;
        }
        if (FeverDream.REALM.equals(realm)) {
            FeverDream.pulse(dreamer, dream);
        }
    }
}
