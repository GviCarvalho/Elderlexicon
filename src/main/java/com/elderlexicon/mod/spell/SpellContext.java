package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.scene.SpellScene;
import com.elderlexicon.mod.spell.vertere.VertereRequest;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SpellContext {

    private static final double EPSILON = 1.0E-4D;

    private final ServerPlayer player;
    private final List<String> lexemes;
    private final List<SpellAction> actions;
    private Optional<Parser.PrimarySource> primarySource;
    private final VitaElement basePrimaryElement;
    private VitaElement activePrimaryElement;
    private String elementRuneId;
    private double conduitOverflow;
    private final EnumMap<VitaElement, Double> ambientEnergy = new EnumMap<>(VitaElement.class);
    private final List<VertereRequest> vertereRequests;
    private double totalCost;
    private double environmentalContribution;
    private double payableCost;
    private SpellScene scene = new SpellScene();
    private int sceneSpellId = scene.registerSpell();

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

    public List<String> lexemes() {
        return lexemes;
    }

    public List<SpellAction> actions() {
        return actions;
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

    public double conduitOverflow() {
        return conduitOverflow;
    }

    public void setConduitOverflow(double overflow) {
        conduitOverflow = Math.max(0.0D, overflow);
    }

    public List<VertereRequest> vertereRequests() {
        return vertereRequests;
    }

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

    public void commitAmbientEnergy() {
        ambientEnergy.forEach((element, amount) ->
                VitaSystem.restoreElementEnergy(player, element, amount));
    }
}
