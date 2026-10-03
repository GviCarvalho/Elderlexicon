package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * What matter is made of: the share of each of the four primordial substances in it, named by their aspects (terra is
 * firmo, água aqua, ar aura, fogo igni), summing to one. A composition says what a thing is (its substance is the recipe
 * it matches), never how it is (its state) or how much of it there is (its UMU). Vis is no part of any: it is energy, not
 * matter.
 */
public final class Composition {

    private static final double EPSILON = 1.0E-9D;

    private final Map<VitaElement, Double> shares;

    private Composition(Map<VitaElement, Double> shares) {
        this.shares = shares;
    }

    /**
     * The composition of these amounts of each primordial (any positive measure: UMU, parts), normalized to shares.
     *
     * @throws IllegalArgumentException for a negative amount, for vis, or when there is nothing at all
     */
    public static Composition of(Map<VitaElement, Double> amounts) {
        Objects.requireNonNull(amounts, "amounts");
        double total = 0.0D;
        for (Map.Entry<VitaElement, Double> entry : amounts.entrySet()) {
            if (entry.getKey() == VitaElement.BALANCED) {
                throw new IllegalArgumentException("vis is energy, no part of matter");
            }
            double amount = entry.getValue() == null ? 0.0D : entry.getValue();
            if (amount < 0.0D) {
                throw new IllegalArgumentException("a negative share of " + entry.getKey().runeId());
            }
            total += amount;
        }
        if (total <= EPSILON) {
            throw new IllegalArgumentException("a composition of nothing");
        }
        EnumMap<VitaElement, Double> shares = new EnumMap<>(VitaElement.class);
        for (Map.Entry<VitaElement, Double> entry : amounts.entrySet()) {
            double amount = entry.getValue() == null ? 0.0D : entry.getValue();
            if (amount > EPSILON) {
                shares.put(entry.getKey(), amount / total);
            }
        }
        return new Composition(Collections.unmodifiableMap(shares));
    }

    /**
     * What a source's essence is made of, as matter: its shares of the four primordials, the balance of vis left out
     * (a source of half aqua and half firmo is mud). Empty when there is none of the four in it.
     */
    public static Optional<Composition> ofEssence(Map<VitaElement, Double> essence) {
        if (essence == null) {
            return Optional.empty();
        }
        EnumMap<VitaElement, Double> amounts = new EnumMap<>(VitaElement.class);
        essence.forEach((aspect, share) -> {
            if (aspect != VitaElement.BALANCED && share != null && share > EPSILON) {
                amounts.put(aspect, share);
            }
        });
        return amounts.isEmpty() ? Optional.empty() : Optional.of(of(amounts));
    }

    /** A primordial substance alone: all of one aspect ({@code firmo} is terra). */
    public static Composition pure(VitaElement aspect) {
        return of(Map.of(aspect, 1.0D));
    }

    /** The share of one primordial in it, 0 to 1. */
    public double share(VitaElement aspect) {
        return shares.getOrDefault(aspect, 0.0D);
    }

    /** Every primordial it holds, with its share. */
    public Map<VitaElement, Double> shares() {
        return shares;
    }

    /** How much of each primordial {@code umu} of it holds. */
    public Map<VitaElement, Double> split(double umu) {
        EnumMap<VitaElement, Double> amounts = new EnumMap<>(VitaElement.class);
        shares.forEach((aspect, share) -> amounts.put(aspect, share * umu));
        return amounts;
    }

    /**
     * How far apart two compositions are: the greatest difference between their shares of any primordial. A recipe is
     * matched within {@link MaterialTable#TOLERANCE} of it.
     */
    public double distance(Composition other) {
        double farthest = 0.0D;
        for (VitaElement aspect : VitaElement.values()) {
            if (aspect != VitaElement.BALANCED) {
                farthest = Math.max(farthest, Math.abs(share(aspect) - other.share(aspect)));
            }
        }
        return farthest;
    }

    /** The primordial it is all made of, when it is one alone. */
    public Optional<VitaElement> pureAspect() {
        if (shares.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(shares.keySet().iterator().next());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Composition composition && composition.shares.equals(shares);
    }

    @Override
    public int hashCode() {
        return shares.hashCode();
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ");
        shares.forEach((aspect, share) -> joiner.add(aspect.runeId() + " " + String.format(Locale.ROOT, "%.2f", share)));
        return joiner.toString();
    }
}
