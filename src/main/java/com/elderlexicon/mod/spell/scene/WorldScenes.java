package com.elderlexicon.mod.spell.scene;

import com.elderlexicon.mod.spell.function.EmissionRenderer;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Holds one {@link SpellScene} per world and runs its laws every tick, so spells interact
 * regardless of who cast them or when. All calls happen on the server thread.
 */
public final class WorldScenes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final class WorldScene {
        private final SpellScene scene = new SpellScene();
        private final SceneLaws laws = new SceneLaws();
    }

    private static final Map<ServerLevel, WorldScene> SCENES = new WeakHashMap<>();

    private WorldScenes() {
    }

    /** The stage every spell cast in this world registers on. */
    public static SpellScene sceneOf(ServerLevel level) {
        return SCENES.computeIfAbsent(level, key -> new WorldScene()).scene;
    }

    public static void forget(ServerLevel level) {
        SCENES.remove(level);
    }

    /** Draws the beams, applies the laws and turns the outcomes into events in the world. */
    public static void tick(ServerLevel level) {
        WorldScene world = SCENES.get(level);
        if (world == null) {
            return;
        }
        long tick = level.getServer().getTickCount();
        world.scene.prune(tick);
        if (world.scene.isEmpty()) {
            world.laws.reset();
            return;
        }
        try {
            EmissionRenderer.render(level, world.scene, tick);
            List<Outcome> outcomes = world.laws.tick(world.scene, tick);
            for (Outcome outcome : outcomes) {
                SceneOutcomeApplier.apply(level, outcome);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Spell scene step failed", exception);
        }
    }
}
