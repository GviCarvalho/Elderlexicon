package com.elderlexicon.mod.caster;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.SpellWitnessedEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** A creature that casts the Old Tongue with a mind of its own ({@link CasterMind}): the Sorcerer, the Wizard. */
public interface RuneCaster {

    /** How far one sees a mage cast, to learn from it and write the sentence down. */
    double WITNESS_REACH = 16.0D;

    CasterMind casterMind();

    /** Whether it has its book open in its hands now, reading or writing: on the client too (how it is drawn). */
    default boolean readingBook() {
        return false;
    }

    /**
     * Its degree as the clients see it: how long its beard is (docs/mago-design.md, 5i). An apprentice has none, a
     * master a black one, an elder a long white one.
     */
    com.elderlexicon.mod.mind.Degree shownDegree();

    /** Whether it is in its trance, its arms raised: on the client too. */
    boolean entranced();

    /** Whether it points its focus where it aims, while what it read is released: on the client too. */
    boolean pointing();

    /**
     * A mage cast where casters could see it (docs/feiticeiro-design.md, 4): each that sees it understands the runes of
     * it a little more and writes the sentence down, as much of it as it can read. A caster learns from another caster
     * too: two who fight teach each other.
     */
    @Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
    final class Witnesses {

        private Witnesses() {
        }

        @SubscribeEvent
        public static void onWitnessed(SpellWitnessedEvent event) {
            LivingEntity caster = event.caster();
            if (caster.level().isClientSide()) {
                return;
            }
            List<Mob> seeing = caster.level().getEntitiesOfClass(Mob.class,
                    caster.getBoundingBox().inflate(WITNESS_REACH),
                    mob -> mob != caster && mob instanceof RuneCaster && mob.isAlive() && mob.hasLineOfSight(caster));
            for (Mob mob : seeing) {
                CasterMind mind = ((RuneCaster) mob).casterMind();
                mind.witness(event.words());
                mind.copy(event.words());
            }
        }
    }
}
