package com.batz.syncwatch;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** Small controls shared by embedded and immersive playback. Video keeps its original ratio. */
public final class PlaybackView extends FrameLayout {
    private final PlaybackEngine engine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FrameLayout surface;
    private final LinearLayout controls;
    private final Button toggle, fullscreen;
    private final TextView time;
    private final SeekBar progress;
    private boolean dragging;
    private long lastInteraction;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            toggle.setText(engine.getPlayWhenReady() ? "Pausa" : "Play");
            long position = engine.getCurrentPosition(), duration = Math.max(0, engine.getDuration());
            time.setText(clock(position) + " / " + clock(duration));
            if (!dragging) progress.setProgress(duration > 0 ? (int) (position * 1000 / duration) : 0);
            if (engine.isPlaying() && !dragging && android.os.SystemClock.elapsedRealtime() - lastInteraction > 2500)
                controls.setVisibility(GONE);
            handler.postDelayed(this, 250);
        }
    };
    public PlaybackView(Context context, PlaybackEngine engine, Runnable audio, Runnable subtitles, Runnable onFullscreen) {
        super(context);
        this.engine = engine;
        setBackgroundColor(Color.BLACK);
        setKeepScreenOn(true);
        surface = new FrameLayout(context);
        addView(surface, new FrameLayout.LayoutParams(-1, -1));
        engine.attachSurface(surface);
        controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setBackgroundColor(0xD9090D18);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        addView(controls, params);
        LinearLayout transport = new LinearLayout(context);
        controls.addView(transport);
        toggle = action(transport, "Play", () -> engine.setPlayWhenReady(!engine.getPlayWhenReady()));
        action(transport, "−10 s", () -> engine.seekTo(engine.getCurrentPosition() - 10000));
        action(transport, "+10 s", () -> engine.seekTo(engine.getCurrentPosition() + 10000));
        time = new TextView(context);
        time.setTextColor(Color.WHITE); time.setTextSize(11); time.setGravity(Gravity.CENTER);
        transport.addView(time, new LinearLayout.LayoutParams(0, -1, 1));
        progress = new SeekBar(context); progress.setMax(1000);
        controls.addView(progress, new LinearLayout.LayoutParams(-1, -2));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { if (user) interact(); }
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; interact(); }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                dragging = false; interact();
                if (engine.getDuration() > 0) engine.seekTo(engine.getDuration() * bar.getProgress() / 1000);
            }
        });
        LinearLayout tracks = new LinearLayout(context);
        controls.addView(tracks);
        action(tracks, "Áudio", audio);
        action(tracks, "Legendas", subtitles);
        fullscreen = action(tracks, "Tela cheia", onFullscreen);
        surface.setOnClickListener(view -> {
            if (controls.getVisibility() == VISIBLE) controls.setVisibility(GONE);
            else { controls.setVisibility(VISIBLE); interact(); }
        });
        interact();
    }
    private Button action(LinearLayout row, String label, Runnable runnable) {
        Button button = new Button(getContext());
        button.setText(label); button.setAllCaps(false); button.setTextSize(12);
        button.setTextColor(Color.rgb(124, 232, 205)); button.setPadding(0, 0, 0, 0);
        button.setMinimumWidth(0); button.setMinWidth(0);
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        button.setOnClickListener(view -> { interact(); runnable.run(); });
        return button;
    }
    private void interact() { lastInteraction = android.os.SystemClock.elapsedRealtime(); }
    private static String clock(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    public void detachSurface() { engine.detachSurface(); }
    public void attachSurface() { engine.attachSurface(surface); }
    public void setFullscreenButtonState(boolean value) { fullscreen.setText(value ? "Sair" : "Tela cheia"); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); handler.post(refresh); }
    @Override protected void onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow(); }
}
