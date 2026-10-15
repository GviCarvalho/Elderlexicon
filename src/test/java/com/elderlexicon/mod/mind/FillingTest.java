package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a caster draws comes into its own body, as a body takes it (docs/feiticeiro-design.md, 3.19): lacking, it
 * heals; full, what comes in pushes the others out, and that hurts it.
 */
class FillingTest {

    private static Option option(String... words) {
        return Option.of(PhraseCatalog.imagine(List.of(words)).orElseThrow());
    }

    private static Mind.Scene scene(double distance, float share) {
        return new Mind.Scene(null, null, distance, share, 1000.0D, mark -> 0.0D, false, element -> true);
    }

    @Test
    void aWholeBodyFilledPastFullLosesLife() {
        double changed = Mind.person(0.0D).lifeChanged(Map.of(VitaElement.FIRMO, 10.0D));
        assertTrue(changed < -0.5D, "filling a whole body with earth pushes the rest out: " + changed);
    }

    @Test
    void aBodyLackingAllAlikeIsNotHurtByWhatItLacks() {
        // The life follows the element most lacking: water alone does not heal it, nor hurt it.
        assertEquals(0.0D, Mind.person(0.3D).lifeChanged(Map.of(VitaElement.AQUA, 10.0D)), 1.0E-9D);
    }

    @Test
    void fireFarPastItsFullIsOverfull() {
        assertTrue(Mind.person(0.0D).overfull(VitaElement.IGNI, 10.0D) > 0.5D,
                "fire is a small share of a body: ten UMU of it is far past its full");
        assertEquals(0.0D, Mind.person(0.5D).overfull(VitaElement.AQUA, 10.0D), 1.0E-9D,
                "water is most of a body: ten UMU into one half empty is not past its full");
    }

    @Test
    void drawingWholeCostsItsOwnLife() {
        Option earth = option("firmo", "quantum", "-10", "vocant");
        Mind.Choice whole = Mind.evaluate(scene(6.0D, 1.0F), Personality.AGGRESSIVE, earth, Mind.person(0.0D))
                .orElse(null);
        Mind.Choice wounded = Mind.evaluate(scene(6.0D, 0.5F), Personality.AGGRESSIVE, earth, Mind.person(0.5D))
                .orElseThrow();
        assertTrue(whole == null || whole.score() < wounded.score(),
                "drawing earth whole is worth less than wounded: it hurts itself");
        assertTrue(whole == null || whole.healed() < 0.0D, "and it knows it: " + (whole == null ? "-" : whole.healed()));
    }
}
