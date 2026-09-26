package com.elderlexicon.mod.spell.module;

import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCostModuleTest {

    private static final double EPSILON = 1.0E-4D;

    @Test
    void aCostWorthLessThanAPointStillComesFromExperience() {
        SpellCostModule.ExperienceCharge first = SpellCostModule.chargeExperience(100, 0.12D, 0.0D);

        assertEquals(1, first.points());
        assertEquals(0.2D, first.carried(), EPSILON, "the fifth of a point is owed, not taken from food");
        assertEquals(0.0D, first.unpaidUmu(), EPSILON);

        double carried = first.carried();
        int taken = first.points();
        for (int second = 1; second < 5; second++) {
            SpellCostModule.ExperienceCharge next = SpellCostModule.chargeExperience(100, 0.12D, carried);
            assertEquals(0.0D, next.unpaidUmu(), EPSILON);
            taken += next.points();
            carried = next.carried();
        }
        assertEquals(6, taken, "five seconds at 0.12 UMU are 6 points");
        assertEquals(0.0D, carried, EPSILON);
    }

    @Test
    void withoutEnoughExperienceTheRestIsLeftForFood() {
        SpellCostModule.ExperienceCharge charge = SpellCostModule.chargeExperience(3, 1.0D, 0.0D);

        assertEquals(3, charge.points());
        assertEquals(0.7D, charge.unpaidUmu(), EPSILON);
        assertEquals(1.0D, SpellCostModule.chargeExperience(0, 1.0D, 0.0D).unpaidUmu(), EPSILON);
    }

    @Test
    void limitsOverflowContributionToTenPercent() {
        TrackingVitaGateway gateway = new TrackingVitaGateway(3.0D);
        double remaining = SpellCostModule.settleElementalCost(gateway, null, VitaElement.IGNI, 5.0D);

        assertEquals(0.5D, gateway.lastOverflowRequest, EPSILON, "Overflow request should cap at 10% of the cost");
        assertEquals(0.5D, gateway.overflowSpent, EPSILON, "Only the requested slice should be consumed");
        assertEquals(4.5D, remaining, EPSILON, "Remaining cost should skip overflow once the slice is used");
        assertTrue(gateway.overflowCalled, "Overflow stage must run before any other drain logic");
    }

    @Test
    void focusSkipsTheVitaElementalStage() {
        TrackingVitaGateway gateway = new TrackingVitaGateway(3.0D);
        double remaining = SpellCostModule.settleElementalStage(gateway, null, VitaElement.IGNI, 5.0D, true);

        assertEquals(5.0D, remaining, EPSILON, "the whole cost is left for the other resources");
        assertTrue(!gateway.overflowCalled, "a focus must not draw elemental excess from the caster's Vita");
    }

    @Test
    void withoutAFocusTheElementalStageStillRuns() {
        TrackingVitaGateway gateway = new TrackingVitaGateway(3.0D);
        double remaining = SpellCostModule.settleElementalStage(gateway, null, VitaElement.IGNI, 5.0D, false);

        assertEquals(4.5D, remaining, EPSILON);
        assertTrue(gateway.overflowCalled);
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
