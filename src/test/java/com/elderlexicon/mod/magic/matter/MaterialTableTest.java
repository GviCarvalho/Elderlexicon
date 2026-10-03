package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.SourceSpec;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The mod's substances: they hold together, the book's words name them, and the game reads back as them. */
class MaterialTableTest {

    private static final MaterialTable TABLE = Materials.builtIn();

    @AfterEach
    void forgetTheAddons() {
        Materials.reset();
    }

    @Test
    void eachAspectIsAPrimordialFoundInTheStateItNames() {
        assertEquals("earth", TABLE.primordial(VitaElement.FIRMO).id());
        assertEquals("water", TABLE.primordial(VitaElement.AQUA).id());
        assertEquals("air", TABLE.primordial(VitaElement.AURA).id());
        assertEquals("fire", TABLE.primordial(VitaElement.IGNI).id());
        for (VitaElement aspect : List.of(VitaElement.FIRMO, VitaElement.AQUA, VitaElement.AURA, VitaElement.IGNI)) {
            Substance primordial = TABLE.primordial(aspect);
            assertTrue(primordial.primordial());
            assertEquals(State.of(aspect).orElseThrow(), primordial.nature(), primordial.id());
        }
    }

    @Test
    void theRunesOfTheBookNameTheSubstancesItTeaches() {
        Lexicon lexicon = Lexicons.builtIn();
        Map<String, String> taught = Map.of("firmo", "earth", "aqua", "water", "aura", "air", "igni", "fire");
        taught.forEach((rune, substance) -> assertEquals(Optional.of(substance),
                TABLE.identify(compositionOf(lexicon.source(rune).orElseThrow())).map(Substance::id), rune));
        assertTrue(TABLE.identify(compositionOf(lexicon.source("vis").orElseThrow())).isEmpty(),
                "vis is energy, no substance");
    }

    @Test
    void everyRecipeHasAZoneOfItsOwn() {
        List<Substance> all = new ArrayList<>(TABLE.substances());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                double distance = all.get(i).recipe().distance(all.get(j).recipe());
                assertTrue(distance >= 2.0D * MaterialTable.TOLERANCE - 1.0E-9D,
                        all.get(i).id() + " and " + all.get(j).id() + " are " + distance + " apart");
            }
        }
    }

    @Test
    void aRecipeIsMatchedWithinFivePointsOfEachShare() {
        Substance stone = TABLE.substance("stone").orElseThrow();
        Composition close = Composition.of(Map.of(VitaElement.FIRMO, 0.76D, VitaElement.AQUA, 0.09D,
                VitaElement.AURA, 0.05D, VitaElement.IGNI, 0.10D));
        assertEquals(Optional.of(stone), TABLE.identify(close), "four points off is still stone");
        Composition far = Composition.of(Map.of(VitaElement.FIRMO, 0.74D, VitaElement.AQUA, 0.11D,
                VitaElement.AURA, 0.05D, VitaElement.IGNI, 0.10D));
        assertNotEquals(Optional.of(stone), TABLE.identify(far), "six points off is not");
    }

    @Test
    void aSubstanceShowsAsWhatItIsInEachState() {
        Substance stone = TABLE.substance("stone").orElseThrow();
        assertEquals("minecraft:lava", TABLE.form(stone, State.LIQUID).orElseThrow().id(), "stone melted is lava");
        assertEquals("minecraft:stone", TABLE.form(stone, State.SOLID).orElseThrow().id());
        Substance water = TABLE.primordial(VitaElement.AQUA);
        assertEquals("minecraft:ice", TABLE.form(water, State.SOLID).orElseThrow().id(), "water frozen is ice");
        assertEquals(Form.Kind.PARTICLE, TABLE.form(water, State.GAS).orElseThrow().kind());
    }

    @Test
    void aFloatingStateTheDataIsSilentOnShowsAsTheNearestOne() {
        Substance earth = TABLE.primordial(VitaElement.FIRMO);
        Form floating = TABLE.form(earth, State.GAS).orElseThrow();
        assertEquals(Form.Kind.PARTICLE, floating.kind(), "earth as a gas is no block");
        assertEquals("block:minecraft:dirt", floating.id(), "but the particles of one");
        Substance flesh = TABLE.substance("flesh").orElseThrow();
        assertEquals("item:minecraft:rotten_flesh", TABLE.form(flesh, State.PLASMA).orElseThrow().id());
    }

    @Test
    void aSolidOrLiquidTheDataIsSilentOnHasNoLook() {
        assertTrue(TABLE.form(TABLE.substance("mud").orElseThrow(), State.LIQUID).isEmpty(), "molten mud is formless");
        assertTrue(TABLE.form(TABLE.primordial(VitaElement.FIRMO), State.LIQUID).isEmpty(), "and so is molten earth");
        assertTrue(TABLE.form(TABLE.primordial(VitaElement.IGNI), State.SOLID).isEmpty(), "and solid fire");
    }

    @Test
    void aBlockOfAnythingHoldsAsMuchAsAnyOther() {
        // docs/particulas-design.md: 4096 particles, 16 UMU, whatever it is and whatever state it is in.
        for (Substance substance : TABLE.substances()) {
            substance.forms().values().forEach(forms -> forms.stream()
                    .filter(form -> form.kind() == Form.Kind.BLOCK)
                    .forEach(form -> assertEquals(Particles.BLOCK, form.particles(), form.id())));
        }
        assertEquals(16.0D, TABLE.read(Form.Kind.BLOCK, "minecraft:dirt").orElseThrow().umu(), 1.0E-9);
        assertEquals(16.0D, TABLE.read(Form.Kind.BLOCK, "minecraft:obsidian").orElseThrow().umu(), 1.0E-9,
                "obsidian no more than dirt: what is denser is what is compressed");
        assertEquals(1.0D, TABLE.read(Form.Kind.ITEM, "minecraft:rotten_flesh").orElseThrow().umu(), 1.0E-9,
                "an item is one layer, one UMU");
    }

    @Test
    void theGameReadsBackAsMatter() {
        MaterialTable.Reading lava = TABLE.read(Form.Kind.BLOCK, "minecraft:lava").orElseThrow();
        assertEquals("stone", lava.substance().id());
        assertEquals(State.LIQUID, lava.state());
        assertEquals(16.0D, lava.umu(), 1.0E-9, "a block of stone melts into a block of lava");
        MaterialTable.Reading ice = TABLE.read(Form.Kind.BLOCK, "minecraft:ice").orElseThrow();
        assertEquals(State.SOLID, ice.state());
        assertEquals(16.0D, ice.umu(), 1.0E-9, "a source of water freezes into a block of ice");
        assertEquals(455.0D / 256.0D, TABLE.read(Form.Kind.ITEM, "minecraft:raw_iron").orElseThrow().umu(), 1.0E-9,
                "nine raw irons are a block, but for a particle");
        assertEquals(4.0D, TABLE.read(Form.Kind.ITEM, "minecraft:clay_ball").orElseThrow().umu(), 1.0E-9,
                "four clay balls are a block");
        assertTrue(TABLE.read(Form.Kind.BLOCK, "minecraft:chest").isEmpty(), "what is made is no natural matter");
    }

    @Test
    void anAddonBringsItsSubstancesInTheSameShape() {
        Materials.extend(table -> table.read(new StringReader("""
                {"substances": {"salt": {"name": "sal", "recipe": {"firmo": 0.5, "aqua": 0.3, "aura": 0.2},
                                         "state": "solid",
                                         "forms": {"solid": [{"block": "addon:salt_block"}]}}}}
                """)));
        MaterialTable table = Materials.get();
        Substance salt = table.substance("salt").orElseThrow();
        assertEquals(Optional.of(salt), table.identify(salt.recipe()));
        assertEquals("salt", table.read(Form.Kind.BLOCK, "addon:salt_block").orElseThrow().substance().id());
    }

    @Test
    void matterThatDoesNotHoldTogetherIsRefused() {
        int before = Materials.get().substances().size();
        assertThrows(IllegalStateException.class, () -> Materials.extend(table -> table.read(new StringReader("""
                {"substances": {"pebble": {"recipe": {"firmo": 0.82, "aqua": 0.04, "aura": 0.04, "igni": 0.10}}}}
                """))), "a recipe inside stone's own");
        assertThrows(IllegalStateException.class, () -> Materials.extend(table -> table.read(new StringReader("""
                {"substances": {"brine": {"recipe": {"aqua": 0.5, "firmo": 0.2, "aura": 0.3},
                                          "forms": {"liquid": [{"block": "minecraft:lava"}]}}}}
                """))), "lava already reads as stone");
        assertThrows(IllegalStateException.class, () -> Materials.extend(table -> table.remove("fire")),
                "fire is a primordial");
        assertEquals(before, Materials.get().substances().size(), "what was refused left no trace");
    }

    @Test
    void anAddonBringsItsBeingsInTheSameShape() {
        int before = Materials.get().beings().size();
        Materials.extend(table -> table.read(new StringReader("""
                {"beings": {"unicorn": {"name": "unicórnio", "recipe": {"firmo": 0.08, "aqua": 0.52, "aura": 0.36, "igni": 0.04},
                                        "entity": "addon:unicorn"}}}
                """)));
        MaterialTable table = Materials.get();
        assertEquals(before + 1, table.beings().size());
        assertEquals("unicórnio", table.being("unicorn").orElseThrow().name());
        assertEquals("unicorn", table.beingShownBy("addon:unicorn").orElseThrow().id());
    }

    @Test
    void beingsThatDoNotHoldTogetherAreRefused() {
        int before = Materials.get().beings().size();
        assertThrows(IllegalStateException.class, () -> Materials.extend(table -> table.read(new StringReader("""
                {"beings": {"calf": {"recipe": {"firmo": 0.07, "aqua": 0.60, "aura": 0.30, "igni": 0.03},
                                     "entity": "minecraft:cow"}}}
                """))), "the cow already shows the cow");
        assertThrows(IllegalStateException.class, () -> Materials.extend(table -> table.read(new StringReader("""
                {"beings": {"snow": {"recipe": {"aqua": 0.70, "aura": 0.30}, "entity": "addon:snowman"}}}
                """))), "snow is a substance");
        assertThrows(IllegalArgumentException.class, () -> Materials.extend(table -> table.read(new StringReader("""
                {"beings": {"ghost": {"recipe": {"aura": 1}}}}
                """))), "a being with no creature to show it");
        assertEquals(before, Materials.get().beings().size(), "what was refused left no trace");
    }

    private static Composition compositionOf(SourceSpec source) {
        return Composition.ofEssence(source.essence()).orElseThrow();
    }
}
