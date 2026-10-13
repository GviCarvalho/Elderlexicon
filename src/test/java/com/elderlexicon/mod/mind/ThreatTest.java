package com.elderlexicon.mod.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A caster does not open its trance with a foe that strikes before it ends (docs/feiticeiro-design.md, 3.9). */
class ThreatTest {

    private static final int TRANCE = 40;

    @Test
    void aZombieAtHandStrikesBeforeTheTranceEnds() {
        double zombie = 0.23D * Threat.PER_SPEED;
        assertTrue(Threat.ticksToBlow(3.0D, Threat.BLOW, zombie) < TRANCE);
        assertTrue(Threat.ticksToBlow(12.0D, Threat.BLOW, zombie) > TRANCE);
    }

    @Test
    void anElderWantsTheFoeFartherThanAMaster() {
        double zombie = 0.23D * Threat.PER_SPEED;
        double master = Threat.safeDistance(Threat.BLOW, zombie, TRANCE, Degree.MASTER.foresight());
        double elder = Threat.safeDistance(Threat.BLOW, zombie, TRANCE, Degree.ELDER.foresight());
        assertTrue(elder > master, master + " / " + elder);
        assertTrue(master > 6.0D && master < 9.0D, "master " + master);
    }

    @Test
    void whatItSawComeWeighsOverWhatItGuessed() {
        Threat threat = new Threat();
        for (int i = 0; i < 20; i++) {
            threat.saw(0.3D);
        }
        assertTrue(threat.speed(0.1D) > 0.25D);
        threat.forget();
        assertEquals(0.1D, threat.speed(0.1D));
    }

    @Test
    void aFoeStandingStillNeverStrikes() {
        assertEquals(Double.POSITIVE_INFINITY, Threat.ticksToBlow(3.0D, Threat.BLOW, 0.0D));
    }

    @Test
    void anApprenticeReadsWhatItJustReadAndAnElderTiresOfIt() {
        assertEquals(1.0D, Degree.APPRENTICE.habit(3));
        assertTrue(Degree.ELDER.habit(2) < Degree.MASTER.habit(2));
        assertTrue(Degree.ELDER.habit(100) >= 0.3D, "it never gives up on it for that alone");
    }
}
