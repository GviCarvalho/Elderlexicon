package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Immutable description of a spell step produced by the action engine.
 */
public final class SpellAction {

    /** Metadata key: the mark whose carriers the function acts on ({@code m1 vocant}). */
    public static final String SUBJECT_MARK = "subjectMark";
    /** Metadata key: the mark a targeted function points at ({@code m1 transvocatio m2}). */
    public static final String TARGET_MARK = "targetMark";
    /** Metadata key: the {@link SpellPlace} written with {@code ubis}. */
    public static final String PLACE = "place";
    /**
     * Metadata key: the UMU written with {@code quantum} right before the function, its potency
     * ({@code igni quantum 20 iactare}: the iactare throws 20 UMU).
     */
    public static final String QUANTITY = "quantity";
    /**
     * Metadata key: the UMU written with {@code quantum} right before the source, the value of the source itself
     * ({@code quantum 2 firmo surgit}: earth worth 2 UMU). A filter acts on what is written after it.
     */
    public static final String SOURCE_QUANTITY = "sourceQuantity";
    /**
     * Metadata key: the function works only with the image of its source, its light and not its matter, written with
     * surgit right before it ({@code igni surgit vocant}: fire that is seen and does not burn).
     */
    public static final String IMAGE = "image";
    /** Metadata key: the mage's sight is bound to its subject, written {@code surgit m1 ligabis}. */
    public static final String SIGHT_BOND = "sightBond";
    /** Metadata key: how much of its subject is seen, 0 to 10, written {@code surgit m1 quantum 0}. */
    public static final String VISIBILITY = "visibility";
    /** Metadata key: the seconds written with {@code chronos} ({@code igni chronos 5 iactare}). */
    public static final String SECONDS = "seconds";
    /** A bare quantum after exsugat ({@code igni exsugat quantum iactare}): all that was captured (book 4.3.2). */
    public static final String QUANTITY_ALL = "quantityAll";
    /** How intense the captured source is when this function releases it (docs/condensacao-design.md). */
    public static final String INTENSITY = "intensity";
    /** How much coal the captured earth held, when it was nearly all coal: condensed hard, it turns to diamond. */
    public static final String CARBON = "carbon";
    /** How many ticks a condensation gathers before this function releases it. */
    public static final String CHARGE = "charge";
    /** The orb a condensation is gathered into, by entity id, for the function to release. */
    public static final String ORB = "orb";

    private final String runeId;
    private final SpellActionType type;
    private final VitaElement element;
    private final List<String> shapes;
    private final String targetRuneId;
    private final double suggestedCost;
    private final Map<String, Object> metadata;

    private SpellAction(Builder builder) {
        this.runeId = builder.runeId;
        this.type = builder.type;
        this.element = builder.element;
        this.shapes = List.copyOf(builder.shapes);
        this.targetRuneId = builder.targetRuneId;
        this.suggestedCost = builder.suggestedCost;
        this.metadata = Map.copyOf(builder.metadata);
    }

    public String runeId() {
        return runeId;
    }

    public SpellActionType type() {
        return type;
    }

    public VitaElement element() {
        return element;
    }

    public List<String> shapes() {
        return shapes;
    }

    public String targetRuneId() {
        return targetRuneId;
    }

    public double suggestedCost() {
        return suggestedCost;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Optional<String> subjectMark() {
        return metadata.get(SUBJECT_MARK) instanceof String mark ? Optional.of(mark) : Optional.empty();
    }

    public Optional<String> targetMark() {
        return metadata.get(TARGET_MARK) instanceof String mark ? Optional.of(mark) : Optional.empty();
    }

    /**
     * The UMU written with quantum, wherever it stands: the function's potency or, without one, the source's value.
     * Functions that do not yet tell the two apart read this one, so both writings keep working for them.
     */
    public OptionalDouble quantity() {
        OptionalDouble potency = potency();
        return potency.isPresent() ? potency : sourceValue();
    }

    /** Whether a bare quantum asks for everything the exsugat before it captures ({@code igni exsugat quantum iactare}). */
    public boolean quantityAll() {
        return Boolean.TRUE.equals(metadata.get(QUANTITY_ALL));
    }

    /** How intense the source this function releases is: 1 is common, more only when captured and condensed. */
    public double intensity() {
        return metadata.get(INTENSITY) instanceof Double value ? value : 1.0D;
    }

    /** The coal in the condensed earth this function releases, when it was nearly all coal; 0 otherwise. */
    public int carbon() {
        return metadata.get(CARBON) instanceof Integer coal ? coal : 0;
    }

    /** How many ticks what this function releases is gathered first (a condensation); 0 releases it at once. */
    public int charge() {
        return metadata.get(CHARGE) instanceof Integer ticks ? ticks : 0;
    }

    /** The entity id of the orb this function releases, or -1 when there is none. */
    public int orb() {
        return metadata.get(ORB) instanceof Integer id ? id : -1;
    }

    /** Whether the function releases what it spends in one instant ({@code chronos 0}): condensed, when captured. */
    public boolean atOnce() {
        return seconds().isPresent() && seconds().getAsDouble() <= 1.0E-4D;
    }

    /** Whether this is a bond of sight to its subject ({@code surgit m1 ligabis}). */
    public boolean sightBond() {
        return Boolean.TRUE.equals(metadata.get(SIGHT_BOND));
    }

    /** Whether the function works only with the image of its source ({@code igni surgit vocant}). */
    public boolean image() {
        return Boolean.TRUE.equals(metadata.get(IMAGE));
    }

    /** For {@code surgit m1 quantum N}: how much of m1 is to be seen, 0 (unseen) to 10. */
    public OptionalDouble visibility() {
        return metadata.get(VISIBILITY) instanceof Double level ? OptionalDouble.of(level) : OptionalDouble.empty();
    }

    /** The quantum written right before the function: how much UMU the function spends. */
    public OptionalDouble potency() {
        return metadata.get(QUANTITY) instanceof Double quantity ? OptionalDouble.of(quantity) : OptionalDouble.empty();
    }

    /** The quantum written right before the source: how much UMU the source itself holds. */
    public OptionalDouble sourceValue() {
        return metadata.get(SOURCE_QUANTITY) instanceof Double value ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    public OptionalDouble seconds() {
        return metadata.get(SECONDS) instanceof Double seconds ? OptionalDouble.of(seconds) : OptionalDouble.empty();
    }

    public Optional<SpellPlace> place() {
        return metadata.get(PLACE) instanceof SpellPlace place ? Optional.of(place) : Optional.empty();
    }

    public Builder toBuilder() {
        Builder builder = new Builder(runeId, type);
        builder.element(element);
        builder.shapes(shapes);
        builder.targetRuneId(targetRuneId);
        builder.suggestedCost(suggestedCost);
        builder.metadata(metadata);
        return builder;
    }

    public static Builder builder(String runeId, SpellActionType type) {
        return new Builder(runeId, type);
    }

    public static final class Builder {
        private final String runeId;
        private final SpellActionType type;
        private VitaElement element;
        private final List<String> shapes = new ArrayList<>();
        private String targetRuneId;
        private double suggestedCost;
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        private Builder(String runeId, SpellActionType type) {
            this.runeId = sanitize(runeId);
            this.type = Objects.requireNonNull(type, "type");
        }

        public Builder element(VitaElement element) {
            this.element = element;
            return this;
        }

        public Builder shapes(List<String> shapes) {
            this.shapes.clear();
            if (shapes != null) {
                shapes.stream()
                        .map(Builder::sanitize)
                        .filter(s -> s != null && !s.isEmpty())
                        .forEach(this.shapes::add);
            }
            return this;
        }

        public Builder addShape(String shape) {
            String sanitized = sanitize(shape);
            if (sanitized != null && !sanitized.isEmpty()) {
                this.shapes.add(sanitized);
            }
            return this;
        }

        public Builder targetRuneId(String targetRuneId) {
            this.targetRuneId = sanitize(targetRuneId);
            return this;
        }

        public Builder suggestedCost(double suggestedCost) {
            this.suggestedCost = Math.max(0.0D, suggestedCost);
            return this;
        }

        public Builder metadata(Map<String, ?> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                metadata.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        this.metadata.put(key, value);
                    }
                });
            }
            return this;
        }

        public Builder putMetadata(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public SpellAction build() {
            return new SpellAction(this);
        }

        private static String sanitize(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    @Override
    public String toString() {
        return "SpellAction{" +
                "runeId='" + runeId + '\'' +
                ", type=" + type +
                ", element=" + element +
                ", shapes=" + shapes +
                ", targetRuneId='" + targetRuneId + '\'' +
                ", suggestedCost=" + suggestedCost +
                ", metadata=" + metadata +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SpellAction other)) {
            return false;
        }
        return Objects.equals(runeId, other.runeId)
                && type == other.type
                && element == other.element
                && Objects.equals(shapes, other.shapes)
                && Objects.equals(targetRuneId, other.targetRuneId)
                && Double.compare(other.suggestedCost, suggestedCost) == 0
                && Objects.equals(metadata, other.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runeId, type, element, shapes, targetRuneId, suggestedCost, metadata);
    }
}
