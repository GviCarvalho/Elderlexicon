package com.elderlexicon.mod.nahva.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.nahva.Nahva;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Nahvá is seen through a fog, so each realm is a world of its own with nothing beyond it; each realm has the color of
 * its idea (the realm of igni glows like embers; the tower stands in the red of Lig'dar, its top lost in the dark; the
 * mage's island floats in a mist the color of dusk).
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class NahvaFog {

    private static final float NEAR = 6.0F;
    private static final float FAR = 72.0F;
    /** The tower is seen from far off, climbing into the fog until it is lost in it. */
    private static final float TOWER_FAR = 320.0F;
    /** The island is soft at its edges: past it, nothing, in a mist the color of dusk. */
    private static final float KERN_FAR = 110.0F;

    private NahvaFog() {
    }

    private static boolean inNahva() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && Nahva.isNahva(minecraft.level);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!inNahva()) {
            return;
        }
        String realm = Nahva.realmAt(event.getCamera().getPosition().z);
        boolean tower = "tower".equals(realm);
        boolean kern = "kern".equals(realm);
        event.setNearPlaneDistance(tower ? 24.0F : kern ? 16.0F : NEAR);
        event.setFarPlaneDistance(Math.min(tower ? TOWER_FAR : kern ? KERN_FAR : FAR, event.getFarPlaneDistance()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inNahva()) {
            return;
        }
        String realm = Nahva.realmAt(event.getCamera().getPosition().z);
        if ("igni".equals(realm)) {
            event.setRed(0.55F);
            event.setGreen(0.20F);
            event.setBlue(0.06F);
        } else if ("kern".equals(realm)) {
            event.setRed(0.42F);
            event.setGreen(0.34F);
            event.setBlue(0.46F);
        } else if ("tower".equals(realm)) {
            // Lig'dar's red light, deep in the dark.
            event.setRed(0.22F);
            event.setGreen(0.04F);
            event.setBlue(0.04F);
        } else {
            event.setRed(0.12F);
            event.setGreen(0.08F);
            event.setBlue(0.18F);
        }
    }
}
