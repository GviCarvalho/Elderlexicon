package com.elderlexicon.mod.ligabis;

/** A displacement or position in blocks. Kept here so the core does not depend on the game's own vector. */
public record Vec(double x, double y, double z) {

    public static final Vec ZERO = new Vec(0.0D, 0.0D, 0.0D);

    public Vec plus(Vec other) {
        return new Vec(x + other.x, y + other.y, z + other.z);
    }

    public Vec minus(Vec other) {
        return new Vec(x - other.x, y - other.y, z - other.z);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
