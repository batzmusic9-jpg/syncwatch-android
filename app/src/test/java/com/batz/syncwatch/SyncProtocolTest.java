package com.batz.syncwatch;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static com.batz.syncwatch.SyncProtocol.object;
import static org.junit.Assert.*;

public class SyncProtocolTest {
    static class Recorder implements SyncProtocol.Listener {
        final List<JSONObject> messages = new ArrayList<>();
        int remoteCalls;
        double position;
        boolean paused;
        String participants, error, chat;
        public void send(JSONObject value) { messages.add(value); }
        public void connected(String value) { }
        public void remote(double value, boolean pause, boolean seek, double delay) {
            remoteCalls++; position = value; paused = pause;
        }
        public void participants(String value) { participants = value; }
        public void chat(String value) { chat = value; }
        public void error(String value) { error = value; }
        JSONObject state() { return messages.get(messages.size() - 1).optJSONObject("State"); }
    }
    private SyncProtocol session(Recorder out) {
        SyncProtocol protocol = new SyncProtocol("movie", out);
        protocol.hello("Batz");
        protocol.receive(object("Hello", object("username", "Batz")));
        protocol.local(10, true, true);
        return protocol;
    }

    @Test public void localPauseIsNotOverwrittenUntilMatchingAcknowledgement() {
        Recorder out = new Recorder();
        SyncProtocol protocol = session(out);
        protocol.change(false);
        JSONObject change = out.state();
        assertTrue(change.optJSONObject("playstate").optBoolean("paused"));
        int sequence = change.optJSONObject("ignoringOnTheFly").optInt("client");
        JSONObject stale = object("playstate", object("position", 1, "paused", false),
                "ping", object("latencyCalculation", 123.5));
        protocol.receive(object("State", stale));
        assertEquals(0, out.remoteCalls);
        assertFalse(out.state().has("playstate"));
        assertEquals(123.5, out.state().optJSONObject("ping").optDouble("latencyCalculation"), 0.001);
        protocol.receive(object("State", object("ignoringOnTheFly", object("client", sequence - 1),
                "playstate", object("position", 2, "paused", false))));
        assertEquals(0, out.remoteCalls);
        protocol.receive(object("State", object("ignoringOnTheFly", object("client", sequence),
                "playstate", object("position", 10, "paused", true))));
        assertEquals(1, out.remoteCalls);
        assertTrue(out.paused);
    }

    @Test public void forcedServerSeekWinsAndIsAcknowledgedOnce() {
        Recorder out = new Recorder();
        SyncProtocol protocol = session(out);
        protocol.change(true);
        protocol.receive(object("State", object("ignoringOnTheFly", object("server", 7),
                "playstate", object("position", 80, "paused", false, "doSeek", true))));
        assertEquals(80, out.position, 0.001);
        assertEquals(7, out.state().optJSONObject("ignoringOnTheFly").optInt("server"));
        protocol.receive(object("State", object("playstate", object("position", 81, "paused", false))));
        assertFalse(out.state().has("ignoringOnTheFly"));
    }

    @Test public void readinessAndRoomMembershipAreUpdatedWithoutLeakingOtherRooms() {
        Recorder out = new Recorder();
        SyncProtocol protocol = session(out);
        protocol.receive(object("List", object("movie", object("Batz", object("file", object("name", "film.mp4"))),
                "other", object("outsider", object("file", object())))));
        assertTrue(out.participants.contains("film.mp4"));
        assertFalse(out.participants.contains("outsider"));
        protocol.receive(object("Set", object("ready", object("username", "Batz", "isReady", true))));
        assertTrue(out.participants.contains("✓"));
        protocol.receive(object("Set", object("user", object("Batz", object("room", object("name", "elsewhere"))))));
        assertTrue(out.participants.contains("0 participante"));
    }

    @Test public void noMediaSendsHeartbeatWithoutInventingPlaybackState() {
        Recorder out = new Recorder();
        SyncProtocol protocol = session(out);
        protocol.local(0, true, false);
        protocol.receive(object("State", object("ping", object("latencyCalculation", 5))));
        assertFalse(out.state().has("playstate"));
        assertTrue(out.state().has("ping"));
    }

    @Test public void malformedErrorAndChatAreSafe() {
        Recorder out = new Recorder();
        SyncProtocol protocol = session(out);
        protocol.receive(object("Error", "bad response"));
        assertNotNull(out.error);
        protocol.receive(object("Chat", object("username", "friend", "message", "Olá")));
        assertEquals("friend: Olá", out.chat);
    }

    @Test public void syncPolicyCompensatesLatencyAndIgnoresInvalidNumbers() {
        assertEquals(10400, SyncPolicy.targetMillis(10, false, 0.4));
        assertEquals(10000, SyncPolicy.targetMillis(10, true, 0.4));
        assertEquals(0, SyncPolicy.targetMillis(Double.NaN, true, 0));
        assertEquals(12000, SyncPolicy.targetMillis(10, false, 20));
        assertFalse(SyncPolicy.shouldSeek(10000, 10800, false, false));
        assertTrue(SyncPolicy.shouldSeek(10000, 11000, false, false));
        assertTrue(SyncPolicy.shouldSeek(10000, 10010, true, false));
        assertTrue(SyncPolicy.shouldSeek(10000, 10010, false, true));
    }
}
