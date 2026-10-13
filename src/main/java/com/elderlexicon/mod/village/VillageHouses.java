package com.elderlexicon.mod.village;

import com.elderlexicon.mod.ElderLexicon;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts the Wizard's house among the houses a village may build (docs/mago-design.md, 5f): into the houses pool of each
 * kind of village, as the game's own houses are, when the server is about to start. The pools are the game's, made
 * from its data: what they hold is not open to be added to, so it is reached by reflection.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class VillageHouses {

    /** How often the house comes up among a village's houses (the game's houses weigh 1 to 4 each). */
    public static final int WEIGHT = 2;
    private static final Logger LOGGER = LogUtils.getLogger();

    private VillageHouses() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Registry<StructureTemplatePool> pools = event.getServer().registryAccess()
                .registryOrThrow(Registries.TEMPLATE_POOL);
        for (WizardHouse.Style style : WizardHouse.Style.values()) {
            StructureTemplatePool pool = pools.get(style.housesPool());
            if (pool == null) {
                continue;
            }
            add(pool, StructurePoolElement.legacy(style.template().toString())
                    .apply(StructureTemplatePool.Projection.RIGID), WEIGHT);
        }
    }

    /** Adds {@code element} to {@code pool}, {@code weight} times over, as the pool's own are. */
    @SuppressWarnings("unchecked")
    static void add(StructureTemplatePool pool, StructurePoolElement element, int weight) {
        try {
            Field templates = ObfuscationReflectionHelper.findField(StructureTemplatePool.class, "f_210560_");
            List<StructurePoolElement> drawn = (List<StructurePoolElement>) templates.get(pool);
            for (int i = 0; i < weight; i++) {
                drawn.add(element);
            }
            Field raw = ObfuscationReflectionHelper.findField(StructureTemplatePool.class, "f_210559_");
            List<Pair<StructurePoolElement, Integer>> weighted = new ArrayList<>(
                    (List<Pair<StructurePoolElement, Integer>>) raw.get(pool));
            weighted.add(Pair.of(element, weight));
            raw.set(pool, weighted);
        } catch (IllegalAccessException | RuntimeException failure) {
            LOGGER.error("Unable to add the wizard's house to {}", pool, failure);
        }
    }

    /** Whether {@code pool} holds the house of {@code style} (for a test). */
    @SuppressWarnings("unchecked")
    public static boolean holds(StructureTemplatePool pool, WizardHouse.Style style) {
        try {
            Field templates = ObfuscationReflectionHelper.findField(StructureTemplatePool.class, "f_210560_");
            return ((List<StructurePoolElement>) templates.get(pool)).stream()
                    .anyMatch(element -> element.toString().contains(style.template().toString()));
        } catch (IllegalAccessException exception) {
            return false;
        }
    }
}
