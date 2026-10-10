package com.batz.syncwatch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** Cinematic controls over the existing, continuously attached VLC texture. */
public final class PlaybackView extends FrameLayout {
    private static final int TEXT = 0xFFF4F6F8, SECONDARY = 0xFFA8B0BC, MINT = 0xFF7CE8CD;
    private final PlaybackEngine engine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FrameLayout surface, controls;
    private final LinearLayout transport, fullscreen;
    private final ImageButton toggle;
    private final ImageView fullscreenIcon;
    private final TextView elapsed, total, fullscreenLabel;
    private final SeekBar progress;
    private boolean dragging, lastWanted;
    private long lastInteraction;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            boolean wanted = engine.getPlayWhenReady();
            if (wanted != lastWanted) {
                lastWanted = wanted;
                toggle.setImageResource(wanted ? R.drawable.ic_pause : R.drawable.ic_play);
                toggle.setContentDescription(getContext().getString(wanted ? R.string.pause : R.string.play));
                if (!wanted) showControls();
            }
            long position = engine.getCurrentPosition(), duration = Math.max(0, engine.getDuration());
            updateTime(elapsed, clock(position));
            updateTime(total, clock(duration));
            if (!dragging) progress.setProgress(duration > 0 ? (int) (position * 1000 / duration) : 0);
            if (engine.isPlaying() && !dragging && android.os.SystemClock.elapsedRealtime() - lastInteraction >= 3000)
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
        controls = new FrameLayout(context);
        controls.setId(R.id.player_controls);
        controls.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x3304070D, 0x2204070D, 0xC704070D}));
        addView(controls, new FrameLayout.LayoutParams(-1, -1));

        transport = new LinearLayout(context);
        transport.setGravity(Gravity.CENTER);
        controls.addView(transport, new FrameLayout.LayoutParams(-2, dp(72), Gravity.CENTER));
        ImageButton rewind = iconButton(R.id.player_rewind, R.drawable.ic_replay_10, R.string.rewind_ten, false,
                () -> engine.seekTo(engine.getCurrentPosition() - 10000));
        transport.addView(rewind, transportParams(56, 12));
        toggle = iconButton(R.id.player_play_pause, R.drawable.ic_play, R.string.play, true,
                () -> engine.setPlayWhenReady(!engine.getPlayWhenReady()));
        transport.addView(toggle, transportParams(64, 12));
        ImageButton forward = iconButton(R.id.player_forward, R.drawable.ic_forward_10, R.string.forward_ten, false,
                () -> engine.seekTo(engine.getCurrentPosition() + 10000));
        transport.addView(forward, transportParams(56, 12));

        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.VERTICAL);
        controls.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        LinearLayout timeline = new LinearLayout(context);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(timeline, new LinearLayout.LayoutParams(-1, dp(48)));
        elapsed = timeLabel(); total = timeLabel();
        timeline.addView(elapsed, new LinearLayout.LayoutParams(-2, -1));
        progress = new SeekBar(context);
        progress.setId(R.id.player_seek);
        progress.setMax(1000);
        progress.setContentDescription(context.getString(R.string.seek_position));
        progress.setProgressTintList(ColorStateList.valueOf(MINT));
        progress.setThumbTintList(ColorStateList.valueOf(MINT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF4A5260));
        timeline.addView(progress, new LinearLayout.LayoutParams(0, -1, 1));
        timeline.addView(total, new LinearLayout.LayoutParams(-2, -1));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { if (user) interact(); }
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; interact(); }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                dragging = false; interact();
                if (engine.getDuration() > 0) engine.seekTo(engine.getDuration() * bar.getProgress() / 1000);
            }
        });
        LinearLayout options = new LinearLayout(context);
        options.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(options, new LinearLayout.LayoutParams(-1, dp(48)));
        options.addView(chip(R.id.player_audio, R.drawable.ic_audio, R.string.audio, audio), chipParams());
        options.addView(chip(R.id.player_subtitles, R.drawable.ic_subtitles, R.string.subtitles, subtitles), chipParams());
        options.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
        fullscreen = chip(R.id.player_fullscreen, R.drawable.ic_fullscreen, R.string.fullscreen, onFullscreen);
        fullscreenIcon = (ImageView) fullscreen.getChildAt(0);
        fullscreenLabel = (TextView) fullscreen.getChildAt(1);
        options.addView(fullscreen, new LinearLayout.LayoutParams(-2, dp(48)));
        View.OnClickListener tap = view -> {
            if (controls.getVisibility() == VISIBLE) controls.setVisibility(GONE);
            else showControls();
        };
        surface.setOnClickListener(tap);
        controls.setOnClickListener(tap);
        setFullscreenButtonState(false);
    }
    private ImageButton iconButton(int id, int drawable, int description, boolean primary, Runnable action) {
        ImageButton button = new ImageButton(getContext());
        button.setId(id); button.setImageResource(drawable);
        button.setContentDescription(getContext().getString(description));
        button.setImageTintList(ColorStateList.valueOf(primary ? 0xFF04070D : TEXT));
        button.setBackground(controlSurface(primary ? MINT : 0xD91A2231, 100));
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setOnClickListener(view -> { showControls(); action.run(); });
        return button;
    }
    private LinearLayout chip(int id, int drawable, int label, Runnable action) {
        LinearLayout chip = new LinearLayout(getContext());
        chip.setId(id); chip.setGravity(Gravity.CENTER); chip.setMinimumWidth(dp(48));
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setBackground(controlSurface(0xD91A2231, 14));
        chip.setContentDescription(getContext().getString(label));
        ImageView icon = new ImageView(getContext()); icon.setImageResource(drawable);
        icon.setImageTintList(ColorStateList.valueOf(MINT));
        chip.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView text = new TextView(getContext()); text.setText(label); text.setTextColor(TEXT); text.setTextSize(12);
        text.setPadding(dp(6), 0, 0, 0);
        chip.addView(text, new LinearLayout.LayoutParams(-2, -2));
        // One accessible action per chip, rather than separate icon and text announcements.
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        chip.setFocusable(true);
        chip.setOnClickListener(view -> { showControls(); action.run(); });
        return chip;
    }
    private RippleDrawable controlSurface(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius));
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(dp(radius));
        return new RippleDrawable(ColorStateList.valueOf(0x337CE8CD), shape, mask);
    }
    private LinearLayout.LayoutParams transportParams(int size, int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(size), dp(size));
        params.setMargins(dp(gap / 2), 0, dp(gap / 2), 0); return params;
    }
    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
        params.setMargins(0, 0, dp(8), 0); return params;
    }
    private TextView timeLabel() {
        TextView view = new TextView(getContext()); view.setText(clock(0));
        view.setTextColor(SECONDARY); view.setTextSize(11); view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.MONOSPACE); return view;
    }
    private static void updateTime(TextView view, String text) { if (!text.contentEquals(view.getText())) view.setText(text); }
    private void interact() { lastInteraction = android.os.SystemClock.elapsedRealtime(); }
    public void showControls() { controls.setVisibility(VISIBLE); interact(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String clock(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    public void detachSurface() { engine.detachSurface(); }
    public void attachSurface() { engine.attachSurface(surface); }
    public void setFullscreenButtonState(boolean value) {
        fullscreenIcon.setImageResource(value ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
        fullscreen.setContentDescription(getContext().getString(value ? R.string.exit_fullscreen : R.string.enter_fullscreen));
        fullscreenLabel.setText(value ? R.string.exit : R.string.fullscreen);
        fullscreenLabel.setVisibility(value ? VISIBLE : GONE);
        controls.setPadding(dp(value ? 24 : 12), 0, dp(value ? 24 : 12), dp(value ? 16 : 4));
        transport.setTranslationY(value ? 0 : -dp(44));
        showControls();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); handler.post(refresh); }
    @Override protected void onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow(); }
}
