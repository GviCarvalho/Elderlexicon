package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable description of a spell step produced by the action engine.
 */
public final class SpellAction {

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
