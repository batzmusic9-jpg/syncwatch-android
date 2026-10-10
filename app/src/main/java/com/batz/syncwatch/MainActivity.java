package com.batz.syncwatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;

import android.widget.FrameLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import org.json.JSONObject;
import java.util.UUID;

public final class MainActivity extends AppCompatActivity {
    private static final int PICK_VIDEO = 10, PICK_SUBTITLE = 11;
    private String connectHost = "syncplay.pl";
    private int connectPort = 8997;
    private boolean requireSecure = true;
    private boolean fullscreen;
    private FrameLayout screen, videoHost;
    private ScrollView scroll;
    private Button subtitleButton;
    private String subtitleSource, subtitleExtension, subtitleName;
    private long subtitleOffset;
    private int subtitleRevision;

    private final Handler main = new Handler(Looper.getMainLooper());
    private PlaybackEngine player;
    private Uri videoUri;
    private SyncProtocol protocol;
    private SyncConnection connection;
    private int generation;
    private boolean connected, applyingRemote, firstState = true;
    private EditText name, room;
    private TextView status, media, participants;
    private CheckBox ready;
    private Button connect;
    private SharedPreferences prefs;
    private String filename;
    private long fileSize;
    private boolean loaded;
    private LinearLayout root;
    private PlaybackView playerView;

    @Override public void onCreate(Bundle saved) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(saved);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        filename = getString(R.string.local_video);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(18, 24, 38), Color.rgb(9, 13, 24)}));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            root.setPadding(dp(20) + bars.left, dp(16) + bars.top, dp(20) + bars.right, dp(24) + bars.bottom);
            return insets;
        });
        scroll.addView(root);
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
        TextView brand = text(getString(R.string.wordmark), 12);
        brand.setLetterSpacing(0.22f);
        brand.setTextColor(Color.rgb(124, 232, 205));
        TextView title = text(getString(R.string.watch_together), 28);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text(getString(R.string.welcome_description), 14).setTextColor(0xFFA8B0BC);
        player = new LibVlcPlaybackEngine(this);
        playerView = new PlaybackView(this, player, this::audioMenu, this::subtitleMenu,
                () -> setFullscreen(!fullscreen, true));
        videoHost = new FrameLayout(this);
        videoHost.setBackground(shape(Color.BLACK, 16));
        videoHost.setClipToOutline(true);
        playerView.setBackground(shape(Color.BLACK, 16));
        playerView.setClipToOutline(true);
        // Keep the native texture attached to one window throughout fullscreen transitions.
        // The scrolling host reserves its portrait space; the player follows those bounds.
        screen.addView(playerView, new FrameLayout.LayoutParams(0, 0));
        screen.getViewTreeObserver().addOnPreDrawListener(() -> { updatePlayerBounds(); return true; });
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(-1, dp(210));
        videoParams.setMargins(0, dp(20), 0, dp(8));
        root.addView(videoHost, videoParams);
        videoHost.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int height = Math.round((r - l) * 9f / 16f);
            if (height > 0 && view.getLayoutParams().height != height) {
                view.getLayoutParams().height = height; view.requestLayout();
            }
        });
        media = text(getString(R.string.video_placeholder), 12);
        media.setTextColor(0xFFA8B0BC); media.setSingleLine(true);
        media.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        button(getString(R.string.choose_video), view -> pick("*/*", PICK_VIDEO), true);
        subtitleButton = button(getString(R.string.subtitles), view -> subtitleMenu(), false);
        Button fullscreenButton = button(getString(R.string.fullscreen), view -> setFullscreen(true, true), false);
        root.removeView(subtitleButton);
        root.removeView(fullscreenButton);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1);
        left.setMargins(0, dp(8), dp(4), dp(4));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1);
        right.setMargins(dp(4), dp(8), 0, dp(4));
        tools.addView(subtitleButton, left);
        tools.addView(fullscreenButton, right);
        root.addView(tools);
        LinearLayout roomCard = new LinearLayout(this);
        roomCard.setOrientation(LinearLayout.VERTICAL);
        roomCard.setPadding(dp(16), dp(12), dp(16), dp(16));
        roomCard.setBackground(shape(0xFF141C29, 16));
        LinearLayout.LayoutParams roomParams = new LinearLayout.LayoutParams(-1, -2);
        roomParams.setMargins(0, dp(16), 0, 0);
        root.addView(roomCard, roomParams);
        text(roomCard, getString(R.string.room), 22).setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout fields = new LinearLayout(this);
        roomCard.addView(fields);
        LinearLayout nameColumn = new LinearLayout(this), roomColumn = new LinearLayout(this);
        nameColumn.setOrientation(LinearLayout.VERTICAL); roomColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.setMargins(0, 0, dp(8), 0);
        fields.addView(nameColumn, nameParams);
        fields.addView(roomColumn, new LinearLayout.LayoutParams(0, -2, 1));
        name = field(nameColumn, getString(R.string.your_name), prefs.getString("name", "Batz"));
        room = field(roomColumn, getString(R.string.room_name), prefs.getString("room", "SyncWatch-" + UUID.randomUUID().toString().substring(0, 8)));
        connect = button(roomCard, getString(R.string.join_room), view -> {
            if (connection != null) disconnect(getString(R.string.disconnected)); else join();
        }, true);
        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        roomCard.addView(stateRow);
        status = new TextView(this);
        status.setText(R.string.disconnected); status.setTextSize(12);
        stateRow.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        status.setTextColor(Color.rgb(124, 232, 205));
        ready = new CheckBox(this);
        ready.setText(R.string.ready);
        ready.setTextColor(0xFFF4F6F8); ready.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF7CE8CD));
        ready.setTextSize(14); ready.setMinHeight(dp(48));
        ready.setEnabled(false);
        stateRow.addView(ready, new LinearLayout.LayoutParams(-2, dp(48)));
        ready.setOnCheckedChangeListener((button, checked) -> {
            if (protocol != null) protocol.ready(checked);
        });
        participants = text(roomCard, getString(R.string.participants_empty), 12);
        participants.setPadding(dp(12), dp(12), dp(12), dp(12));
        participants.setTextColor(0xFFA8B0BC);
        participants.setBackground(shape(0xFF0D1420, 16));
        button(roomCard, getString(R.string.invite), view -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_message, room.getText().toString()));
            startActivity(Intent.createChooser(intent, getString(R.string.invite_room)));
        }, false);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (fullscreen) setFullscreen(false, true);
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); setEnabled(true); }
            }
        });
        player.setListener(new PlaybackEngine.Listener() {
            @Override public void onCommand(boolean seek) {
                updateLocal();
                if (!applyingRemote && protocol != null) protocol.change(seek);
            }
            @Override public void onReady() { updateLocal(); announceFile(); }
            @Override public void onEnded() {
                player.pause();
                updateLocal();
            }
            @Override public void onError(String details) {
                loaded = false;
                ready.setChecked(false);
                ready.setEnabled(false);
                updateLocal();
                media.setText(getString(R.string.playback_error));
            }
        });
        if (saved != null && saved.containsKey("video")) {
            openVideo(Uri.parse(saved.getString("video")), saved.getLong("position"));
        } else {
            String previous = prefs.getString("video", null);
            if (previous != null) openVideo(Uri.parse(previous), prefs.getLong("position", 0));
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            setFullscreen(true, false);
    }

    private void join() {
        String host = connectHost;
        String user = name.getText().toString().trim();
        String roomName = room.getText().toString().trim();
        int number = connectPort;
        if (user.isEmpty() || roomName.isEmpty() || user.length() > 100 || roomName.length() > 100) {
            toast(getString(R.string.invalid_room_fields)); return;
        }
        saveSettings();
        firstState = true;
        connected = false;
        setFieldsEnabled(false);
        status.setText(getString(R.string.connecting));
        connect.setText(getString(R.string.cancel_connection));
        int token = ++generation;
        final boolean[] encrypted = {false};
        protocol = new SyncProtocol(roomName, new SyncProtocol.Listener() {
            public void send(JSONObject value) { if (token == generation && connection != null) connection.send(value); }
            public void connected(String assignedName) {
                if (token != generation) return;
                connected = true;
                status.setText(encrypted[0] ? R.string.connected_tls : R.string.connected_plain);
                status.setContentDescription(getString(R.string.connected_as, assignedName));
                connect.setText(getString(R.string.leave_room));
                ready.setEnabled(loaded);
            }
            public void remote(double seconds, boolean paused, boolean seek, double latency) {
                if (token != generation || !loaded) return;
                long target = SyncPolicy.targetMillis(seconds, paused, latency);
                long duration = player.getDuration();
                if (duration > 0) target = Math.min(target, duration);
                applyingRemote = true;
                try {
                    if (SyncPolicy.shouldSeek(player.getCurrentPosition(), target, seek, firstState)) player.seekTo(target);
                    if (player.getPlayWhenReady() == paused) player.setPlayWhenReady(!paused);
                    firstState = false;
                    updateLocal();
                } finally { applyingRemote = false; }
            }
            public void participants(String value) { if (token == generation) participants.setText(localizeParticipants(value)); }
            public void chat(String value) { /* Chat is not part of the viewing interface. */ }
            public void error(String value) { if (token == generation) disconnect(getString(R.string.error_message, localizeError(value))); }
        });
        updateLocal();
        announceFile();
        connection = new SyncConnection(task -> main.post(task), new SyncConnection.Events() {
            public void transport(boolean secure) {
                if (token != generation) return;
                encrypted[0] = secure;
                protocol.hello(user);
            }
            public void message(JSONObject value) {
                if (token != generation) return;
                updateLocal();
                protocol.receive(value);
            }
            public void failed(String value) { if (token == generation) disconnect(getString(R.string.connection_closed, localizeError(value))); }
        });
        connection.connect(host, number, requireSecure);
    }

    private void updateLocal() {
        if (protocol != null) protocol.local(player.getCurrentPosition() / 1000.0, !player.getPlayWhenReady(), loaded);
    }

    private void announceFile() {
        if (loaded && protocol != null && player.getDuration() > 0)
            protocol.file(filename, fileSize, player.getDuration() / 1000.0);
    }

    private void disconnect(String reason) {
        ++generation;
        if (connection != null) connection.close();
        connection = null;
        protocol = null;
        connected = false;
        ready.setChecked(false);
        ready.setEnabled(false);
        setFieldsEnabled(true);
        connect.setText(getString(R.string.join_room));
        status.setText(R.string.disconnected);
        status.setContentDescription(null);
        if (!reason.equals(getString(R.string.disconnected))) toast(reason);
        participants.setText(getString(R.string.participants_empty));
        player.pause();
    }

    private void openVideo(Uri uri, long position) {
        try {
            filename = getString(R.string.local_video);
            fileSize = 0;
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    filename = cursor.getString(0);
                    fileSize = cursor.isNull(1) ? 0 : cursor.getLong(1);
                }
            }
            ready.setChecked(false);
            applyingRemote = true;
            player.pause();
            clearSubtitle();
            player.open(uri);
            videoUri = uri;
            player.seekTo(Math.max(0, position));
            loaded = true;
            firstState = true;
            player.prepare();
            applyingRemote = false;
            media.setText(filename);
            ready.setEnabled(connected);
            updateLocal();
            // A file replacement resets the room position and pauses everyone.
            if (protocol != null) protocol.change(true);
            prefs.edit().putString("video", uri.toString()).putLong("position", position).apply();
        } catch (Exception e) {
            applyingRemote = false;
            loaded = false;
            updateLocal();
            media.setText(getString(R.string.file_unavailable));
            prefs.edit().remove("video").remove("position").apply();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if ((request == PICK_VIDEO || request == PICK_SUBTITLE) && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            catch (SecurityException ignored) { }
            if (request == PICK_VIDEO) openVideo(uri, 0); else loadSubtitle(uri);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (loaded && videoUri != null) {
            state.putString("video", videoUri.toString());
            state.putLong("position", player.getCurrentPosition());
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) {
            player.pause();
            prefs.edit().putLong("position", player.getCurrentPosition()).apply();
        }
        saveSettings();
    }

    @Override protected void onDestroy() {
        ++generation;
        if (connection != null) connection.close();
        if (playerView != null) playerView.detachSurface();
        if (player != null) player.release();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void saveSettings() {
        prefs.edit().putString("name", name.getText().toString().trim())
                .putString("room", room.getText().toString().trim()).apply();
    }
    private void setFieldsEnabled(boolean value) {
        name.setEnabled(value); room.setEnabled(value);
    }
    private void pick(String mime, int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(mime);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, request);
    }
    private void setFullscreen(boolean value, boolean rotate) {
        if (fullscreen != value) {
            fullscreen = value;
            scroll.setVisibility(value ? View.GONE : View.VISIBLE);
            updatePlayerBounds();
        }
        playerView.setFullscreenButtonState(value);
        playerView.setBackground(value ? new android.graphics.drawable.ColorDrawable(Color.BLACK)
                : shape(Color.BLACK, 16));
        playerView.setClipToOutline(!value);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), screen);
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (value) bars.hide(WindowInsetsCompat.Type.systemBars());
        else bars.show(WindowInsetsCompat.Type.systemBars());
        if (rotate) setRequestedOrientation(value ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
    private void updatePlayerBounds() {
        int width = fullscreen ? screen.getWidth() : videoHost.getWidth();
        int height = fullscreen ? screen.getHeight() : videoHost.getHeight();
        int x = 0, y = 0;
        if (!fullscreen) {
            int[] host = new int[2], window = new int[2];
            videoHost.getLocationInWindow(host);
            screen.getLocationInWindow(window);
            x = host[0] - window[0]; y = host[1] - window[1];
        }
        FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) playerView.getLayoutParams();
        if (bounds.width != width || bounds.height != height || bounds.leftMargin != x || bounds.topMargin != y) {
            bounds.width = width; bounds.height = height;
            bounds.leftMargin = x; bounds.topMargin = y;
            playerView.setLayoutParams(bounds);
        }
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        setFullscreen(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE, false);
    }
    private void audioMenu() {
        if (!loaded) { toast(getString(R.string.choose_video_first)); return; }
        java.util.List<PlaybackEngine.Track> tracks = player.audioTracks();
        if (tracks.isEmpty()) { toast(getString(R.string.audio_loading)); return; }
        String[] labels = new String[tracks.size()];
        int selected = -1;
        for (int i = 0; i < tracks.size(); i++) {
            labels[i] = tracks.get(i).label;
            if (tracks.get(i).id == player.selectedAudio()) selected = i;
        }
        new AlertDialog.Builder(this).setTitle(getString(R.string.audio)).setSingleChoiceItems(labels, selected, (dialog, which) -> {
            if (!player.selectAudio(tracks.get(which).id)) toast(getString(R.string.audio_selection_error));
            dialog.dismiss();
        }).setNegativeButton(getString(R.string.close), null).show();
    }
    private void subtitleMenu() {
        if (!loaded) { toast(getString(R.string.choose_video_first)); return; }
        java.util.List<PlaybackEngine.Track> tracks = player.subtitleTracks();
        String[] labels = new String[tracks.size() + 3];
        labels[0] = getString(R.string.no_subtitles);
        int selected = player.selectedSubtitle() < 0 ? 0 : -1;
        for (int i = 0; i < tracks.size(); i++) {
            labels[i + 1] = tracks.get(i).label;
            if (tracks.get(i).id == player.selectedSubtitle()) selected = i + 1;
        }
        labels[labels.length - 2] = getString(R.string.add_external_subtitle);
        labels[labels.length - 1] = getString(R.string.subtitle_timing_value, subtitleOffset);
        new AlertDialog.Builder(this).setTitle(getString(R.string.subtitles)).setSingleChoiceItems(labels, selected, (dialog, which) -> {
            dialog.dismiss();
            if (which == 0) player.selectSubtitle(-1);
            else if (which == labels.length - 2) pick("*/*", PICK_SUBTITLE);
            else if (which == labels.length - 1) subtitleTiming();
            else if (!player.selectSubtitle(tracks.get(which - 1).id)) toast(getString(R.string.subtitle_selection_error));
        }).setNegativeButton(getString(R.string.close), null).show();
    }
    private void subtitleTiming() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.valueOf(subtitleOffset));
        input.setPadding(dp(24), dp(16), dp(24), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.subtitle_delay))
                .setMessage(getString(R.string.subtitle_delay_help))
                .setView(input).setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.apply), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                long offset = Long.parseLong(input.getText().toString().trim());
                if (offset < -600000 || offset > 600000) { toast(getString(R.string.subtitle_offset_range)); return; }
                subtitleOffset = offset;
                refreshSubtitles();
                dialog.dismiss();
            } catch (NumberFormatException error) { toast(getString(R.string.subtitle_offset_number)); }
        }));
        dialog.show();
    }
    private void loadSubtitle(Uri uri) {
        try {
            String label = uri.getLastPathSegment();
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) label = cursor.getString(0);
            }
            if (label == null) throw new IllegalArgumentException();
            String extension = label.toLowerCase(java.util.Locale.ROOT);
            if (!extension.endsWith(".srt") && !extension.endsWith(".vtt")) {
                toast(getString(R.string.subtitle_file_type)); return;
            }
            byte[] bytes;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalArgumentException();
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    if (output.size() > 2 * 1024 * 1024) break;
                }
                bytes = output.toByteArray();
            }
            if (bytes.length > 2 * 1024 * 1024) { toast(getString(R.string.subtitle_file_size)); return; }
            String source = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
            if (!source.contains("-->")) { toast(getString(R.string.subtitle_invalid_timing)); return; }
            subtitleName = label;
            subtitleExtension = extension.endsWith(".srt") ? "srt" : "vtt";
            subtitleSource = source;
            subtitleOffset = 0;
            File next = new File(getCacheDir(), "subtitle-" + (++subtitleRevision) + "." + subtitleExtension);
            Files.write(next.toPath(), subtitleSource.getBytes(StandardCharsets.UTF_8));
            player.addSubtitle(Uri.fromFile(next), subtitleName);
            refreshSubtitles();
        } catch (Exception error) { toast(getString(R.string.subtitle_read_error)); }
    }
    private void clearSubtitle() {
        subtitleSource = null;
        subtitleOffset = 0;
        if (player != null) { player.selectSubtitle(-1); player.setSubtitleOffset(0); }
        subtitleButton.setText(getString(R.string.subtitles));
    }
    private void refreshSubtitles() {
        try {
            player.setSubtitleOffset(subtitleOffset);
            subtitleButton.setText(subtitleSource == null ? getString(R.string.subtitles) : getString(R.string.subtitle_offset_label, subtitleOffset));
        } catch (Exception error) { toast(getString(R.string.subtitle_delay_error)); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    // Localize only the protocol's presentation strings; wire messages and sync state stay unchanged.
    private String localizeParticipants(String value) {
        String[] lines = value.split("\n", -1);
        if (lines.length > 0) {
            lines[0] = lines[0].replaceFirst("^Sala:", getString(R.string.room) + ":");
            java.util.regex.Matcher count = java.util.regex.Pattern.compile(" · (\\d+) participante\\(s\\)$").matcher(lines[0]);
            if (count.find()) {
                int participants = Integer.parseInt(count.group(1));
                lines[0] = count.replaceFirst(" · " + getResources().getQuantityString(R.plurals.participant_count, participants, participants));
            }
        }
        for (int i = 1; i < lines.length; i++)
            if (lines[i].endsWith(" — sem vídeo"))
                lines[i] = lines[i].substring(0, lines[i].length() - "sem vídeo".length()) + getString(R.string.no_video);
        return android.text.TextUtils.join("\n", lines);
    }
    private String localizeError(String value) {
        if (value == null) return getString(R.string.server_error);
        switch (value) {
            case "O servidor não ofereceu TLS. Confira servidor e porta.": return getString(R.string.tls_unavailable);
            case "Mensagem do servidor muito grande": return getString(R.string.server_message_large);
            case "Conexão encerrada pelo servidor": return getString(R.string.server_closed);
            case "Sem conexão": return getString(R.string.no_connection);
            case "Resposta inválida do servidor": return getString(R.string.server_response_invalid);
            case "Erro do servidor": return getString(R.string.server_error);
            case "Mensagem de sincronização inválida": return getString(R.string.sync_message_invalid);
            default: return value;
        }
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color); background.setCornerRadius(dp(radius)); return background;
    }
    private TextView text(String value, int size) {
        return text(root, value, size);
    }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(0xFFF4F6F8);
        view.setPadding(0, dp(4), 0, dp(6)); parent.addView(view); return view;
    }
    private EditText field(LinearLayout parent, String label, String value) {
        text(parent, label, 12).setTextColor(0xFFA8B0BC);
        EditText view = new EditText(this);
        view.setHint(label); view.setText(value); view.setTextSize(16); view.setSingleLine(true);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setTextColor(0xFFF4F6F8); view.setHintTextColor(0xFFA8B0BC);
        view.setBackground(shape(0xFF0D1420, 12));
        view.setMinimumHeight(dp(52)); parent.addView(view, new LinearLayout.LayoutParams(-1, dp(52))); return view;
    }
    private Button button(String label, View.OnClickListener listener, boolean primary) {
        return button(root, label, listener, primary);
    }
    private Button button(LinearLayout parent, String label, View.OnClickListener listener, boolean primary) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(15);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(primary ? Color.rgb(9, 25, 27) : Color.rgb(217, 228, 239));
        view.setBackgroundTintList(null);
        view.setBackground(shape(primary ? Color.rgb(124, 232, 205) : Color.rgb(26, 34, 49), 16));
        view.setMinHeight(dp(50)); view.setPadding(dp(16), dp(10), dp(16), dp(10));
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(4));
        parent.addView(view, params); return view;
    }
}
