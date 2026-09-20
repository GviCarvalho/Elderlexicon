package com.elderlexicon.mod.spell.module;

import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCostModuleTest {

    private static final double EPSILON = 1.0E-4D;

    @Test
    void limitsOverflowContributionToTenPercent() {
        TrackingVitaGateway gateway = new TrackingVitaGateway(3.0D);
        double remaining = SpellCostModule.settleElementalCost(gateway, null, VitaElement.IGNI, 5.0D);

        assertEquals(0.5D, gateway.lastOverflowRequest, EPSILON, "Overflow request should cap at 10% of the cost");
        assertEquals(0.5D, gateway.overflowSpent, EPSILON, "Only the requested slice should be consumed");
        assertEquals(4.5D, remaining, EPSILON, "Remaining cost should skip overflow once the slice is used");
        assertTrue(gateway.overflowCalled, "Overflow stage must run before any other drain logic");
    }

    private static final class TrackingVitaGateway implements SpellCostModule.VitaGateway {

        private final double initialOverflow;
        private double overflowSpent;
        private double lastOverflowRequest;
        private boolean overflowCalled;

        private TrackingVitaGateway(double overflow) {
            this.initialOverflow = overflow;
        }

        @Override
        public double consumeElementExcess(ServerPlayer player, VitaElement element, double umuAmount) {
            overflowCalled = true;
            lastOverflowRequest = umuAmount;
            double spent = Math.min(initialOverflow - overflowSpent, umuAmount);
            overflowSpent += spent;
            return Math.max(0.0D, umuAmount - spent);
        }

        @Override
        public void consumeLifeEnergy(ServerPlayer player, float hpDamage, VitaElement element) {
            // Not needed for this test since overflow + reserve cover full cost
        }
    }
}
