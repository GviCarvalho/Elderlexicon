package com.elderlexicon.mod.mind;

import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * How a caster weighs what it wants (docs/feiticeiro-design.md, 3.4). Each is born with one.
 *
 * @param strike     how much it wants to hurt
 * @param draw       how much it wants to draw out of a body (more when wounded: it heals by it)
 * @param veil       how much it wants to vanish when hard pressed
 * @param costWeight how much each UMU spent weighs against it
 * @param desperate  below this share of its life it reads what it cannot pay for, and pays in flesh (0: never)
 * @param call       how much it wants to bring its band to the target
 * @param keep       how much it wants to keep itself whole: heal, ward, get away (docs/feiticeiro-design.md, 3.8)
 */
public enum Personality {
    /** Wants to hurt, minds the cost little, pays in flesh early. */
    AGGRESSIVE(1.4D, 0.8D, 0.4D, 0.04D, 0.45F, 1.2D, 0.7D),
    /** Minds the cost, vanishes when pressed, never pays in flesh. */
    CAUTIOUS(0.9D, 1.0D, 1.6D, 0.12D, 0.0F, 0.9D, 1.5D),
    /** Likes to draw out of bodies and to vanish. */
    TRICKSTER(1.0D, 1.5D, 1.2D, 0.08D, 0.3F, 1.0D, 1.1D);

    private final double strike;
    private final double draw;
    private final double veil;
    private final double costWeight;
    private final float desperate;
    private final double call;
    private final double keep;

    Personality(double strike, double draw, double veil, double costWeight, float desperate, double call,
                double keep) {
        this.strike = strike;
        this.draw = draw;
        this.veil = veil;
        this.costWeight = costWeight;
        this.desperate = desperate;
        this.call = call;
        this.keep = keep;
    }

    public double weight(Intent intent) {
        return switch (intent) {
            case STRIKE -> strike;
            case DRAW -> draw;
            case VEIL -> veil;
            case CALL -> call;
            case HEAL, WARD, FLEE -> keep;
            case DOUSE -> 1.5D; // anyone puts out the fire on it
        };
    }

    public double costWeight() {
        return costWeight;
    }

    /** Whether, at {@code healthShare} of its life, it reads what it cannot pay for. */
    public boolean desperate(float healthShare) {
        return healthShare < desperate;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Personality byId(String id) {
        for (Personality personality : values()) {
            if (personality.id().equals(id)) {
                return personality;
            }
        }
        return AGGRESSIVE;
    }

    public static Personality draw(RandomSource random) {
        return values()[random.nextInt(values().length)];
    }
}
