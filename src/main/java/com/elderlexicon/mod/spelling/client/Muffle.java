package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.logging.LogUtils;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.client.event.sound.SoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Muffles the world's sounds while the mage is in trance, as if under water (book, chapter VI): a low-pass filter on
 * every sound playing, cutting the high notes, as deeply as the trance has gone. It uses OpenAL's effects extension;
 * where the sound card lacks it, the world simply is not muffled. The trance's own sounds are left clear.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class Muffle {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** At the deepest, how loud what is muffled stays and how much of its high notes is left. */
    private static final float DEEPEST_GAIN = 0.8F;
    private static final float DEEPEST_HIGH_GAIN = 0.25F;

    /** Sounds playing, by the channel they play on. */
    private static final Map<Channel, Boolean> CHANNELS = Collections.synchronizedMap(new WeakHashMap<>());
    /** The trance's own sounds, heard clear. */
    private static final Set<SoundInstance> OWN = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static volatile boolean on;
    private static float depth;
    private static int filter;
    private static boolean unavailable;
    private static Field sourceField;

    private Muffle() {
    }

    /** Keeps {@code sound} clear of the muffling. */
    static void spare(SoundInstance sound) {
        OWN.add(sound);
    }

    /**
     * How deep the muffling goes, from 0 (the world heard clearly) to 1. A filter is copied into each sound when set on
     * it, so every sound playing is given it again when the depth changes.
     */
    static void setDepth(float wanted) {
        float next = Math.max(0.0F, Math.min(1.0F, wanted));
        boolean muffled = next > 0.001F;
        if (!muffled && !on) {
            return;
        }
        if (muffled && Math.abs(next - depth) < 0.01F && on) {
            return;
        }
        depth = next;
        on = muffled;
        if (muffled && !tune()) {
            return;
        }
        for (Channel channel : new ArrayList<>(CHANNELS.keySet())) {
            apply(channel, muffled);
        }
    }

    /** Sets the filter to the depth now; false when it cannot be had. */
    private static boolean tune() {
        if (unavailable) {
            return false;
        }
        try {
            if (!ensureFilter()) {
                return false;
            }
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, 1.0F - (1.0F - DEEPEST_GAIN) * depth);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, 1.0F - (1.0F - DEEPEST_HIGH_GAIN) * depth);
            return true;
        } catch (Throwable problem) {
            unavailable = true;
            LOGGER.warn("Could not muffle sounds in trance; they will be heard clearly", problem);
            return false;
        }
    }

    @SubscribeEvent
    public static void played(PlaySoundSourceEvent event) {
        track(event);
    }

    @SubscribeEvent
    public static void streamed(PlayStreamingSourceEvent event) {
        track(event);
    }

    private static void track(SoundEvent.SoundSourceEvent event) {
        if (OWN.contains(event.getSound())) {
            return;
        }
        CHANNELS.put(event.getChannel(), Boolean.TRUE);
        if (on) {
            apply(event.getChannel(), true);
        }
    }

    private static void apply(Channel channel, boolean muffled) {
        if (unavailable) {
            return;
        }
        try {
            int source = sourceOf(channel);
            if (muffled && !ensureFilter()) {
                return;
            }
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, muffled ? filter : EXTEfx.AL_FILTER_NULL);
            AL10.alGetError(); // a sound that already ended has no source left; that is fine
        } catch (Throwable problem) {
            unavailable = true;
            LOGGER.warn("Could not muffle sounds in trance; they will be heard clearly", problem);
        }
    }

    private static boolean ensureFilter() {
        if (filter != 0) {
            return true;
        }
        long device = ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext());
        if (!ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
            unavailable = true;
            LOGGER.info("This sound device has no effects extension: sounds in trance will not be muffled");
            return false;
        }
        filter = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        return true;
    }

    /** The OpenAL source a channel plays through: its first number field (the game keeps it private). */
    private static int sourceOf(Channel channel) throws ReflectiveOperationException {
        if (sourceField == null) {
            for (Field field : Channel.class.getDeclaredFields()) {
                if (field.getType() == int.class && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    sourceField = field;
                    break;
                }
            }
            if (sourceField == null) {
                throw new NoSuchFieldException("Channel source");
            }
        }
        return sourceField.getInt(channel);
    }
}
