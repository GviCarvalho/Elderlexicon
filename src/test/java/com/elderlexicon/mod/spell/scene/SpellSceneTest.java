package com.elderlexicon.mod.spell.scene;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpellSceneTest {

    private static final Emission.Point ORIGIN = new Emission.Point(0, 0, 0);

    private static Emission beam(int spell, VitaElement element, Emission.Point origin, double dx, double dy, double dz,
                                 long start, int life) {
        return Emission.beam(spell, element.runeId(), ElementProperties.forElement(element), 10.0D,
                origin, dx, dy, dz, 8.0D, 0.5D, start, life);
    }

    @Test
    void emissionIsActiveOnlyDuringItsLifetime() {
        Emission emission = beam(0, VitaElement.AQUA, ORIGIN, 1, 0, 0, 100, 5);

        assertFalse(emission.activeAt(99));
        assertTrue(emission.activeAt(100));
        assertTrue(emission.activeAt(104));
        assertFalse(emission.activeAt(105));
    }

    @Test
    void beamSamplesCoverItsLength() {
        Emission emission = beam(0, VitaElement.AURA, ORIGIN, 0, 0, 2, 0, 5);

        Emission.Point last = emission.samples().get(emission.samples().size() - 1);
        assertEquals(0.0D, last.x(), 1.0E-9);
        assertEquals(8.0D, last.z(), 1.0E-9);
    }

    @Test
    void parallelBeamsFromTheSameOriginOverlapButOnlyAcrossSpells() {
        SpellScene scene = new SpellScene();
        int water = scene.registerSpell();
        int wind = scene.registerSpell();
        scene.add(beam(water, VitaElement.AQUA, ORIGIN, 1, 0, 0, 10, 5));
        scene.add(beam(wind, VitaElement.AURA, ORIGIN, 1, 0, 0, 10, 5));

        assertEquals(1, scene.overlapsAt(12).size());
    }

    @Test
    void emissionsOfTheSameSpellDoNotOverlapEachOther() {
        SpellScene scene = new SpellScene();
        int only = scene.registerSpell();
        scene.add(beam(only, VitaElement.AQUA, ORIGIN, 1, 0, 0, 10, 5));
        scene.add(beam(only, VitaElement.AQUA, ORIGIN, 1, 0, 0, 10, 5));

        assertTrue(scene.overlapsAt(12).isEmpty());
    }

    @Test
    void beamsPointingAwayDoNotOverlap() {
        SpellScene scene = new SpellScene();
        scene.add(beam(scene.registerSpell(), VitaElement.AQUA, ORIGIN, 1, 0, 0, 10, 5));
        scene.add(beam(scene.registerSpell(), VitaElement.AURA, new Emission.Point(0, 0, 5), 0, 0, 1, 10, 5));
        // second beam starts 5 blocks away on z and moves further away; first stays on the x axis
        assertTrue(scene.overlapsAt(12).isEmpty());
    }

    @Test
    void emissionsOutsideTheirLifetimeDoNotOverlap() {
        SpellScene scene = new SpellScene();
        scene.add(beam(scene.registerSpell(), VitaElement.AQUA, ORIGIN, 1, 0, 0, 10, 5));
        scene.add(beam(scene.registerSpell(), VitaElement.AURA, ORIGIN, 1, 0, 0, 20, 5));

        assertTrue(scene.overlapsAt(12).isEmpty());
        assertTrue(scene.overlapsAt(22).isEmpty());
    }

    @Test
    void pointOnABeamOverlapsIt() {
        SpellScene scene = new SpellScene();
        scene.add(beam(scene.registerSpell(), VitaElement.AURA, ORIGIN, 1, 0, 0, 10, 20));
        scene.add(Emission.point(scene.registerSpell(), "aqua", ElementProperties.forElement(VitaElement.AQUA),
                10.0D, new Emission.Point(4, 0.3, 0), 0.5D, 10, 20));

        assertEquals(1, scene.overlapsAt(15).size());
    }

    @Test
    void sceneDropsEmissionsBeyondTheCap() {
        SpellScene scene = new SpellScene();
        int spell = scene.registerSpell();
        for (int i = 0; i < SpellScene.MAX_EMISSIONS; i++) {
            assertTrue(scene.add(beam(spell, VitaElement.IGNI, ORIGIN, 1, 0, 0, i, 5)));
        }
        assertFalse(scene.add(beam(spell, VitaElement.IGNI, ORIGIN, 1, 0, 0, 999, 5)));
        assertEquals(SpellScene.MAX_EMISSIONS, scene.emissions().size());
    }

    @Test
    void elementProfilesFollowTheirCharacter() {
        assertTrue(ElementProperties.forElement(VitaElement.IGNI).heat() > ElementProperties.forElement(VitaElement.AQUA).heat());
        assertTrue(ElementProperties.forElement(VitaElement.AQUA).moisture() > ElementProperties.forElement(VitaElement.FIRMO).moisture());
        assertTrue(ElementProperties.forElement(VitaElement.AURA).momentum() > ElementProperties.forElement(VitaElement.FIRMO).momentum());
        assertTrue(ElementProperties.forElement(VitaElement.FIRMO).mass() > ElementProperties.forElement(VitaElement.AURA).mass());
    }
}
