package com.elderlexicon.mod.magic.lexicon;

/**
 * How a source shows itself when a spell makes it appear, as data: the world code asks these instead of the rune's name,
 * so a new source (one an addon brings) behaves by what it declares.
 *
 * @param persistent  its matter appears and stays (water, earth, mud, magma); otherwise it acts and goes
 *                    (docs/marcas-como-runas-design.md, "Elementos permanentes e efêmeros")
 * @param matter      the block it lays when it is permanent ({@code minecraft:water}); null lays loose soil
 * @param image       the block its image shows ({@code igni surgit vocant}); null shows its matter when it is
 *                    permanent, and only its light otherwise
 * @param wind        it blows (air, mist, dust): it moves what it reaches instead of burning it
 * @param windStrikes its gust also strikes the creature it was aimed at (air does; mist and dust do not)
 * @param kindles     it lays fire where it lands
 * @param touches     it works on the creatures it reaches (Vis only glows)
 * @param strikes     it falls as lightning
 * @param quenches    it fills a cauldron or puts out a fire when it lands on one
 * @param particle    the particle it shows as it flows ({@code minecraft:lava}, or {@code block:minecraft:mud});
 *                    null uses its element's
 * @param glow        the particle it shows where it is invoked; null uses {@code particle}
 * @param burnTicks   how long it keeps a furnace lit when it lights one, in ticks
 * @param reveals     what {@code surgit} with it looks for ({@code igni}, {@code aqua}, {@code aura}, {@code firmo});
 *                    null looks for magic, as mana does
 */
public record Traits(boolean persistent, String matter, String image, boolean wind, boolean windStrikes,
                     boolean kindles, boolean touches, boolean strikes, boolean quenches, String particle, String glow,
                     int burnTicks, String reveals) {

    /** What a source that declares nothing does: acts, touches, and shows its element's particle. */
    public static final Traits NONE = new Traits(false, null, null, false, false, false, true, false, false, null,
            null, 200, null);

    /** The block its image shows, or null when it has no matter to show. */
    public String imageBlock() {
        if (image != null) {
            return image;
        }
        return persistent ? matterBlock() : null;
    }

    /** The block it lays when permanent. */
    public String matterBlock() {
        return matter == null ? "minecraft:dirt" : matter;
    }
}
