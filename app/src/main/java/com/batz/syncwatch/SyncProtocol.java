package com.batz.syncwatch;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Syncplay JSON messages; all methods run on the main application thread. */
public final class SyncProtocol {
    public interface Listener {
        void send(JSONObject message);
        void connected(String username);
        void remote(double seconds, boolean paused, boolean seek, double latency);
        void participants(String text);
        void chat(String text);
        void error(String message);
    }

    private final Listener listener;
    private final String room;
    private final Map<String, JSONObject> users = new LinkedHashMap<>();
    private int pendingClient, nextClient, pendingServer;
    private double localPosition, rtt;
    private boolean localPaused = true, loaded;
    private JSONObject file;
    private String username;
    private boolean logged;

    public SyncProtocol(String room, Listener listener) {
        this.room = room;
        this.listener = listener;
    }

    public static JSONObject object(Object... pairs) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < pairs.length; i += 2)
                result.put((String) pairs[i], pairs[i + 1] == null ? JSONObject.NULL : pairs[i + 1]);
        } catch (JSONException e) { throw new IllegalArgumentException(e); }
        return result;
    }

    public void hello(String name) {
        username = name;
        listener.send(object("Hello", object("username", name, "room", object("name", room),
                "version", "1.2.255", "realversion", "1.7.3", "features", object(
                "sharedPlaylists", false, "chat", true, "readiness", true,
                "managedRooms", false, "persistentRooms", true, "featureList", true,
                "uiMode", "GUI"))));
    }

    public void local(double seconds, boolean paused, boolean hasMedia) {
        localPosition = Math.max(0, seconds);
        localPaused = paused;
        loaded = hasMedia;
    }

    public void file(String name, long size, double duration) {
        file = object("name", name, "size", Math.max(0, size), "duration", Math.max(0, duration));
        if (logged) listener.send(object("Set", object("file", file)));
    }

    public void change(boolean seek) {
        if (!logged || !loaded) return;
        pendingClient = ++nextClient;
        sendState(null, seek, true);
    }

    public void ready(boolean value) {
        if (logged) listener.send(object("Set", object("ready", object(
                "isReady", value, "manuallyInitiated", true))));
    }

    public void chat(String text) { if (logged) listener.send(object("Chat", text)); }

    public void receive(JSONObject message) {
        if (message.has("Error")) {
            JSONObject error = message.optJSONObject("Error");
            listener.error(error == null ? "Resposta inválida do servidor" : error.optString("message", "Erro do servidor"));
            return;
        }
        JSONObject hello = message.optJSONObject("Hello");
        if (hello != null) {
            username = hello.optString("username", username);
            logged = true;
            listener.connected(username);
            if (file != null) listener.send(object("Set", object("file", file)));
            listener.send(object("List", null));
        }
        JSONObject list = message.optJSONObject("List");
        if (list != null) {
            users.clear();
            JSONObject roomUsers = list.optJSONObject(room);
            if (roomUsers != null) {
                Iterator<String> names = roomUsers.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    users.put(name, roomUsers.optJSONObject(name));
                }
            }
            showUsers();
        }
        JSONObject set = message.optJSONObject("Set");
        if (set != null) {
            JSONObject updated = set.optJSONObject("user");
            if (updated != null) {
                Iterator<String> names = updated.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    JSONObject data = updated.optJSONObject(name);
                    if (data == null) continue;
                    JSONObject event = data.optJSONObject("event");
                    JSONObject userRoom = data.optJSONObject("room");
                    if ((event != null && event.has("left")) ||
                            (userRoom != null && !room.equals(userRoom.optString("name")))) {
                        users.remove(name);
                    } else {
                        JSONObject merged = users.getOrDefault(name, new JSONObject());
                        Iterator<String> keys = data.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            try { merged.put(key, data.opt(key)); } catch (JSONException ignored) { }
                        }
                        users.put(name, merged);
                    }
                }
                showUsers();
            }
            JSONObject ready = set.optJSONObject("ready");
            if (ready != null) {
                String name = ready.optString("username");
                JSONObject data = users.get(name);
                if (data != null) {
                    try { data.put("isReady", ready.optBoolean("isReady")); } catch (JSONException ignored) { }
                    showUsers();
                }
            }
        }
        JSONObject chat = message.optJSONObject("Chat");
        if (chat != null) listener.chat(chat.optString("username") + ": " + chat.optString("message"));
        JSONObject state = message.optJSONObject("State");
        if (state != null) handleState(state);
    }

    private void handleState(JSONObject state) {
        JSONObject ignore = state.optJSONObject("ignoringOnTheFly");
        if (ignore != null) {
            if (ignore.has("server")) {
                pendingServer = ignore.optInt("server");
                pendingClient = 0;
            } else if (ignore.optInt("client", -1) == pendingClient) {
                pendingClient = 0;
            }
        }
        JSONObject ping = state.optJSONObject("ping");
        if (ping != null && ping.has("clientLatencyCalculation")) {
            double elapsed = System.currentTimeMillis() / 1000.0 - ping.optDouble("clientLatencyCalculation");
            if (Double.isFinite(elapsed) && elapsed >= 0 && elapsed < 10)
                rtt = rtt == 0 ? elapsed : rtt * 0.8 + elapsed * 0.2;
        }
        JSONObject play = state.optJSONObject("playstate");
        if (pendingClient == 0 && play != null && !play.isNull("paused")) {
            listener.remote(play.optDouble("position", 0), play.optBoolean("paused", true),
                    play.optBoolean("doSeek", false), rtt / 2);
        }
        sendState(ping, false, false);
    }

    private void sendState(JSONObject incomingPing, boolean seek, boolean stateChange) {
        JSONObject state = new JSONObject();
        try {
            if (loaded && (pendingClient == 0 || stateChange || pendingServer != 0)) {
                state.put("playstate", object("position", localPosition, "paused", localPaused, "doSeek", seek));
            }
            JSONObject ping = object("clientLatencyCalculation", System.currentTimeMillis() / 1000.0, "clientRtt", rtt);
            if (incomingPing != null && incomingPing.has("latencyCalculation"))
                ping.put("latencyCalculation", incomingPing.opt("latencyCalculation"));
            state.put("ping", ping);
            if (pendingServer != 0 || pendingClient != 0) {
                JSONObject ignoring = new JSONObject();
                if (pendingServer != 0) ignoring.put("server", pendingServer);
                if (pendingClient != 0) ignoring.put("client", pendingClient);
                state.put("ignoringOnTheFly", ignoring);
                pendingServer = 0;
            }
        } catch (JSONException e) { listener.error("Mensagem de sincronização inválida"); return; }
        listener.send(object("State", state));
    }

    private void showUsers() {
        StringBuilder text = new StringBuilder("Sala: " + room + " · " + users.size() + " participante(s)");
        for (Map.Entry<String, JSONObject> entry : users.entrySet()) {
            JSONObject data = entry.getValue();
            JSONObject media = data == null ? null : data.optJSONObject("file");
            String name = media == null ? "sem vídeo" : media.optString("name", "sem vídeo");
            text.append("\n").append(entry.getKey()).append(data != null && data.optBoolean("isReady") ? " ✓" : " ○")
                    .append(" — ").append(name);
        }
        listener.participants(text.toString());
    }
}
