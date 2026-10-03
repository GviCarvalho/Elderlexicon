package com.elderlexicon.mod.spell.life;

import com.elderlexicon.mod.magic.matter.Identity;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The law of animation (docs/vita-design.md): life is not an element but an unbalanced mixture of the four, in a
 * proportion each kind of being has (a body of 100 UMU is 20 HP), anchored to a reserve of 100 UMU of Vis (book 3.2:
 * the Anchor of the Hundred, the same in every living thing). When energies and matter are released together, in one
 * place and one instant, with an anchor among them and a body in them, they bind into a being instead of scattering:
 * the kind of the table (docs/particulas-design.md, section 6) whose proportion is nearest to what was given, counted
 * in particles. The nearer, the sounder; what is off by much is born malformed.
 */
public final class Animation {

    /** The Vis every living thing is anchored to. */
    public static final double ANCHOR = 100.0D;
    /** The least body that lives: one half heart. */
    public static final double LEAST_BODY = 5.0D;
    /** UMU of body for each point of health. */
    public static final double UMU_PER_HP = 5.0D;
    /** Off by less than this in an element, the body is sound in it. */
    public static final double SOUND = Identity.Creature.SOUND;

    public static final List<VitaElement> ELEMENTS =
            List.of(VitaElement.AQUA, VitaElement.AURA, VitaElement.IGNI, VitaElement.FIRMO);

    /**
     * A being bound from what was released: the kind the table reads its body as (how far off it is in each element,
     * positive for too much, and how far in all), how much body it has (and so its health), and the Vis left beyond
     * the anchor.
     */
    public record Being(Identity.Creature creature, double body, double leftoverVis) {

        /** Its kind, as the table has it. */
        public com.elderlexicon.mod.magic.matter.Being kind() {
            return creature.being();
        }

        public Map<VitaElement, Double> deviation() {
            return creature.deviation();
        }

        public double distance() {
            return creature.distance();
        }

        public double health() {
            return body / UMU_PER_HP;
        }

        public boolean sound() {
            return creature.sound();
        }

        /** The share of its body the being has in {@code element}. */
        public double share(VitaElement element) {
            return kind().recipe().share(element) + deviation().getOrDefault(element, 0.0D);
        }
    }

    private Animation() {
    }

    /** What binds from {@code body} (UMU in each of the four elements) and {@code vis}, released together. */
    public static Optional<Being> quicken(Map<VitaElement, Double> body, double vis) {
        Particles particles = Particles.NONE;
        for (VitaElement element : ELEMENTS) {
            double umu = Math.max(0.0D, body.getOrDefault(element, 0.0D));
            particles = particles.plus(Particles.of(element, Particles.ofUmu(umu)));
        }
        return quicken(particles, vis);
    }

    /**
     * What binds from the particles of a body and {@code vis}, released together: a being when there is an anchor and
     * enough body, of the kind whose proportion is nearest; nothing otherwise. What the body owes is no part of it.
     */
    public static Optional<Being> quicken(Particles body, double vis) {
        double held = body.present().umu();
        if (vis < ANCHOR || held < LEAST_BODY) {
            return Optional.empty();
        }
        if (!(Materials.get().identify(body, true) instanceof Identity.Creature creature)) {
            return Optional.empty(); // a table with no beings: nothing for the anchor to hold
        }
        return Optional.of(new Being(creature, held, vis - ANCHOR));
    }
}
