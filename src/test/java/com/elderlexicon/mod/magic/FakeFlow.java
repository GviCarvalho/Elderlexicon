package com.elderlexicon.mod.magic;

import com.elderlexicon.mod.magic.flow.FlowWorld;
import com.elderlexicon.mod.magic.flow.SpellLedger;
import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A spell's ledger and world kept in plain numbers, to run the flow without a game. */
final class FakeFlow {

    private FakeFlow() {
    }

    static final class Ledger implements SpellLedger {
        Optional<Parser.PrimarySource> primarySource = Optional.empty();
        VitaElement primaryElement;
        String elementRuneId;
        final List<VertereRequest> vertereRequests;
        SpellAction currentAction;
        double totalCost;
        double environmental;
        boolean focus;
        final Map<VitaElement, Double> ambient = new EnumMap<>(VitaElement.class);
        final Map<VitaElement, Double> absorbed = new EnumMap<>(VitaElement.class);

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
                ambient.merge(element, amount, Double::sum);
                environmental += amount;
            }
        }

        @Override
        public void absorbIntoBody(VitaElement element, double amount) {
            if (amount > 1.0E-4D) {
                absorbed.merge(element, amount, Double::sum);
            }
        }

        @Override
        public boolean focusActive() {
            return focus;
        }
    }

    /** One call the flow made to the world. */
    record Call(String what, VitaElement element, SpellAction action, double amount) {
    }

    static final class World implements FlowWorld {
        /** What the world was told of where a source taken from it is: one per capture, in order. */
        final List<SpellAction> origins = new ArrayList<>();
        final List<Call> calls = new ArrayList<>();
        boolean caster = true;
        /** What the body holds of each element (mana for vis). */
        final Map<VitaElement, Double> body = new EnumMap<>(VitaElement.class);
        /** What the world holds within reach, and in how many sources. */
        double worldUmu = 40.0D;
        int worldSources = 4;
        /** What each verb costs when it runs, as its operation would add. */
        final Map<String, Double> operationCost = new java.util.HashMap<>();
        Ledger ledger;

        World(Ledger ledger) {
            this.ledger = ledger;
        }

        List<String> performed() {
            return calls.stream().filter(call -> call.what().equals("perform")).map(call -> call.action().runeId()).toList();
        }

        Call last(String what) {
            for (int i = calls.size() - 1; i >= 0; i--) {
                if (calls.get(i).what().equals(what)) {
                    return calls.get(i);
                }
            }
            return null;
        }

        @Override
        public boolean hasCaster() {
            return caster;
        }

        @Override
        public void perform(SpellAction action, VitaElement element) {
            calls.add(new Call("perform", element, action, 0.0D));
            Double cost = operationCost.get(action.runeId());
            if (cost != null) {
                ledger.addTotalCost(cost);
            }
        }

        @Override
        public void disguise(SpellAction action) {
            calls.add(new Call("disguise", null, action, 0.0D));
        }

        @Override
        public double drawAll(VitaElement element) {
            return draw(element, Double.MAX_VALUE);
        }

        @Override
        public double draw(VitaElement element, double amount) {
            double held = body.getOrDefault(element, 0.0D);
            double taken = Math.min(held, amount);
            body.put(element, held - taken);
            calls.add(new Call("draw", element, null, taken));
            return taken;
        }

        @Override
        public void nothingInBody(VitaElement element) {
            calls.add(new Call("nothing", element, null, 0.0D));
        }

        @Override
        public Captured captureAll(List<VitaElement> chain, double limit, SpellAction spender, SpellAction capture) {
            double taken = Math.min(worldUmu, limit);
            calls.add(new Call("captureAll", chain.get(chain.size() - 1), spender, taken));
            origins.add(capture);
            return new Captured(worldSources, taken, 0, spender.atOnce() ? 7 : -1);
        }

        @Override
        public int gatherFromBody(List<VitaElement> chain, double worked, SpellAction spender, int chargeTicks) {
            calls.add(new Call("gather", chain.get(chain.size() - 1), spender, worked));
            return 9;
        }

        @Override
        public double capture(VitaElement from, VitaElement as, SpellAction spender, double workShare) {
            double needed = ledger.payableCost();
            calls.add(new Call("capture", as, spender, needed));
            origins.add(ledger.currentAction);
            ledger.addAmbientEnergy(as, needed);
            return needed;
        }

        @Override
        public double convertInPlace(VitaElement from, VitaElement as, double amount, double workShare) {
            calls.add(new Call("convertInPlace", as, null, amount));
            origins.add(ledger.currentAction);
            return amount;
        }

        @Override
        public double transferVita(VertereRequest request) {
            calls.add(new Call("vita", request.target(), null, request.amount()));
            return request.amount();
        }
    }
}
