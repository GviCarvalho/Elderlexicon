package com.elderlexicon.mod.magic.flow;

import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.List;
import java.util.Optional;

/**
 * The account of one spell as its energy flows: what it costs, what the world paid, what went into the mage's body and
 * which step runs now. The game's {@code SpellContext} keeps it; a test can keep it in a plain object.
 */
public interface SpellLedger {

    /** The source written last, the one the spell starts from (mana when none was written). */
    Optional<Parser.PrimarySource> primarySource();

    VitaElement primaryElement();

    void setPrimaryElement(VitaElement element);

    /** The source rune in force right now: its own name, which an addon's source keeps where its element would not. */
    String elementRuneId();

    void setElementRuneId(String runeId);

    /** The conversions the sentence asked for, in the order they were written. */
    List<VertereRequest> vertereRequests();

    /** The step running now, for the world to read its marks, place and filters. */
    void setCurrentAction(SpellAction action);

    double totalCost();

    void addTotalCost(double delta);

    /** What the spell still has to take from the mage (its cost less what the world gave). */
    double payableCost();

    /** Energy from outside the body (captured, or drawn out of it before) that pays for what the spell spends. */
    void addAmbientEnergy(VitaElement element, double amount);

    /** Energy that goes into the mage's body. */
    void absorbIntoBody(VitaElement element, double amount);

    /** Whether the spell runs through a focus, which spares the mage's Vita. */
    boolean focusActive();
}
