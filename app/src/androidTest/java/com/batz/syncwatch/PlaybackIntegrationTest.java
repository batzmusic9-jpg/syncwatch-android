package com.batz.syncwatch;

import android.net.Uri;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;
import android.widget.ScrollView;
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
    private static PlaybackEngine player(MainActivity activity) { return (PlaybackEngine) field(activity, "player"); }
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
        for (int i = 0; i < 200; i++) {
            scenario.onActivity(a -> ready.set(player(a).isReady()));
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
                SystemClock.sleep(700);
                scenario.onActivity(a -> {
                    assertFalse(player(a).getPlayWhenReady());
                    assertEquals(4000, player(a).getCurrentPosition(), 200);
                    assertEquals("Native paused seek must reach the requested time", 4000,
                            ((LibVlcPlaybackEngine) player(a)).nativeTime(), 250);
                });
                send(peer, object("State", object("playstate", object("position", 8, "paused", false, "doSeek", true),
                        "ignoringOnTheFly", object("server", 2))));
                assertEquals(2, nextState(queue).getJSONObject("ignoringOnTheFly").getInt("server"));
                // Native callbacks arrive after the remote guard has been cleared. They must not echo commands.
                SystemClock.sleep(800);
                for (JSONObject value : queue) if (value.has("State"))
                    assertFalse("Remote operation echoed a local command", value.getJSONObject("State").has("ignoringOnTheFly"));
                queue.clear();
                scenario.onActivity(a -> {
                    assertTrue(player(a).getPlayWhenReady());
                    assertEquals(8000, player(a).getCurrentPosition(), 1600);
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

    private static File fixture(String asset) throws Exception {
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), asset);
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(asset);
             FileOutputStream output = new FileOutputStream(file)) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
        return file;
    }
    private static android.view.TextureView texture(View view) {
        if (view instanceof android.view.TextureView) return (android.view.TextureView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.TextureView found = texture(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private static int whitePixels(MainActivity activity) { return framePixels(activity, true); }
    private static int bluePixels(MainActivity activity) { return framePixels(activity, false); }
    private static int framePixels(MainActivity activity, boolean white) {
        android.view.TextureView view = texture((View) field(activity, "playerView"));
        if (view == null || !view.isAvailable()) return 0;
        android.graphics.Bitmap bitmap = view.getBitmap(320, 180);
        if (bitmap == null) return 0;
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            int color = bitmap.getPixel(x, y);
            if (white ? android.graphics.Color.red(color) > 180 && android.graphics.Color.green(color) > 180
                    && android.graphics.Color.blue(color) > 180 : android.graphics.Color.blue(color) > 150
                    && android.graphics.Color.red(color) < 80 && android.graphics.Color.green(color) < 80) count++;
        }
        bitmap.recycle();
        return count;
    }
    @Test public void externalSubtitlesRenderAndFullscreenKeepsPlayer() throws Exception {
        File video = fixture("sync-test.mp4");
        File subtitle = new File(video.getParentFile(), "test.srt");
        try (FileOutputStream output = new FileOutputStream(subtitle)) {
            output.write("1\n00:00:00,000 --> 00:00:18,000\nLegenda de teste\n".getBytes(StandardCharsets.UTF_8));
        }
        AtomicBoolean rendered = new AtomicBoolean();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> invoke(a, "openVideo", new Class<?>[]{Uri.class, long.class}, Uri.fromFile(video), 0L));
            waitUntilReady(scenario);
            scenario.onActivity(a -> {
                player(a).selectSubtitle(-1);
                player(a).play();
            });
            SystemClock.sleep(800);
            scenario.onActivity(a -> assertTrue("Fixture must have a plain blue frame", whitePixels(a) < 15));
            scenario.onActivity(a -> invoke(a, "loadSubtitle", new Class<?>[]{Uri.class}, Uri.fromFile(subtitle)));
            for (int i = 0; i < 100 && !rendered.get(); i++) {
                scenario.onActivity(a -> rendered.set(player(a).selectedSubtitle() >= 0 && whitePixels(a) > 30));
                SystemClock.sleep(100);
            }
            assertTrue("LibVLC external subtitle must render white text into the video texture", rendered.get());
            scenario.onActivity(a -> {
                player(a).pause();
                PlaybackEngine before = player(a);
                long position = before.getCurrentPosition();
                invoke(a, "setFullscreen", new Class<?>[]{boolean.class, boolean.class}, true, false);
                assertEquals(View.GONE, ((ScrollView) field(a, "scroll")).getVisibility());
                assertSame(field(a, "screen"), ((View) field(a, "playerView")).getParent());
                assertSame(before, player(a));
                assertEquals(position, player(a).getCurrentPosition(), 100);
                player(a).play();
            });
            SystemClock.sleep(800);
            scenario.onActivity(a -> {
                View full = (View) field(a, "playerView"), window = (View) field(a, "screen");
                assertEquals(window.getWidth(), full.getWidth());
                assertEquals(window.getHeight(), full.getHeight());
                assertTrue("Fullscreen must display the actual blue video", bluePixels(a) > 1000);
                player(a).pause();
                long position = player(a).getCurrentPosition();
                invoke(a, "setFullscreen", new Class<?>[]{boolean.class, boolean.class}, false, false);
                assertEquals(View.VISIBLE, ((ScrollView) field(a, "scroll")).getVisibility());
                assertSame("Native video texture must stay attached across fullscreen changes",
                        field(a, "screen"), ((View) field(a, "playerView")).getParent());
                setField(a, "subtitleOffset", 500L);
                invoke(a, "refreshSubtitles", new Class<?>[]{});
                assertEquals(500000, ((LibVlcPlaybackEngine) player(a)).nativeSubtitleDelay());
                assertEquals(position, player(a).getCurrentPosition(), 100);
                setField(a, "subtitleOffset", -500L);
                invoke(a, "refreshSubtitles", new Class<?>[]{});
                assertEquals(-500000, ((LibVlcPlaybackEngine) player(a)).nativeSubtitleDelay());
                assertEquals(position, player(a).getCurrentPosition(), 100);
                invoke(a, "clearSubtitle", new Class<?>[]{});
                assertEquals(-1, player(a).selectedSubtitle());
                player(a).play();
            });
            SystemClock.sleep(800);
            scenario.onActivity(a -> {
                View inline = (View) field(a, "playerView"), host = (View) field(a, "videoHost");
                assertEquals(host.getWidth(), inline.getWidth());
                assertEquals(host.getHeight(), inline.getHeight());
                assertTrue("Fullscreen return must continue producing video frames", player(a).isPlaying());
                assertTrue("Fullscreen return must preserve the blue video image", bluePixels(a) > 1000);
                assertTrue("Disabled external subtitle must disappear", whitePixels(a) < 15);
            });
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            scenario.onActivity(a -> assertFalse("onStop must pause playback", player(a).getPlayWhenReady()));
        }
    }
    @Test public void matroskaHevcAc3DecodesAndSelectsTracks() throws Exception {
        File video = fixture("codec-test.mkv");
        AtomicBoolean decoded = new AtomicBoolean();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> invoke(a, "openVideo", new Class<?>[]{Uri.class, long.class}, Uri.fromFile(video), 0L));
            waitUntilReady(scenario);
            scenario.onActivity(a -> player(a).play());
            for (int i = 0; i < 200 && !decoded.get(); i++) {
                scenario.onActivity(a -> {
                    org.videolan.libvlc.interfaces.IMedia.Stats stats = ((LibVlcPlaybackEngine) player(a)).diagnostics();
                    decoded.set(stats != null && stats.decodedVideo > 0 && stats.displayedPictures > 0
                            && stats.decodedAudio > 0 && stats.playedAbuffers > 0);
                });
                SystemClock.sleep(100);
            }
            assertTrue("Synthetic HEVC/AC3 must decode actual video and audio buffers", decoded.get());
            scenario.onActivity(a -> {
                String metadata = ((LibVlcPlaybackEngine) player(a)).metadata();
                assertTrue(metadata, metadata.contains("1920x804"));
                assertTrue(metadata, metadata.contains("channels=6"));
                assertTrue(metadata, metadata.contains("sampleRate=48000"));
                java.util.List<PlaybackEngine.Track> audio = player(a).audioTracks();
                java.util.List<PlaybackEngine.Track> subtitles = player(a).subtitleTracks();
                assertEquals(2, audio.size());
                assertEquals(2, subtitles.size());
                assertTrue(player(a).selectAudio(audio.get(1).id));
                assertTrue(player(a).selectSubtitle(subtitles.get(1).id));
            });
            SystemClock.sleep(500);
            scenario.onActivity(a -> {
                assertEquals(player(a).audioTracks().get(1).id, player(a).selectedAudio());
                assertEquals(player(a).subtitleTracks().get(1).id, player(a).selectedSubtitle());
                player(a).pause();
                long position = player(a).getCurrentPosition();
                player(a).setSubtitleOffset(500);
                assertEquals(500000, ((LibVlcPlaybackEngine) player(a)).nativeSubtitleDelay());
                player(a).setSubtitleOffset(-500);
                assertEquals(-500000, ((LibVlcPlaybackEngine) player(a)).nativeSubtitleDelay());
                assertEquals(position, player(a).getCurrentPosition(), 200);
                assertTrue(player(a).selectSubtitle(-1));
                player(a).seekTo(12000);
                player(a).play();
            });
            SystemClock.sleep(800);
            scenario.onActivity(a -> {
                assertEquals(12000, player(a).getCurrentPosition(), 1800);
                player(a).pause();
                assertFalse(player(a).getPlayWhenReady());
            });
        }
    }

    @Test public void externalVttRendersAndDoesNotReplaceMedia() throws Exception {
        File video = fixture("sync-test.mp4");
        File subtitle = new File(video.getParentFile(), "test.vtt");
        try (FileOutputStream output = new FileOutputStream(subtitle)) {
            output.write("WEBVTT\n\n00:00:00.000 --> 00:00:18.000\nVTT subtitle test\n".getBytes(StandardCharsets.UTF_8));
        }
        AtomicBoolean rendered = new AtomicBoolean();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> invoke(a, "openVideo", new Class<?>[]{Uri.class, long.class}, Uri.fromFile(video), 4000L));
            waitUntilReady(scenario);
            scenario.onActivity(a -> {
                long duration = player(a).getDuration();
                long position = player(a).getCurrentPosition();
                invoke(a, "loadSubtitle", new Class<?>[]{Uri.class}, Uri.fromFile(subtitle));
                assertEquals(duration, player(a).getDuration());
                assertEquals(position, player(a).getCurrentPosition(), 100);
                player(a).play();
            });
            for (int i = 0; i < 100 && !rendered.get(); i++) {
                scenario.onActivity(a -> rendered.set(player(a).selectedSubtitle() >= 0 && whitePixels(a) > 30));
                SystemClock.sleep(100);
            }
            assertTrue("LibVLC must render the external VTT text", rendered.get());
        }
    }

    private static void captureUi(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(250);
        // UiAutomation executes a process directly; shell operators are not interpreted.
        String[] commands = {"mkdir -p /sdcard/Download/syncwatch-ui",
                "screencap -p /sdcard/Download/syncwatch-ui/" + name + ".png"};
        for (String command : commands) {
            try (InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
                byte[] buffer = new byte[1024];
                while (input.read(buffer) != -1) { /* Wait for the process to finish. */ }
            }
        }
    }

    @Test public void cinematicOverlayRotatesAndAutoHidesWithAccessibleControls() throws Exception {
        File video = fixture("sync-test.mp4");
        AtomicReference<PlaybackEngine> originalPlayer = new AtomicReference<>();
        AtomicReference<android.view.TextureView> originalTexture = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                ((EditText) field(a, "name")).setText("Alex");
                ((EditText) field(a, "room")).setText("Movie night");
                invoke(a, "openVideo", new Class<?>[]{Uri.class, long.class}, Uri.fromFile(video), 0L);
            });
            waitUntilReady(scenario);
            scenario.onActivity(a -> player(a).play());
            SystemClock.sleep(800);
            scenario.onActivity(a -> {
                player(a).pause();
                PlaybackView view = (PlaybackView) field(a, "playerView");
                view.showControls();
                originalPlayer.set(player(a)); originalTexture.set(texture(view));
                assertEquals("Choose video", a.getString(R.string.choose_video));
                String people = (String) call(a, "localizeParticipants", new Class<?>[]{String.class},
                        "Sala: Movie night · 1 participante(s)\nAlex ✓ — sem vídeo");
                assertEquals("Room: Movie night · 1 participant\nAlex ✓ — No video", people);
            });
            captureUi("portrait");
            scenario.onActivity(a -> invoke(a, "setFullscreen", new Class<?>[]{boolean.class, boolean.class}, true, true));
            AtomicBoolean rotated = new AtomicBoolean();
            for (int i = 0; i < 100 && !rotated.get(); i++) {
                scenario.onActivity(a -> rotated.set(a.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE));
                SystemClock.sleep(100);
            }
            assertTrue("Fullscreen button must rotate into landscape", rotated.get());
            // Configuration changes precede the compositor's completed rotation frame.
            SystemClock.sleep(1000);
            scenario.onActivity(a -> {
                PlaybackView view = (PlaybackView) field(a, "playerView");
                view.showControls();
                View window = (View) field(a, "screen");
                assertEquals(window.getWidth(), view.getWidth());
                assertEquals(window.getHeight(), view.getHeight());
                assertEquals(View.GONE, ((ScrollView) field(a, "scroll")).getVisibility());
                assertSame(originalPlayer.get(), player(a));
                assertSame("Rotation must preserve the native texture", originalTexture.get(), texture(view));
                int minimum = Math.round(48 * a.getResources().getDisplayMetrics().density);
                int[] controls = {R.id.player_play_pause, R.id.player_rewind, R.id.player_forward,
                        R.id.player_audio, R.id.player_subtitles, R.id.player_fullscreen, R.id.player_seek};
                for (int id : controls) {
                    View control = view.findViewById(id);
                    assertTrue("Touch width must be at least 48dp", control.getWidth() >= minimum);
                    assertTrue("Touch height must be at least 48dp", control.getHeight() >= minimum);
                    assertNotNull(control.getContentDescription());
                }
                assertEquals("Exit fullscreen", view.findViewById(R.id.player_fullscreen).getContentDescription());
                assertTrue("Fullscreen must retain the real video image", bluePixels(a) > 1000);
            });
            captureUi("fullscreen-visible");
            scenario.onActivity(a -> player(a).play());
            SystemClock.sleep(3600);
            scenario.onActivity(a -> {
                assertEquals(View.GONE, ((View) field(a, "playerView")).findViewById(R.id.player_controls).getVisibility());
                assertTrue(bluePixels(a) > 1000);
            });
            captureUi("fullscreen-hidden");
            int[] point = new int[2];
            scenario.onActivity(a -> {
                View view = (View) field(a, "playerView");
                view.getLocationOnScreen(point); point[0] += view.getWidth() / 4; point[1] += view.getHeight() / 5;
            });
            long now = SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, point[0], point[1], 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 50, android.view.MotionEvent.ACTION_UP, point[0], point[1], 0);
            try {
                InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
                InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
            } finally { down.recycle(); up.recycle(); }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                assertEquals(View.VISIBLE, ((View) field(a, "playerView")).findViewById(R.id.player_controls).getVisibility());
                a.getOnBackPressedDispatcher().onBackPressed();
                assertEquals(View.VISIBLE, ((ScrollView) field(a, "scroll")).getVisibility());
                assertSame(originalTexture.get(), texture((View) field(a, "playerView")));
                player(a).pause();
            });
        }
    }

    private static Object call(MainActivity activity, String name, Class<?>[] types, Object... args) {
        try { Method method = MainActivity.class.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(activity, args); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
