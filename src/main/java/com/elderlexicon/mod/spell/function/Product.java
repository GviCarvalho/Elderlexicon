package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * What a verb produced, handed to the verb after it (R2 of docs/plano-materia-e-forca.md: {@code igni vocant iactare}
 * pushes the fire the vocant made). The verb that makes it says, once it is there, where it is and what it does where
 * it lands; the verb after takes it and moves it. Nobody taking it, the verb that makes it leaves it where it would
 * have gone.
 */
public final class Product {

    /** What the product does where it lands. */
    interface Arrival {
        void at(SpellEffects.SpellImpact impact);
    }

    private final VitaElement element;
    private final double umu;
    private boolean taken;
    private ServerLevel level;
    private Vec3 at;
    private Arrival arrival;
    private final List<Consumer<Product>> movers = new ArrayList<>();

    Product(VitaElement element, double umu) {
        this.element = element;
        this.umu = umu;
    }

    /** Whether the verb after took it to move: then it is made where it starts from, not where it would have gone. */
    boolean taken() {
        return taken;
    }

    /** The verb after takes it: {@code mover} runs once it is there. */
    void take(Consumer<Product> mover) {
        taken = true;
        if (at != null) {
            mover.accept(this);
        } else {
            movers.add(mover);
        }
    }

    /** It is there: at {@code at}, doing {@code arrival} where it lands. */
    void ready(ServerLevel level, Vec3 at, Arrival arrival) {
        this.level = level;
        this.at = at;
        this.arrival = arrival;
        List<Consumer<Product>> waiting = List.copyOf(movers);
        movers.clear();
        waiting.forEach(mover -> mover.accept(this));
    }

    VitaElement element() {
        return element;
    }

    double umu() {
        return umu;
    }

    ServerLevel level() {
        return level;
    }

    Vec3 at() {
        return at;
    }

    void arrive(SpellEffects.SpellImpact impact) {
        if (arrival != null) {
            arrival.at(impact);
        }
    }
}
