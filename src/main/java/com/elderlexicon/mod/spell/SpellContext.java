package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SpellContext {

    private static final double EPSILON = 1.0E-4D;

    private final Parser parser;
    private final ServerPlayer player;
    private final List<String> lexemes;
    private final List<String> loweredLexemes;
    private Optional<Parser.PrimarySource> primarySource;
    private final VitaElement primaryElement;
    private final EnumMap<VitaElement, Double> ambientEnergy = new EnumMap<>(VitaElement.class);
    private double totalCost;
    private double environmentalContribution;
    private double payableCost;

    public SpellContext(Parser parser,
                        ServerPlayer player,
                        List<String> lexemes,
                        Optional<Parser.PrimarySource> primarySource,
                        VitaElement primaryElement,
                        double totalCost) {
        this.parser = parser;
        this.player = player;
        this.lexemes = List.copyOf(lexemes);
        this.loweredLexemes = this.lexemes.stream()
                .map(token -> token.toLowerCase(Locale.ROOT))
                .toList();
        this.primarySource = primarySource;
        this.primaryElement = primaryElement;
        this.totalCost = totalCost;
        this.payableCost = totalCost;
    }

    public Parser parser() {
        return parser;
    }

    public ServerPlayer player() {
        return player;
    }

    public List<String> lexemes() {
        return lexemes;
    }

    public List<String> loweredLexemes() {
        return loweredLexemes;
    }

    public Optional<Parser.PrimarySource> primarySource() {
        return primarySource;
    }

    public void setPrimarySource(Optional<Parser.PrimarySource> primarySource) {
        this.primarySource = primarySource;
    }

    public VitaElement primaryElement() {
        return primaryElement;
    }

    public double totalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
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

    public void addAmbientEnergy(VitaElement element, double amount) {
        if (element == null || amount <= EPSILON) {
            return;
        }
        ambientEnergy.merge(element, amount, Double::sum);
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
