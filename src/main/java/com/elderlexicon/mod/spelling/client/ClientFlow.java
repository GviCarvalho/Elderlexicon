package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spelling.flow.FlowState;
import net.minecraft.Util;

/** Whether the local mage is in flow, as the server last said, and since when (docs/fluxo-design.md). */
public final class ClientFlow {

    private static boolean flowing;
    private static long sinceMs;

    private ClientFlow() {
    }

    public static void set(boolean now) {
        if (now && !flowing) {
            sinceMs = Util.getMillis();
        }
        flowing = now;
    }

    public static boolean flowing() {
        return flowing;
    }

    public static double seconds() {
        return flowing ? (Util.getMillis() - sinceMs) / 1000.0D : 0.0D;
    }

    /** What a second of flow costs now, in UMU. */
    public static double umuPerSecond() {
        return FlowState.umuPerSecond(seconds());
    }
}
