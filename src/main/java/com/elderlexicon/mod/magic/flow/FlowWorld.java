package com.elderlexicon.mod.magic.flow;

import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.List;

/**
 * What the flow of a spell asks of the world it runs in: to take energy out of the body or the world, to convert it, and
 * to run each verb's operation. The flow decides when and how much; the world only does. The game gives it the mage and
 * the level; a test gives it numbers.
 */
public interface FlowWorld {

    /** Whether a mage is there for the spell to act through (a spell read with no one there only keeps accounts). */
    boolean hasCaster();

    /** Runs the operation of {@code action}'s verb, with the energy in flow showing as {@code element}. */
    void perform(SpellAction action, VitaElement element);

    /** Makes the source nearby look like what the conversion names; it stays what it is ({@code igni surgit vertere aqua}). */
    void disguise(SpellAction action);

    /** Takes all of {@code element} there is out of the mage's body; returns how much. */
    double drawAll(VitaElement element);

    /** Takes up to {@code amount} of {@code element} out of the mage's body; returns how much. */
    double draw(VitaElement element, double amount);

    /** Tells the mage there was none of {@code element} in the body to take. */
    void nothingInBody(VitaElement element);

    /**
     * Captures from the world every source of the first element of {@code chain} in reach, up to {@code limit} UMU, for
     * {@code spender} to release at once; converted along the chain when it is longer than one.
     */
    Captured captureAll(List<VitaElement> chain, double limit, SpellAction spender, SpellAction capture);

    /**
     * Gathers {@code worked} UMU drawn out of the body into one point before {@code spender} releases it, over
     * {@code chargeTicks}; returns the id of what it is gathered into, or -1.
     */
    int gatherFromBody(List<VitaElement> chain, double worked, SpellAction spender, int chargeTicks);

    /**
     * Pulls from the world what the spell spent, as {@code from} converted into {@code as}; what the last source brings
     * beyond it goes into the body. Returns what was pulled.
     */
    double capture(VitaElement from, VitaElement as, SpellAction spender, double workShare);

    /** Converts {@code amount} UMU of {@code from} in the world into {@code as}, where it is. Returns what was converted. */
    double convertInPlace(VitaElement from, VitaElement as, double amount, double workShare);

    /** Converts the mage's own Vita as {@code request} asks; returns how much was converted. */
    double transferVita(VertereRequest request);

    /** What a whole capture took: how many sources, the UMU they held, the coal in them, and its orb (or -1). */
    record Captured(int sources, double total, int coal, int orb) {
    }
}
