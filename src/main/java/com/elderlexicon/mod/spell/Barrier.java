package com.elderlexicon.mod.spell;

/**
 * The zone an impediunt keeps its element out of (docs/impediunt-design.md): an upright cylinder standing on the ground
 * where it is centred, three blocks tall, whose size the UMU put into it buys. The ground itself is never part of it,
 * so the floor stays and only what stands on it is moved.
 */
public final class Barrier {

    /** Ten UMU, what a function spends by default (book 4.3.2), keep three blocks clear around the centre. */
    public static final double DEFAULT_UMU = 10.0D;
    public static final double DEFAULT_RADIUS = 3.0D;
    public static final double MAX_RADIUS = 32.0D;
    /** How tall the zone stands, in blocks, from the level of the centre's feet. */
    public static final int HEIGHT = 3;

    private Barrier() {
    }

    /**
     * The radius {@code umu} keeps clear. The UMU buy area, so twice the radius costs four times as much: forty UMU
     * clear six blocks around, a hundred about nine and a half.
     */
    public static double radius(double umu) {
        if (umu <= 0.0D) {
            return 0.0D;
        }
        return Math.min(MAX_RADIUS, DEFAULT_RADIUS * Math.sqrt(umu / DEFAULT_UMU));
    }

    /** Whether the block at {@code x, y, z} lies in the zone of {@code radius} centred on the feet at {@code cx, cy, cz}. */
    public static boolean inside(double cx, double cy, double cz, int x, int y, int z, double radius) {
        int floor = (int) Math.floor(cy);
        if (y < floor || y >= floor + HEIGHT) {
            return false;
        }
        double dx = x + 0.5D - cx;
        double dz = z + 0.5D - cz;
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * Where something at {@code x, z} is pushed to: just past the edge, straight out from the centre. What sits right
     * on the centre goes out toward the east.
     */
    public static double[] edge(double cx, double cz, double x, double z, double radius) {
        double dx = x - cx;
        double dz = z - cz;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-6D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }
        double out = radius + 1.0D;
        return new double[]{cx + dx / length * out, cz + dz / length * out};
    }
}
