package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.magic.flow.SpellLedger;
import com.elderlexicon.mod.magic.grammar.SpellGrammar;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.function.Product;
import com.elderlexicon.mod.spell.scene.SpellScene;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One spell being cast: who casts it, its words and steps, and the account of its energy ({@link SpellLedger}) that the
 * flow keeps as it runs.
 */
public final class SpellContext implements SpellLedger {

    private static final double EPSILON = 1.0E-4D;

    private final ServerPlayer player;
    private final List<String> lexemes;
    private List<String> words;
    private final List<SpellAction> actions;
    private Optional<Parser.PrimarySource> primarySource;
    private final VitaElement basePrimaryElement;
    private VitaElement activePrimaryElement;
    private String elementRuneId;
    private double conduitOverflow;
    private final EnumMap<VitaElement, Double> ambientEnergy = new EnumMap<>(VitaElement.class);
    /** What a spell brought into the mage's body from the world rather than spent (a verb turned around). */
    private final EnumMap<VitaElement, Double> absorbed = new EnumMap<>(VitaElement.class);
    private final List<VertereRequest> vertereRequests;
    private double totalCost;
    private double environmentalContribution;
    private double payableCost;
    private boolean focusActive;
    private SpellScene scene = new SpellScene();
    private int sceneSpellId = scene.registerSpell();
    private SpellAction currentAction;
    /** What the last verb made for the verb after it to act on (R2), until that verb takes it. */
    private Product handedOn;

    public SpellContext(ServerPlayer player,
                        List<String> lexemes,
                        Optional<Parser.PrimarySource> primarySource,
                        VitaElement primaryElement,
                        double totalCost,
                        List<SpellAction> actions,
                        List<VertereRequest> vertereRequests) {
        this.player = player;
        this.lexemes = List.copyOf(lexemes);
        this.primarySource = primarySource;
        this.basePrimaryElement = primaryElement == null ? VitaElement.BALANCED : primaryElement;
        this.activePrimaryElement = this.basePrimaryElement;
        this.elementRuneId = primarySource
                .map(primary -> primary.definition().id())
                .orElse(this.basePrimaryElement.runeId());
        this.vertereRequests = vertereRequests == null ? List.of() : List.copyOf(vertereRequests);
        this.totalCost = totalCost;
        this.conduitOverflow = 0.0D;
        this.payableCost = totalCost;
        this.actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public ServerPlayer player() {
        return player;
    }

    /** Stage shared with the other spells released by the same cast. */
    public SpellScene scene() {
        return scene;
    }

    /** Identifies this spell inside {@link #scene()}. */
    public int sceneSpellId() {
        return sceneSpellId;
    }

    /** Joins this spell to the scene of its block, replacing the private one it starts with. */
    public void attachScene(SpellScene sharedScene) {
        if (sharedScene == null) {
            return;
        }
        this.scene = sharedScene;
        this.sceneSpellId = sharedScene.registerSpell();
    }

    /** The words as they were written, shorthands and all (what a naming verb records is this). */
    public List<String> lexemes() {
        return lexemes;
    }

    /**
     * The words the spell says: its lexemes with every fusion that stands for other runes written out
     * ({@code transiectio igni} says {@code vertere igni iactare}), as the grammar read them.
     */
    public List<String> words() {
        List<String> found = words;
        if (found == null) {
            found = List.copyOf(new SpellGrammar(Lexicons.get()).expand(lexemes, new ArrayList<>()));
            words = found;
        }
        return found;
    }

    public List<SpellAction> actions() {
        return actions;
    }

    /**
     * The function being executed right now, so a handler can read its marks and place. Handlers that
     * act later must read it while {@code execute} runs, since the next function replaces it.
     */
    public Optional<SpellAction> currentAction() {
        return Optional.ofNullable(currentAction);
    }

    public void setCurrentAction(SpellAction action) {
        this.currentAction = action;
    }

    /** A verb hands what it produces to the verb after it ({@code igni vocant iactare}). */
    public void handOn(Product product) {
        this.handedOn = product;
    }

    /** What the verb before handed on, taken by the verb acting on it: it is handed to no one else. */
    public Optional<Product> takeHandedOn() {
        Optional<Product> taken = Optional.ofNullable(handedOn);
        handedOn = null;
        return taken;
    }

    public Optional<Parser.PrimarySource> primarySource() {
        return primarySource;
    }

    public void setPrimarySource(Optional<Parser.PrimarySource> primarySource) {
        this.primarySource = primarySource;
    }

    public VitaElement basePrimaryElement() {
        return basePrimaryElement;
    }

    public VitaElement primaryElement() {
        return activePrimaryElement;
    }

    public void setPrimaryElement(VitaElement element) {
        this.activePrimaryElement = element == null ? VitaElement.BALANCED : element;
    }

    public String elementRuneId() {
        return elementRuneId;
    }

    public void setElementRuneId(String runeId) {
        if (runeId == null || runeId.isBlank()) {
            return;
        }
        this.elementRuneId = runeId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public double totalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
        recalculatePayable();
    }

    public void addTotalCost(double delta) {
        if (delta <= EPSILON) {
            return;
        }
        this.totalCost += delta;
        recalculatePayable();
    }

    public double environmentalContribution() {
        return environmentalContribution;
    }

    public double payableCost() {
        return payableCost;
    }

    public Map<VitaElement, Double> ambientEnergy() {
        return Collections.unmodifiableMap(ambientEnergy);
    }

    /**
     * True when the caster held a ready arcane focus when the spell started. A focus keeps the
     * spell away from the caster's Vita reserves.
     */
    public boolean focusActive() {
        return focusActive;
    }

    public void setFocusActive(boolean focusActive) {
        this.focusActive = focusActive;
    }

    public double conduitOverflow() {
        return conduitOverflow;
    }

    public void setConduitOverflow(double overflow) {
        conduitOverflow = Math.max(0.0D, overflow);
    }

    public List<VertereRequest> vertereRequests() {
        return vertereRequests;
    }

    /**
     * Energy taken from the world that pays for the spell (a source with tenet): the functions spend it, so
     * it lowers what the mage pays and is not kept.
     */
    public void addAmbientEnergy(VitaElement element, double amount) {
        if (element == null || amount <= EPSILON) {
            return;
        }
        ambientEnergy.merge(element, amount, (left, right) -> left + right);
        environmentalContribution += amount;
        recalculatePayable();
    }

    private void recalculatePayable() {
        this.payableCost = Math.max(0.0D, totalCost - environmentalContribution);
    }

    /** Energy pulled from the world into the mage's body (a verb turned around, or what a capture brought beyond the cost). */
    public void absorbIntoBody(VitaElement element, double amount) {
        if (element == null || amount <= EPSILON) {
            return;
        }
        absorbed.merge(element, amount, Double::sum);
    }

    public Map<VitaElement, Double> absorbed() {
        return Collections.unmodifiableMap(absorbed);
    }

    /**
     * Gives the body what was absorbed into it. What paid for the spell was spent by it and is not given as well; with
     * a focus the body is left alone.
     */
    public void commitAmbientEnergy() {
        if (focusActive || player == null) {
            return;
        }
        absorbed.forEach((element, amount) -> VitaSystem.restoreElementEnergy(player, element, amount));
    }
}
