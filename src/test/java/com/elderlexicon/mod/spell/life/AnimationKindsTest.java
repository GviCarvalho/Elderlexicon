package com.elderlexicon.mod.spell.life;

import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The kinds the animation binds are the beings of the table (docs/particulas-design.md, section 6). */
class AnimationKindsTest {

    @AfterEach
    void forgetTheAddons() {
        Materials.reset();
    }

    /** The table in force with only these kinds of being, for what the choice between them shows. */
    static void onlyKinds(String... kept) {
        java.util.Set<String> keep = java.util.Set.of(kept);
        java.util.List<String> others = Materials.get().beings().stream()
                .map(com.elderlexicon.mod.magic.matter.Being::id).filter(id -> !keep.contains(id)).toList();
        Materials.extend(table -> others.forEach(table::remove));
    }

    @Test
    void aBeingAnAddonBringsCanBeBorn() {
        Materials.extend(table -> table.read(new StringReader("""
                {"beings": {"wolf": {"name": "lobo", "recipe": {"firmo": 0.12, "aqua": 0.40, "aura": 0.40, "igni": 0.08},
                                     "entity": "minecraft:wolf"}}}
                """)));
        Animation.Being wolf = Animation.quicken(Map.of(VitaElement.FIRMO, 12.0D, VitaElement.AQUA, 40.0D,
                VitaElement.AURA, 40.0D, VitaElement.IGNI, 8.0D), Animation.ANCHOR).orElseThrow();
        assertEquals("wolf", wolf.kind().id());
        assertEquals("minecraft:wolf", wolf.kind().entity());
        assertTrue(wolf.sound());
    }

    @Test
    void aBodyAsNearTwoKindsIsTheOneTheTableListsFirst() {
        // 8 firmo, 45 aqua, 44 aura and 53 igni are 94/150 from a pig and from a blaze.
        onlyKinds("pig", "blaze");
        Animation.Being tie = Animation.quicken(Map.of(VitaElement.FIRMO, 8.0D, VitaElement.AQUA, 45.0D,
                VitaElement.AURA, 44.0D, VitaElement.IGNI, 53.0D), Animation.ANCHOR).orElseThrow();
        assertEquals("pig", tie.kind().id(), "the pig comes first in the table");
    }

    @Test
    void ingredientsBroughtBesideTheAnchorAreTheBody() {
        // A quarter of flesh, a little more than a third of air and two fifths of water: a person's proportion.
        Particles flesh = Materials.get().substance("flesh").orElseThrow().code(25L * Particles.PER_UMU);
        Particles body = flesh.plus(Particles.of(VitaElement.AURA, Particles.ofUmu(35.5D)))
                .plus(Particles.of(VitaElement.AQUA, Particles.ofUmu(40.0D)));
        Animation.Being being = Animation.quicken(body, Animation.ANCHOR).orElseThrow();
        assertEquals("homunculus", being.kind().id());
        assertTrue(being.sound());
        assertEquals(100.5D, being.body(), 1.0E-9, "every particle brought is body");
        assertEquals(20.1D, being.health(), 1.0E-9);
    }

    @Test
    void whatTheBodyOwesIsNoPartOfIt() {
        Particles person = new Particles(Particles.ofUmu(5.0D), Particles.ofUmu(55.0D), Particles.ofUmu(38.0D),
                Particles.ofUmu(2.0D));
        Particles owing = person.minus(Particles.of(VitaElement.FIRMO, Particles.ofUmu(20.0D)));
        assertTrue(owing.owes());
        Animation.Being being = Animation.quicken(owing, Animation.ANCHOR).orElseThrow();
        assertEquals(95.0D, being.body(), 1.0E-9, "only what is held is body");
        assertEquals(-0.05D, being.deviation().get(VitaElement.FIRMO), 1.0E-9, "and the earth it owes is simply lacking");
    }
}
