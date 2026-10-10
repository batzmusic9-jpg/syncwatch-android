package com.batz.syncwatch;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PlaybackIntentTest {
    @Test public void delayedNativeEventsCannotEchoRemoteCommands() {
        List<Boolean> roomChanges = new ArrayList<>();
        boolean[] applyingRemote = {true};
        PlaybackIntent intent = new PlaybackIntent(seek -> { if (!applyingRemote[0]) roomChanges.add(seek); });
        intent.seek(600000);
        intent.play(true);
        applyingRemote[0] = false;
        intent.observedPlaying(true);
        intent.observedPlaying(false); // late bootstrap pause
        intent.observedPlaying(true);
        intent.seekApplied();
        assertTrue(roomChanges.isEmpty());
        assertTrue(intent.wanted());
        intent.play(false); // real user action after the remote operation
        intent.seek(700000);
        assertEquals(java.util.Arrays.asList(false, true), roomChanges);
    }
    @Test public void queuedSeekAndPauseSurvivePreparation() {
        PlaybackIntent intent = new PlaybackIntent(seek -> {});
        intent.seek(12000);
        intent.observedPlaying(true);
        assertFalse(intent.wanted());
        assertEquals(12000, intent.pending());
        intent.seekApplied();
        assertEquals(-1, intent.pending());
        intent.reset();
        assertFalse(intent.wanted());
        assertFalse(intent.observed());
    }
    @Test public void smallDriftDoesNotSeekAndExplicitSeekStillWorks() {
        assertFalse(SyncPolicy.shouldSeek(600000, 599700, false, false));
        assertTrue(SyncPolicy.shouldSeek(600000, 599700, true, false));
        assertTrue(SyncPolicy.shouldSeek(600000, 599700, false, true));
        assertTrue(SyncPolicy.shouldSeek(600000, 599000, false, false));
        assertEquals(599700, SyncPolicy.targetMillis(599.7, true, 0.2));
    }
}
