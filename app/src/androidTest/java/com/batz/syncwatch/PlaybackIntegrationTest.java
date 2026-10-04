package com.batz.syncwatch;

import android.net.Uri;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import androidx.media3.common.Player;
import androidx.media3.common.text.CueGroup;
import android.view.View;
import android.widget.ScrollView;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static com.batz.syncwatch.SyncProtocol.object;
import static org.junit.Assert.*;

@UnstableApi
@RunWith(AndroidJUnit4.class)
public class PlaybackIntegrationTest {
    private static void setField(MainActivity activity, String name, Object value) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void invoke(MainActivity activity, String name, Class<?>[] types, Object... args) {
        try { Method m = MainActivity.class.getDeclaredMethod(name, types); m.setAccessible(true); m.invoke(activity, args); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static Object field(MainActivity activity, String name) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static ExoPlayer player(MainActivity activity) { return (ExoPlayer) field(activity, "player"); }
    private static void send(Socket peer, JSONObject value) throws Exception {
        peer.getOutputStream().write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
        peer.getOutputStream().flush();
    }
    private static JSONObject nextState(LinkedBlockingQueue<JSONObject> queue) throws Exception {
        long until = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < until) {
            JSONObject message = queue.poll(1, TimeUnit.SECONDS);
            if (message != null && message.has("State")) return message.getJSONObject("State");
        }
        throw new AssertionError("No state message received from the Android player");
    }
    private static void waitUntilReady(ActivityScenario<MainActivity> scenario) {
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 80; i++) {
            scenario.onActivity(a -> ready.set(player(a).getPlaybackState() == Player.STATE_READY));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail("Real video did not prepare in the emulator");
    }

    @Test public void videoAndSocketSynchronizeInBothDirections() throws Exception {
        File video = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "sync-test.mp4");
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("sync-test.mp4");
             FileOutputStream output = new FileOutputStream(video)) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
        LinkedBlockingQueue<JSONObject> queue = new LinkedBlockingQueue<>();
        AtomicReference<Socket> accepted = new AtomicReference<>();
        AtomicReference<Throwable> serverError = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0);
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Thread simulated = new Thread(() -> {
                try {
                    Socket peer = server.accept();
                    accepted.set(peer);
                    peer.setSoTimeout(15000);
                    JSONObject tls = new JSONObject(SyncConnection.readLine(peer.getInputStream()));
                    assertTrue(tls.has("TLS"));
                    send(peer, object("TLS", object("startTLS", "false")));
                    JSONObject hello = new JSONObject(SyncConnection.readLine(peer.getInputStream()));
                    assertEquals("Android-test", hello.getJSONObject("Hello").getString("username"));
                    send(peer, object("Hello", object("username", "Android-test", "room", object("name", "test-room"), "version", "1.7.3")));
                    while (!peer.isClosed()) queue.add(new JSONObject(SyncConnection.readLine(peer.getInputStream())));
                } catch (Exception e) {
                    if (accepted.get() == null || !accepted.get().isClosed()) serverError.set(e);
                }
            }, "emulator-server");
            simulated.setDaemon(true);
            simulated.start();
            scenario.onActivity(a -> {
                try {
                    Method open = MainActivity.class.getDeclaredMethod("openVideo", Uri.class, long.class);
                    open.setAccessible(true);
                    open.invoke(a, Uri.fromFile(video), 0L);
                } catch (Exception e) { throw new AssertionError(e); }
            });
            waitUntilReady(scenario);
            scenario.onActivity(a -> {
                setField(a, "connectHost", "127.0.0.1");
                setField(a, "connectPort", server.getLocalPort());
                setField(a, "requireSecure", false);
                ((EditText) field(a, "name")).setText("Android-test");
                ((EditText) field(a, "room")).setText("test-room");
                ((Button) field(a, "connect")).performClick();
            });
            try {
                long until = SystemClock.elapsedRealtime() + 10000;
                boolean listed = false;
                while (SystemClock.elapsedRealtime() < until) {
                    JSONObject value = queue.poll(1, TimeUnit.SECONDS);
                    if (value != null && value.has("List")) { listed = true; break; }
                }
                assertTrue("Client handshake did not finish", listed);
                Socket peer = accepted.get();
                send(peer, object("State", object("playstate", object("position", 4, "paused", true, "doSeek", true),
                        "ignoringOnTheFly", object("server", 1))));
                assertEquals(1, nextState(queue).getJSONObject("ignoringOnTheFly").getInt("server"));
                waitUntilReady(scenario);
                scenario.onActivity(a -> {
                    assertFalse(player(a).getPlayWhenReady());
                    assertEquals(4000, player(a).getCurrentPosition(), 200);
                });
                send(peer, object("State", object("playstate", object("position", 8, "paused", false, "doSeek", true),
                        "ignoringOnTheFly", object("server", 2))));
                assertEquals(2, nextState(queue).getJSONObject("ignoringOnTheFly").getInt("server"));
                scenario.onActivity(a -> {
                    assertTrue(player(a).getPlayWhenReady());
                    assertEquals(8000, player(a).getCurrentPosition(), 800);
                    player(a).pause();
                });
                JSONObject pause = nextState(queue);
                assertTrue(pause.getJSONObject("playstate").getBoolean("paused"));
                assertTrue(pause.getJSONObject("ignoringOnTheFly").getInt("client") > 0);
                scenario.onActivity(a -> player(a).seekTo(12000));
                JSONObject seek = nextState(queue);
                assertTrue(seek.getJSONObject("playstate").getBoolean("doSeek"));
                assertEquals(12, seek.getJSONObject("playstate").getDouble("position"), 0.2);
                assertNull(serverError.get());
            } finally {
                Socket peer = accepted.get();
                if (peer != null) peer.close();
                simulated.join(2000);
            }
        }
    }

    @Test public void externalSubtitlesRenderAndFullscreenKeepsPlayer() throws Exception {
        File video = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "subtitle-video.mp4");
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("sync-test.mp4");
             FileOutputStream output = new FileOutputStream(video)) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
        File subtitle = new File(video.getParentFile(), "test.srt");
        try (FileOutputStream output = new FileOutputStream(subtitle)) {
            output.write("1\n00:00:00,000 --> 00:00:18,000\nLegenda de teste\n".getBytes(StandardCharsets.UTF_8));
        }
        AtomicBoolean visibleCue = new AtomicBoolean();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> invoke(a, "openVideo", new Class<?>[]{Uri.class, long.class}, Uri.fromFile(video), 0L));
            waitUntilReady(scenario);
            scenario.onActivity(a -> {
                player(a).addListener(new Player.Listener() {
                    @Override public void onCues(CueGroup group) {
                        for (androidx.media3.common.text.Cue cue : group.cues)
                            if (cue.text != null && cue.text.toString().contains("Legenda de teste")) visibleCue.set(true);
                    }
                });
                invoke(a, "loadSubtitle", new Class<?>[]{Uri.class}, Uri.fromFile(subtitle));
            });
            waitUntilReady(scenario);
            scenario.onActivity(a -> { player(a).seekTo(4000); player(a).play(); });
            for (int i = 0; i < 80 && !visibleCue.get(); i++) SystemClock.sleep(100);
            assertTrue("External subtitle must produce a rendered cue", visibleCue.get());
            scenario.onActivity(a -> {
                player(a).pause();
                ExoPlayer before = player(a);
                long position = before.getCurrentPosition();
                invoke(a, "setFullscreen", new Class<?>[]{boolean.class, boolean.class}, true, false);
                assertEquals(View.GONE, ((ScrollView) field(a, "scroll")).getVisibility());
                assertSame(field(a, "screen"), ((View) field(a, "playerView")).getParent());
                assertSame(before, player(a));
                assertEquals(position, player(a).getCurrentPosition(), 100);
                invoke(a, "setFullscreen", new Class<?>[]{boolean.class, boolean.class}, false, false);
                assertEquals(View.VISIBLE, ((ScrollView) field(a, "scroll")).getVisibility());
                assertSame(field(a, "videoHost"), ((View) field(a, "playerView")).getParent());
                setField(a, "subtitleOffset", 500L);
                invoke(a, "refreshSubtitles", new Class<?>[]{});
                assertEquals(position, player(a).getCurrentPosition(), 100);
            });
            waitUntilReady(scenario);
            scenario.onActivity(a -> {
                invoke(a, "clearSubtitle", new Class<?>[]{});
                invoke(a, "refreshSubtitles", new Class<?>[]{});
                assertTrue(player(a).getCurrentMediaItem().localConfiguration.subtitleConfigurations.isEmpty());
            });
        }
    }
}
