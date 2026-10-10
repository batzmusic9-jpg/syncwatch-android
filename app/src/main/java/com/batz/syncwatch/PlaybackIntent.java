package com.batz.syncwatch;

/** Logical commands are synchronous. Decoder notifications are observations, never commands. */
final class PlaybackIntent {
    interface Commands { void changed(boolean seek); }
    private final Commands commands;
    private boolean wanted, observed;
    private long pending = -1;
    PlaybackIntent(Commands commands) { this.commands = commands; }
    boolean wanted() { return wanted; }
    boolean observed() { return observed; }
    void reset() { wanted = false; observed = false; pending = -1; }
    void play(boolean value) {
        if (wanted == value) return;
        wanted = value;
        commands.changed(false);
    }
    void seek(long value) { pending = Math.max(0, value); commands.changed(true); }
    long pending() { return pending; }
    void observedPlaying(boolean value) { observed = value; }
    void seekApplied() { pending = -1; }
}
