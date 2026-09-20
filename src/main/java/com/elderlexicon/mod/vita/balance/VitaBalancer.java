package com.elderlexicon.mod.vita.balance;

/**
 * Applies one-pass damped reactions around the Vita cycle.
 */
public final class VitaBalancer {

    private static final float TARGET_TOTAL = 100.0f;
    private static final float DIRECT_NEIGHBOR_FACTOR = 0.6f;
    private static final float SECOND_NEIGHBOR_FACTOR = 0.3f;
    private static final float TERTIARY_NEIGHBOR_FACTOR = 0.1f;
    private static final float DEADZONE = 0.5f;
    private static final float NORMALIZATION_EPSILON = 0.01f;

    private VitaBalancer() {
    }

    public static void applyVitaChange(VitaStatus vita, Element changedElement, float delta) {
        if (vita == null || changedElement == null || delta == 0.0f) {
            return;
        }

        applyDampedDelta(vita, changedElement.next(), -delta * DIRECT_NEIGHBOR_FACTOR);
        applyDampedDelta(vita, changedElement.secondNext(), delta * SECOND_NEIGHBOR_FACTOR);
        applyDampedDelta(vita, changedElement.thirdNext(), -delta * TERTIARY_NEIGHBOR_FACTOR);

        normalize(vita);
    }

    private static void applyDampedDelta(VitaStatus vita, Element element, float rawDelta) {
        if (Math.abs(rawDelta) < DEADZONE) {
            return;
        }
        vita.adjust(element, rawDelta);
    }

    private static void normalize(VitaStatus vita) {
        float sum = vita.sum();
        if (sum <= 1.0e-4f) {
            return;
        }
        if (Math.abs(sum - TARGET_TOTAL) > NORMALIZATION_EPSILON) {
            float factor = TARGET_TOTAL / sum;
            vita.scale(factor);
        }
    }
}
