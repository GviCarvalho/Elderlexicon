package com.elderlexicon.mod.spell;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * A spell was cast where others could see it: whoever cast it, and the words it said (docs/feiticeiro-design.md, 4). A
 * sorcerer that sees a mage cast something that works writes the sentence down. Posted on the Forge bus after a
 * spell is cast with success.
 */
public final class SpellWitnessedEvent extends Event {

    private final LivingEntity caster;
    private final List<String> words;

    public SpellWitnessedEvent(LivingEntity caster, List<String> words) {
        this.caster = caster;
        this.words = List.copyOf(words);
    }

    public LivingEntity caster() {
        return caster;
    }

    /** The words as they were said, before the scene was bound ({@code ego}, not a mark of its own). */
    public List<String> words() {
        return words;
    }
}
