package com.elderlexicon.mod.ligabis;

import java.util.Objects;

/**
 * Stable identity of something that carries a mark (an entity, a block position...). The core never
 * looks inside; adapters decide how to build the key.
 */
public record MemberId(String key) implements Comparable<MemberId> {

    public MemberId {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("A member key cannot be blank");
        }
    }

    @Override
    public int compareTo(MemberId other) {
        return key.compareTo(other.key);
    }
}
