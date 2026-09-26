package com.elderlexicon.mod.spelling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players this client saw thrown by a spell, still in the air. The way each one flies is read from how it moved since
 * the last tick (the same for the mage's own body and for others), and a flight ends when it lands, falls into water,
 * or after a long while.
 */
public final class ClientLaunches {

    /** A thrown player barely moves the first ticks after the throw, still on the ground; its landing is not read then. */
    private static final int TAKE_OFF_TICKS = 3;
    private static final int LONGEST_FLIGHT_TICKS = 20 * 20;
    private static final double STILL = 1.0E-3D;

    private record Flight(long since, Vec3 heading) {
    }

    private static final Map<Integer, Flight> FLIGHTS = new ConcurrentHashMap<>();

    private ClientLaunches() {
    }

    public static void start(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        FLIGHTS.put(entityId, new Flight(now, new Vec3(0.0D, 1.0D, 0.0D)));
    }

    /** The way {@code entity} flies now, if it is still flying from a throw. */
    static Optional<Vec3> heading(Entity entity) {
        Flight flight = FLIGHTS.get(entity.getId());
        if (flight == null || entity.level() == null) {
            return Optional.empty();
        }
        long airborne = entity.level().getGameTime() - flight.since();
        boolean landed = airborne > TAKE_OFF_TICKS && (entity.onGround() || entity.isInWater());
        if (landed || airborne > LONGEST_FLIGHT_TICKS || entity.isRemoved()) {
            FLIGHTS.remove(entity.getId());
            return Optional.empty();
        }
        Vec3 moved = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        if (moved.lengthSqr() > STILL) {
            FLIGHTS.put(entity.getId(), new Flight(flight.since(), moved));
            return Optional.of(moved);
        }
        return Optional.of(flight.heading());
    }

    public static void clear() {
        FLIGHTS.clear();
    }
}
