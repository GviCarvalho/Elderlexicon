package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.magic.matter.Qualities;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

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
    private final Qualities qualities;
    @Nullable
    private ElementOrb orb;
    private boolean taken;
    private ServerLevel level;
    private Vec3 at;
    private Arrival arrival;
    private final List<Consumer<Product>> movers = new ArrayList<>();

    Product(VitaElement element, double umu) {
        this(element, umu, Qualities.of(element));
    }

    /** What it is like decides how hard it strikes where it lands (its weight) and what it does there (its heat...). */
    Product(VitaElement element, double umu, Qualities qualities) {
        this.element = element;
        this.umu = umu;
        this.qualities = qualities;
    }

    /**
     * It is already gathered into {@code orb} (a condensation): the verb after throws that orb instead of making one
     * (igni quantum chronos 0 vocant iactare).
     */
    void carriedBy(ElementOrb orb) {
        this.orb = orb;
    }

    /** The orb it is gathered into, while it is still there. */
    @Nullable
    ElementOrb orb() {
        return orb != null && orb.isAlive() && !orb.isRemoved() ? orb : null;
    }

    Qualities qualities() {
        return qualities;
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
