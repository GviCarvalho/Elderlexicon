package com.elderlexicon.mod.ligabis.world;

/**
 * Marks the stretch of code in which the engine itself is hurting or killing something. Damage that
 * happens inside it is reported to the engine as coming from a link, so it is never reflected again.
 * Everything runs on the server thread, so a plain counter is enough.
 */
public final class LigabisGuard {

    private static int depth;

    private LigabisGuard() {
    }

    public static boolean active() {
        return depth > 0;
    }

    public static void run(Runnable action) {
        depth++;
        try {
            action.run();
        } finally {
            depth--;
        }
    }
}
