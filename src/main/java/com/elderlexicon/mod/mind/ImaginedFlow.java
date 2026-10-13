package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.magic.flow.FlowInterpreter;
import com.elderlexicon.mod.magic.flow.FlowWorld;
import com.elderlexicon.mod.magic.flow.SpellLedger;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A spell read with no one there (docs/feiticeiro-design.md, 3.2): the flow runs as it would, and the world it runs in only
 * writes down what each verb would do. Nothing is cast; it is how the mind learns what a sentence does.
 */
final class ImaginedFlow {

    /** How much of each energy the imagined body and world hold: enough for any sentence to run its course. */
    private static final double PLENTY = 1000.0D;

    private ImaginedFlow() {
    }

    /** What {@code read} would do, act by act, in the order its verbs act. */
    static List<Act> imagine(Lexicon lexicon, SpellActionResult read, double cost) {
        VitaElement primary = read.primarySource().map(source -> lexicon.elementOf(source.definition().id()))
                .orElse(VitaElement.BALANCED);
        Ledger ledger = new Ledger(primary, cost, read.vertereRequests());
        ledger.primarySource = read.primarySource();
        World world = new World(lexicon, ledger);
        new FlowInterpreter(lexicon).run(ledger, read.actions(), world);
        return List.copyOf(world.acts);
    }

    private static final class Ledger implements SpellLedger {
        Optional<Parser.PrimarySource> primarySource = Optional.empty();
        VitaElement primaryElement;
        String elementRuneId;
        final List<VertereRequest> vertereRequests;
        SpellAction currentAction;
        double totalCost;
        double environmental;

        Ledger(VitaElement primary, double totalCost, List<VertereRequest> requests) {
            this.primaryElement = primary;
            this.elementRuneId = primary.runeId();
            this.totalCost = totalCost;
            this.vertereRequests = List.copyOf(requests);
        }

        @Override
        public Optional<Parser.PrimarySource> primarySource() {
            return primarySource;
        }

        @Override
        public VitaElement primaryElement() {
            return primaryElement;
        }

        @Override
        public void setPrimaryElement(VitaElement element) {
            primaryElement = element;
        }

        @Override
        public String elementRuneId() {
            return elementRuneId;
        }

        @Override
        public void setElementRuneId(String runeId) {
            if (runeId != null) {
                elementRuneId = runeId;
            }
        }

        @Override
        public List<VertereRequest> vertereRequests() {
            return vertereRequests;
        }

        @Override
        public void setCurrentAction(SpellAction action) {
            currentAction = action;
        }

        @Override
        public double totalCost() {
            return totalCost;
        }

        @Override
        public void addTotalCost(double delta) {
            if (delta > 1.0E-4D) {
                totalCost += delta;
            }
        }

        @Override
        public double payableCost() {
            return Math.max(0.0D, totalCost - environmental);
        }

        @Override
        public void addAmbientEnergy(VitaElement element, double amount) {
            if (amount > 1.0E-4D) {
                environmental += amount;
            }
        }

        @Override
        public void absorbIntoBody(VitaElement element, double amount) {
        }

        @Override
        public boolean focusActive() {
            return false;
        }
    }

    private static final class World implements FlowWorld {
        final List<Act> acts = new ArrayList<>();
        private final Lexicon lexicon;
        private final Ledger ledger;

        World(Lexicon lexicon, Ledger ledger) {
            this.lexicon = lexicon;
            this.ledger = ledger;
        }

        @Override
        public boolean hasCaster() {
            return true;
        }

        @Override
        public void perform(SpellAction action, VitaElement element) {
            acts.add(Act.of(lexicon, action, element));
        }

        @Override
        public void disguise(SpellAction action) {
        }

        @Override
        public double drawAll(VitaElement element) {
            return PLENTY;
        }

        @Override
        public double draw(VitaElement element, double amount) {
            return Math.min(PLENTY, amount);
        }

        @Override
        public void nothingInBody(VitaElement element) {
        }

        @Override
        public Captured captureAll(List<VitaElement> chain, double limit, SpellAction spender, SpellAction capture) {
            return new Captured(1, Math.min(PLENTY, limit), 0, -1);
        }

        @Override
        public int gatherFromBody(List<VitaElement> chain, double worked, SpellAction spender, int chargeTicks) {
            return -1;
        }

        @Override
        public double capture(VitaElement from, VitaElement as, SpellAction spender, double workShare) {
            double needed = ledger.payableCost();
            ledger.addAmbientEnergy(as, needed);
            return needed;
        }

        @Override
        public double convertInPlace(VitaElement from, VitaElement as, double amount, double workShare) {
            return amount;
        }

        @Override
        public double transferVita(VertereRequest request) {
            return request.amount();
        }
    }
}
