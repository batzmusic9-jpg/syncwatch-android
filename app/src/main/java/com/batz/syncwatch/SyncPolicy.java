package com.batz.syncwatch;

/** Millisecond arithmetic independent of the player and network. */
public final class SyncPolicy {
    private SyncPolicy() {}

    public static long targetMillis(double seconds, boolean paused, double latencySeconds) {
        if (!Double.isFinite(seconds) || seconds < 0) seconds = 0;
        double delay = paused || !Double.isFinite(latencySeconds)
                ? 0 : Math.max(0, Math.min(2, latencySeconds));
        return Math.max(0, Math.round((seconds + delay) * 1000));
    }

    public static boolean shouldSeek(long local, long target, boolean explicitSeek, boolean first) {
        return first || explicitSeek || Math.abs(local - target) >= 1000;
    }
}
