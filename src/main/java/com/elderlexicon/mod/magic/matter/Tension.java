package com.elderlexicon.mod.magic.matter;

/**
 * How high a portion stands above the rose (docs/plano-rosa-dos-elementos.md, section 2), and so whether it is matter
 * or a phenomenon. It comes from two sources:
 * <ul>
 *   <li><b>latent</b>: opposites held together ({@link Rose#latent()}). In a solid the parts are bound and hold it
 *       still (wood has water and fire and does not boil); in a fluid they move and it is free.</li>
 *   <li><b>agitation</b>: energy put into the portion, for each of its UMU: the push of a force, heat above common fire,
 *       a condensation pressing it into one point ({@link Agitation}).</li>
 * </ul>
 * Low tension is matter: it stops, has a form and settles as the code of a natural thing or as formless matter (the
 * side of creation). High tension is a phenomenon: energy that does not stop and discharges (lightning, steam, a gust,
 * shrapnel, a blow), and what is left settles back into matter (the side of destruction).
 *
 * @param latent    the latent tension that is free to act, 0 to 1
 * @param agitation the energy put into it, for each UMU
 */
public record Tension(double latent, double agitation) {

    /**
     * From this much tension a portion is a phenomenon. It is the same line the reaction of opposites always had: two
     * opposites of a tenth of a fluid each, a fifth of it cancelling out.
     */
    public static final double PHENOMENON = 0.2D;

    public static final Tension NONE = new Tension(0.0D, 0.0D);

    public Tension {
        if (latent < 0.0D || agitation < 0.0D) {
            throw new IllegalArgumentException("a negative tension");
        }
    }

    /** The tension of matter standing at {@code rose}, in {@code state}, with {@code agitation} put into it. */
    public static Tension of(Rose rose, State state, Agitation agitation) {
        return new Tension(state == State.SOLID ? 0.0D : rose.latent(), agitation.total());
    }

    /** The tension of this matter, left alone. */
    public static Tension of(Matter matter) {
        return of(Rose.of(matter.composition()), matter.state(), Agitation.NONE);
    }

    /** All of it: how high it stands above the rose. */
    public double total() {
        return latent + agitation;
    }

    /** Whether it is a phenomenon, energy that discharges, rather than matter that stays. */
    public boolean phenomenon() {
        return total() > PHENOMENON;
    }
}
