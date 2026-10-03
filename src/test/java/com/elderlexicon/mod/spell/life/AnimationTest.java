package com.elderlexicon.mod.spell.life;

import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTest {

    @AfterEach
    void theWholeTableAgain() {
        Materials.reset();
    }

    private static Map<VitaElement, Double> body(double aqua, double aura, double igni, double firmo) {
        return Map.of(VitaElement.AQUA, aqua, VitaElement.AURA, aura, VitaElement.IGNI, igni, VitaElement.FIRMO, firmo);
    }

    @Test
    void theExactBodyOfAPersonWithItsAnchorIsASoundHomunculus() {
        Animation.Being being = Animation.quicken(body(55, 38, 2, 5), 100).orElseThrow();
        assertEquals("homunculus", being.kind().id());
        assertEquals(20.0D, being.health(), 1.0E-9, "100 UMU of body is 20 HP");
        assertTrue(being.sound());
        assertEquals(0.0D, being.leftoverVis(), 1.0E-9);
    }

    @Test
    void withoutTheAnchorNothingBinds() {
        assertTrue(Animation.quicken(body(55, 38, 2, 5), 99).isEmpty(), "short of the hundred");
        assertTrue(Animation.quicken(body(1, 1, 0, 0), 100).isEmpty(), "too little body");
    }

    @Test
    void theNearestKindIsBornAtTheSizeOfItsBody() {
        Animation.Being blaze = Animation.quicken(body(1, 19, 29, 1), 100).orElseThrow();
        assertEquals("blaze", blaze.kind().id());
        assertEquals(10.0D, blaze.health(), 1.0E-9);
        assertEquals("iron_golem", Animation.quicken(body(10, 20, 10, 160), 120).orElseThrow().kind().id());
        assertEquals(20.0D, Animation.quicken(body(10, 20, 10, 160), 120).orElseThrow().leftoverVis(), 1.0E-9);
    }

    @Test
    void anImpreciseBodyIsBornMalformedWhereItIsOff() {
        // Between a person and a pig: the kinds near a person (a villager, a wolf) would take a body this far off.
        AnimationKindsTest.onlyKinds("homunculus", "pig");
        Animation.Being being = Animation.quicken(body(55, 35, 6, 4), 100).orElseThrow();
        assertEquals("homunculus", being.kind().id());
        assertFalse(being.sound());
        assertTrue(being.deviation().get(VitaElement.IGNI) > 0.03D, "too much fire");
        assertTrue(being.deviation().get(VitaElement.AURA) < -0.02D, "too little air");
        // Far enough off, it is another kind altogether: a person with fire for air is nearer a pig.
        assertEquals("pig", Animation.quicken(body(55, 30, 10, 5), 100).orElseThrow().kind().id());
    }
}
