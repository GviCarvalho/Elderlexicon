package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.magic.matter.Form;
import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Matter in a square of the invocation bench (docs/invocacao-design.md, section 2.1). Every thing is its code, so many
 * particles of each of the four, and that is all that makes it what it is: matter invoked into a square becomes the
 * thing whose code it has (the particles of iron are an ingot of it, those of wood a stick), within the tolerance any
 * matter is read with (five points of each share, and five parts in a hundred of the amount). Matter near no thing is
 * kept as it is, matter with no name: a blade of fire is fire, and no item of the game.
 */
public final class BenchMatter {

    /** How far the amount may be from a thing's and still be that thing: five parts in a hundred, as the shares. */
    public static final double AMOUNT_TOLERANCE = MaterialTable.TOLERANCE;

    private BenchMatter() {
    }

    /** {@code umu} of {@code element} as particles: vis is a quarter of each of the four. */
    public static Particles of(VitaElement element, double umu) {
        if (element == null || element.isBalanced()) {
            long quarter = Particles.ofUmu(umu / 4.0D);
            return new Particles(quarter, quarter, quarter, quarter);
        }
        return Particles.of(element, Particles.ofUmu(umu));
    }

    /**
     * The matter a stack is: what the bench made is what it keeps; a natural thing of the game (an ingot, a log) is its
     * code, as many times as there are of it. Empty for what is no matter (a chest, a sword of the game).
     */
    public static Optional<Particles> of(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.is(Invocations.MATTER.get())) {
            return Optional.of(Conjured.matter(stack));
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return Optional.empty();
        }
        MaterialTable table = Materials.get();
        Optional<MaterialTable.Reading> reading = table.read(Form.Kind.ITEM, id.toString());
        if (reading.isEmpty() && stack.getItem() instanceof BlockItem) {
            reading = table.read(Form.Kind.BLOCK, id.toString());
        }
        return reading.map(read -> read.substance().code(Particles.ofUmu(read.umu()) * stack.getCount()));
    }

    /**
     * What some matter becomes in a square: the thing of the game whose code it has, one of it; or else the matter as
     * it is, which lasts {@code window} ticks once out of the bench when it is volatile ({@code 0}: the default).
     */
    public static ItemStack become(Particles particles, int window) {
        Optional<Item> thing = thingOf(particles);
        if (thing.isPresent()) {
            return new ItemStack(thing.get());
        }
        ItemStack matter = new ItemStack(Invocations.MATTER.get());
        Conjured.setMatter(matter, particles);
        Conjured.setWindow(matter, window);
        return matter;
    }

    /** The thing of the game whose code this is: an item first, a block of it if no item is. */
    public static Optional<Item> thingOf(Particles particles) {
        // A square is the room of one item: what the matter in it is, is its code and how tightly it is packed there
        // (an ingot of iron is ten UMU of iron in a square). The bench is worked by hands: what only hands make (an
        // ingot, planks) is found near, as anything is.
        Optional<Substance> substance = particles.present().composition()
                .flatMap(composition -> Materials.get().identify(composition, Conjured.packing(particles), true));
        if (substance.isEmpty() || particles.owes()) {
            return Optional.empty();
        }
        long total = particles.total();
        for (Form.Kind kind : new Form.Kind[] {Form.Kind.ITEM, Form.Kind.BLOCK}) {
            for (State state : State.values()) {
                for (Form form : substance.get().declared(state)) {
                    if (form.kind() != kind || form.particles() <= 0L
                            || Math.abs(total - form.particles()) > AMOUNT_TOLERANCE * form.particles()) {
                        continue;
                    }
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(form.id()));
                    if (item != null && item != Items.AIR) {
                        return Optional.of(item);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Whether two portions of matter are the same thing: the same substance, or the same mixture with no name. */
    public static boolean same(Particles one, Particles other) {
        MaterialTable table = Materials.get();
        Optional<Substance> first = table.identify(one.present());
        Optional<Substance> second = table.identify(other.present());
        if (first.isPresent() || second.isPresent()) {
            return first.equals(second);
        }
        return one.present().composition().flatMap(a -> other.present().composition()
                .map(b -> a.distance(b) <= MaterialTable.TOLERANCE + 1.0E-9D)).orElse(false);
    }
}
