package com.elderlexicon.mod.nahva.client;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * The fever of a dream, on the dreamer's screen (docs/aprendizado-runas-design.md, 4b): each scene of each rune seen
 * through one of the game's own screen effects, the sight that swells and shrinks as a fevered breath, and the mist the
 * colour of the scene. In the void of aura there is no sound at all.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class FeverClient {

    /** How a scene is seen: the screen effect, and the colour of the mist. */
    private record Look(ResourceLocation screen, float red, float green, float blue) {
        Look(String screen, float red, float green, float blue) {
            this(new ResourceLocation("shaders/post/" + screen + ".json"), red, green, blue);
        }
    }

    /** The memories, of every rune: trails behind all that moves, in the ghost of a colour. */
    private static final Look MEMORY = new Look("phosphor", 0.40F, 0.18F, 0.48F);
    /** Each rune's two extremes, in the order of the scenes (FeverDream.Scene): the first, then the second. */
    private static final Map<String, Look[]> EXTREMES = Map.ofEntries(
            Map.entry("igni", new Look[]{new Look("wobble", 0.62F, 0.18F, 0.05F),
                    new Look("deconverge", 0.55F, 0.72F, 0.92F)}),
            // (blur shows nothing but black in this game: the water ripples instead.)
            Map.entry("aqua", new Look[]{new Look("wobble", 0.08F, 0.32F, 0.52F),
                    new Look("color_convolve", 0.78F, 0.62F, 0.34F)}),
            Map.entry("aura", new Look[]{new Look("scan_pincushion", 0.82F, 0.86F, 0.92F),
                    new Look("desaturate", 0.04F, 0.04F, 0.06F)}),
            Map.entry("firmo", new Look[]{new Look("bits", 0.34F, 0.30F, 0.25F),
                    new Look("blobs", 0.18F, 0.13F, 0.09F)}),
            // The verbs: the verb, and the verb turned round.
            Map.entry("iactare", new Look[]{new Look("bumpy", 0.55F, 0.55F, 0.52F),
                    new Look("creeper", 0.30F, 0.42F, 0.30F)}),
            Map.entry("vocant", new Look[]{new Look("spider", 0.42F, 0.24F, 0.48F),
                    new Look("invert", 0.30F, 0.12F, 0.36F)}),
            Map.entry("impediunt", new Look[]{new Look("notch", 0.12F, 0.10F, 0.14F),
                    new Look("deconverge", 0.20F, 0.16F, 0.24F)}),
            Map.entry("vertere", new Look[]{new Look("art", 0.62F, 0.42F, 0.26F),
                    new Look("desaturate", 0.40F, 0.38F, 0.34F)}),
            Map.entry("ligabis", new Look[]{new Look("pencil", 0.50F, 0.48F, 0.44F),
                    new Look("green", 0.20F, 0.26F, 0.22F)}),
            Map.entry("reframe", new Look[]{new Look("sobel", 0.30F, 0.22F, 0.14F),
                    new Look("desaturate", 0.46F, 0.46F, 0.46F)}),
            Map.entry("surgit", new Look[]{new Look("desaturate", 0.02F, 0.02F, 0.03F),
                    new Look("phosphor", 0.80F, 0.86F, 0.90F)}),
            // The filters: the measure, and the measure turned round.
            Map.entry("quantum", new Look[]{new Look("scan_pincushion", 0.46F, 0.52F, 0.40F),
                    new Look("pencil", 0.70F, 0.74F, 0.80F)}),
            Map.entry("chronos", new Look[]{new Look("phosphor", 0.62F, 0.50F, 0.30F),
                    new Look("invert", 0.24F, 0.20F, 0.18F)}),
            Map.entry("ubis", new Look[]{new Look("desaturate", 0.94F, 0.94F, 0.94F),
                    new Look("deconverge", 0.40F, 0.36F, 0.44F)}),
            Map.entry("tenet", new Look[]{new Look("notch", 0.36F, 0.20F, 0.16F),
                    new Look("blobs", 0.10F, 0.08F, 0.10F)}));

    /** What the dreamer sees now; null awake. And whether they hear anything at all. */
    private static Look look;
    private static boolean silent;

    private FeverClient() {
    }

    /** The facts, all in one mirror: seen double, and then the other way round. */
    private static final java.util.Set<String> FACTS = java.util.Set.of("ferit", "patitur", "necat", "salit", "cadit",
            "frangit", "utitur", "latet", "currit", "languet", "ardet", "mergitur");
    private static final Look[] MIRROR = {new Look("deconverge", 0.60F, 0.52F, 0.20F),
            new Look("invert", 0.30F, 0.26F, 0.10F)};

    public static void set(String rune, int scene) {
        Minecraft minecraft = Minecraft.getInstance();
        Look[] extremes = FACTS.contains(rune) ? MIRROR : EXTREMES.get(rune);
        look = scene == 1 ? MEMORY : extremes != null && (scene == 0 || scene == 2) ? extremes[scene / 2] : null;
        silent = "aura".equals(rune) && scene == 2;
        if (look == null) {
            minecraft.gameRenderer.shutdownEffect();
            return;
        }
        minecraft.gameRenderer.loadEffect(look.screen());
    }

    /** The game takes its screen effect off when the view changes (a journey between worlds): it is put back. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || look == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            look = null;
            silent = false;
            return;
        }
        PostChain shown = minecraft.gameRenderer.currentEffect();
        if (shown == null || !shown.getName().equals(look.screen().toString())) {
            minecraft.gameRenderer.loadEffect(look.screen());
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (look == null || Minecraft.getInstance().level == null) {
            return;
        }
        double time = Minecraft.getInstance().level.getGameTime() + event.getPartialTick();
        event.setFOV(event.getFOV() * (1.0D + 0.08D * Mth.sin((float) (time * 0.07D))
                + 0.03D * Mth.sin((float) (time * 0.31D))));
    }

    /** After the mist of Nahvá is coloured: the scene's colour over it. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (look == null) {
            return;
        }
        event.setRed(look.red());
        event.setGreen(look.green());
        event.setBlue(look.blue());
    }

    /** In the void, nothing is heard. */
    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (silent) {
            event.setSound(null);
        }
    }
}
