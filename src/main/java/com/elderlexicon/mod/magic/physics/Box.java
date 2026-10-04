package com.elderlexicon.mod.magic.physics;

import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.State;

/**
 * The laboratory of the four drives (docs/particulas-design.md, section 8.7): a closed box of blocks where the drives
 * act ({@link Field}) and nothing else. What comes out of them (vapour, lava, a fire that eats the air of a closed
 * room, charcoal) is what the tests look at. Pure: no Minecraft. The walls of the box are closed.
 */
public final class Box {

    private final int width;
    private final int height;
    private final int depth;
    private final Field field;
    private final int[] blocks;

    public Box(int width, int height, int depth) {
        this(width, height, depth, 0L);
    }

    /** A box whose chances fall as {@code seed} says: the same seed, the same world. */
    public Box(int width, int height, int depth, long seed) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("a box needs room");
        }
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.field = new Field(seed);
        this.blocks = new int[width * height * depth];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    blocks[(y * depth + z) * width + x] = field.add();
                }
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    int i = index(x, y, z);
                    if (x + 1 < width) {
                        field.link(i, Field.EAST, index(x + 1, y, z));
                    }
                    if (y + 1 < height) {
                        field.link(i, Field.UP, index(x, y + 1, z));
                    }
                    if (z + 1 < depth) {
                        field.link(i, Field.SOUTH, index(x, y, z + 1));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ setting up and reading

    /** Puts matter in a block, held, at rest. What cannot hold together (air, fire with no mass) goes on the first step. */
    public void put(int x, int y, int z, Particles matter) {
        field.put(index(x, y, z), matter);
    }

    /** Puts gas in a block: the earth as dust, the water as mist, the air, and the fire as free agitation. */
    public void blow(int x, int y, int z, Particles gas) {
        field.blow(index(x, y, z), gas);
    }

    /** Agitation brought into a block from outside (a spell); negative, taken out of it, down to absolute zero. */
    public void heat(int x, int y, int z, long igni) {
        field.heat(index(x, y, z), igni);
    }

    /** The matter a block holds, the fire held in it among it. */
    public Particles held(int x, int y, int z) {
        return field.held(index(x, y, z));
    }

    /** How much of the fire a block holds its mass has gripped into char. */
    public long charred(int x, int y, int z) {
        return field.charred(index(x, y, z));
    }

    /** What flies in a block: the smoke as air, the fire the vapour holds and the fuel the dust carries as fire. */
    public Particles airborne(int x, int y, int z) {
        return field.airborne(index(x, y, z));
    }

    /** The smoke in a block: air the fire took. */
    public long smoke(int x, int y, int z) {
        return field.smoke(index(x, y, z));
    }

    /** The free agitation of a block: igni above the world at rest, or owed below it. */
    public long heat(int x, int y, int z) {
        return field.heat(index(x, y, z));
    }

    /** How agitated a block is: 1 at rest, 0 at absolute zero. */
    public double temperature(int x, int y, int z) {
        return field.temperature(index(x, y, z));
    }

    /** The state of the matter a block holds; with none, a gas, or plasma where the agitation beats the expansion. */
    public State state(int x, int y, int z) {
        return field.state(index(x, y, z));
    }

    /** Whether a block is agitated enough to glow (lava, iron red with heat, flame). */
    public boolean glows(int x, int y, int z) {
        return field.glows(index(x, y, z));
    }

    /** Whether a block is flame: gas that glows, short of plasma. */
    public boolean fire(int x, int y, int z) {
        return field.fire(index(x, y, z));
    }

    /** The free agitation that would bring a block, as it is made now, to {@code temperature}. */
    public long agitationFor(int x, int y, int z, double temperature) {
        return field.agitationFor(index(x, y, z), temperature);
    }

    /** Every particle in the box, the free agitation counted as fire: what no step may change. */
    public Particles total() {
        return field.total();
    }

    // ------------------------------------------------------------------ steps

    /**
     * One step of the world: agitation spreads and shines, fire is let go, matter changes, gas evens out and what is
     * light rises.
     */
    public void step() {
        field.step();
    }

    public void steps(int count) {
        for (int step = 0; step < count; step++) {
            field.step();
        }
    }

    private int index(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
            throw new IndexOutOfBoundsException("(" + x + ", " + y + ", " + z + ") is outside the box");
        }
        return blocks[(y * depth + z) * width + x];
    }
}
