package com.batz.syncwatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.view.ViewGroup;

import android.widget.FrameLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MimeTypes;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
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
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.util.UUID;

@UnstableApi
public final class MainActivity extends AppCompatActivity {
    private static final int PICK_VIDEO = 10, PICK_SUBTITLE = 11;
    private String connectHost = "syncplay.pl";
    private int connectPort = 8997;
    private boolean requireSecure = true;
    private boolean fullscreen;
    private FrameLayout screen, videoHost;
    private ScrollView scroll;
    private Button subtitleButton;
    private String subtitleSource, subtitleMime, subtitleName;
    private long subtitleOffset;
    private int subtitleRevision;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private SyncProtocol protocol;
    private SyncConnection connection;
    private int generation;
    private boolean connected, applyingRemote, firstState = true;
    private EditText name, room;
    private TextView status, media, participants;
    private CheckBox ready;
    private Button connect;
    private SharedPreferences prefs;
    private String filename = "Vídeo local";
    private long fileSize;
    private boolean loaded;
    private LinearLayout root;
    private PlayerView playerView;

    @Override public void onCreate(Bundle saved) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(saved);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
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
            root.setPadding(dp(24) + bars.left, dp(20) + bars.top, dp(24) + bars.right, dp(32) + bars.bottom);
            return insets;
        });
        scroll.addView(root);
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
        TextView brand = text("S Y N C W A T C H", 13);
        brand.setTextColor(Color.rgb(124, 232, 205));
        TextView title = text("Um filme.\nA mesma companhia.", 30);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text("Escolha seu vídeo e encontrem-se na mesma sala.", 14);
        player = new ExoPlayer.Builder(this).build();
        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setKeepScreenOn(true);
        playerView.setControllerShowTimeoutMs(2500);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        playerView.setShowSubtitleButton(true);
        playerView.setFullscreenButtonClickListener(value -> setFullscreen(value, true));
        videoHost = new FrameLayout(this);
        videoHost.setBackground(shape(Color.rgb(4, 7, 13), 20));
        videoHost.setClipToOutline(true);
        videoHost.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(-1, dp(210));
        videoParams.setMargins(0, dp(20), 0, dp(8));
        root.addView(videoHost, videoParams);
        media = text("Seu vídeo aparecerá aqui", 13);
        button("Escolher vídeo", view -> pick("video/*", PICK_VIDEO), true);
        subtitleButton = button("Adicionar legenda · SRT ou VTT", view -> subtitleMenu(), false);
        button("Assistir em tela cheia", view -> setFullscreen(true, true), false);
        text("Sua sala", 23).setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name = field("Seu nome", prefs.getString("name", "Batz"));
        room = field("Nome da sala", prefs.getString("room", "SyncWatch-" + UUID.randomUUID().toString().substring(0, 8)));
        connect = button("Entrar na sala", view -> {
            if (connection != null) disconnect("Você saiu da sala"); else join();
        }, true);
        status = text("Pronto para uma sessão?", 13);
        status.setTextColor(Color.rgb(124, 232, 205));
        ready = new CheckBox(this);
        ready.setText("Estou pronto");
        ready.setTextSize(15);
        ready.setEnabled(false);
        root.addView(ready);
        ready.setOnCheckedChangeListener((button, checked) -> {
            if (protocol != null) protocol.ready(checked);
        });
        participants = text("Quem entrar na sala aparecerá aqui.", 13);
        participants.setPadding(dp(16), dp(16), dp(16), dp(16));
        participants.setBackground(shape(Color.rgb(26, 34, 49), 16));
        button("Convidar alguém", view -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, "Vamos assistir no SyncWatch!\nSala: " + room.getText()
                    + "\nAbra sua cópia do mesmo vídeo e entre na sala.\nPara Syncplay no computador: syncplay.pl:8997");
            startActivity(Intent.createChooser(intent, "Convidar para a sala"));
        }, false);
        text("Cada pessoa usa sua cópia do vídeo. Play, pausa e saltos são sincronizados; legendas são ajustadas só neste aparelho.", 12);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (fullscreen) setFullscreen(false, true);
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); setEnabled(true); }
            }
        });
        player.addListener(new Player.Listener() {
            @Override public void onPlayWhenReadyChanged(boolean value, int reason) {
                updateLocal();
                if (!applyingRemote && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST && protocol != null)
                    protocol.change(false);
            }
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                         Player.PositionInfo newPosition, int reason) {
                updateLocal();
                if (!applyingRemote && reason == Player.DISCONTINUITY_REASON_SEEK && protocol != null)
                    protocol.change(true);
            }
            @Override public void onPlaybackStateChanged(int state) {
                updateLocal();
                if (state == Player.STATE_READY) announceFile();
                if (state == Player.STATE_ENDED && protocol != null) {
                    player.pause();
                    updateLocal();
                    protocol.change(false);
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                loaded = false;
                ready.setChecked(false);
                ready.setEnabled(false);
                updateLocal();
                media.setText("Não foi possível abrir o vídeo: " + error.getErrorCodeName()
                        + "\nEscolha outro arquivo. Formatos dependem dos codecs do aparelho.");
            }
        });
        if (saved != null && saved.containsKey("video")) {
            openVideo(Uri.parse(saved.getString("video")), saved.getLong("position"));
        } else {
            String previous = prefs.getString("video", null);
            if (previous != null) openVideo(Uri.parse(previous), prefs.getLong("position", 0));
        }
    }

    private void join() {
        String host = connectHost;
        String user = name.getText().toString().trim();
        String roomName = room.getText().toString().trim();
        int number = connectPort;
        if (user.isEmpty() || roomName.isEmpty() || user.length() > 100 || roomName.length() > 100) {
            toast("Preencha seu nome e a sala (até 100 caracteres)"); return;
        }
        saveSettings();
        firstState = true;
        connected = false;
        setFieldsEnabled(false);
        status.setText("Encontrando sua sala…");
        connect.setText("Cancelar conexão");
        int token = ++generation;
        final boolean[] encrypted = {false};
        protocol = new SyncProtocol(roomName, new SyncProtocol.Listener() {
            public void send(JSONObject value) { if (token == generation && connection != null) connection.send(value); }
            public void connected(String assignedName) {
                if (token != generation) return;
                connected = true;
                status.setText("Conectado como " + assignedName + (encrypted[0] ? " · TLS" : " · sem criptografia"));
                connect.setText("Sair da sala");
                ready.setEnabled(loaded);
            }
            public void remote(double seconds, boolean paused, boolean seek, double latency) {
                if (token != generation || !loaded) return;
                long target = SyncPolicy.targetMillis(seconds, paused, latency);
                long duration = player.getDuration();
                if (duration != C.TIME_UNSET && duration > 0) target = Math.min(target, duration);
                applyingRemote = true;
                try {
                    if (SyncPolicy.shouldSeek(player.getCurrentPosition(), target, seek, firstState)) player.seekTo(target);
                    if (player.getPlayWhenReady() == paused) player.setPlayWhenReady(!paused);
                    firstState = false;
                    updateLocal();
                } finally { applyingRemote = false; }
            }
            public void participants(String value) { if (token == generation) participants.setText(value); }
            public void chat(String value) { /* Chat is not part of the viewing interface. */ }
            public void error(String value) { if (token == generation) disconnect("Erro: " + value); }
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
            public void failed(String value) { if (token == generation) disconnect("Conexão encerrada: " + value); }
        });
        connection.connect(host, number, requireSecure);
    }

    private void updateLocal() {
        if (protocol != null) protocol.local(player.getCurrentPosition() / 1000.0, !player.getPlayWhenReady(), loaded);
    }

    private void announceFile() {
        if (loaded && protocol != null && player.getDuration() != C.TIME_UNSET)
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
        connect.setText("Entrar na sala");
        status.setText(reason + " · controles agora são locais");
        participants.setText("Fora da sala");
        player.pause();
    }

    private void openVideo(Uri uri, long position) {
        try {
            filename = "Vídeo local";
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
            player.setMediaItem(MediaItem.fromUri(uri), Math.max(0, position));
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
            media.setText("Arquivo indisponível. Escolha o vídeo novamente.");
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
        if (loaded && player.getCurrentMediaItem() != null && player.getCurrentMediaItem().localConfiguration != null) {
            state.putString("video", player.getCurrentMediaItem().localConfiguration.uri.toString());
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
        if (playerView != null) playerView.setPlayer(null);
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
            ((ViewGroup) playerView.getParent()).removeView(playerView);
            if (value) {
                scroll.setVisibility(View.GONE);
                screen.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
            } else {
                screen.removeView(playerView);
                scroll.setVisibility(View.VISIBLE);
                videoHost.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
            }
        }
        playerView.setFullscreenButtonState(value);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), screen);
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (value) bars.hide(WindowInsetsCompat.Type.systemBars());
        else bars.show(WindowInsetsCompat.Type.systemBars());
        if (rotate) setRequestedOrientation(value ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        setFullscreen(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE, false);
    }
    private void subtitleMenu() {
        if (!loaded) { toast("Escolha o vídeo primeiro"); return; }
        if (subtitleSource == null) { pick("*/*", PICK_SUBTITLE); return; }
        new AlertDialog.Builder(this).setTitle("Legenda · " + subtitleName)
                .setItems(new String[]{"Escolher outra legenda", "Ajustar tempo (" + subtitleOffset + " ms)", "Remover legenda"},
                        (dialog, which) -> {
                            if (which == 0) pick("*/*", PICK_SUBTITLE);
                            else if (which == 1) subtitleTiming();
                            else { clearSubtitle(); refreshSubtitles(); }
                        }).show();
    }
    private void subtitleTiming() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.valueOf(subtitleOffset));
        input.setPadding(dp(24), dp(16), dp(24), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Ajustar tempo da legenda")
                .setMessage("Milissegundos: +500 atrasa meio segundo; -500 adianta meio segundo.")
                .setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Aplicar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                long offset = Long.parseLong(input.getText().toString().trim());
                if (offset < -600000 || offset > 600000) { toast("Use um ajuste entre -600000 e 600000 ms"); return; }
                subtitleOffset = offset;
                refreshSubtitles();
                dialog.dismiss();
            } catch (NumberFormatException error) { toast("Digite um número em milissegundos"); }
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
                toast("Escolha uma legenda .srt ou .vtt em UTF-8"); return;
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
            if (bytes.length > 2 * 1024 * 1024) { toast("Use uma legenda de até 2 MB"); return; }
            String source = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
            if (!source.contains("-->")) { toast("O arquivo não contém tempos de legenda válidos"); return; }
            subtitleName = label;
            subtitleMime = extension.endsWith(".srt") ? MimeTypes.APPLICATION_SUBRIP : MimeTypes.TEXT_VTT;
            subtitleSource = source;
            subtitleOffset = 0;
            refreshSubtitles();
        } catch (Exception error) { toast("Não foi possível ler a legenda. Escolha outro arquivo."); }
    }
    private void clearSubtitle() {
        subtitleSource = null;
        subtitleOffset = 0;
        subtitleButton.setText("Adicionar legenda · SRT ou VTT");
    }
    private void refreshSubtitles() {
        MediaItem current = player.getCurrentMediaItem();
        if (current == null || current.localConfiguration == null) return;
        try {
            MediaItem.Builder item = current.buildUpon().setSubtitleConfigurations(Collections.emptyList());
            if (subtitleSource != null) {
                File next = new File(getCacheDir(), "subtitle-" + (++subtitleRevision) + "." + (MimeTypes.TEXT_VTT.equals(subtitleMime) ? "vtt" : "srt"));
                Files.write(next.toPath(), SubtitleTiming.shift(subtitleSource, subtitleOffset).getBytes(StandardCharsets.UTF_8));
                item.setSubtitleConfigurations(Collections.singletonList(new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(next))
                        .setMimeType(subtitleMime).setLabel(subtitleName).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()));

            }
            long position = player.getCurrentPosition();
            boolean playing = player.getPlayWhenReady();
            applyingRemote = true;
            try {
                player.setMediaItem(item.build(), position);
                player.prepare();
                player.setPlayWhenReady(playing);
            } finally { applyingRemote = false; }
            if (subtitleSource != null) subtitleButton.setText("Legenda · " + subtitleName + " · " + subtitleOffset + " ms");
        } catch (Exception error) { toast("Não foi possível aplicar a legenda"); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color); background.setCornerRadius(dp(radius)); return background;
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(225, 231, 241));
        view.setPadding(0, dp(8), 0, dp(8)); root.addView(view); return view;
    }
    private EditText field(String label, String value) {
        text(label, 12);
        EditText view = new EditText(this);
        view.setHint(label); view.setText(value); view.setTextSize(16); view.setSingleLine(true);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setBackground(shape(Color.rgb(26, 34, 49), 14));
        view.setMinimumHeight(dp(52)); root.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private Button button(String label, View.OnClickListener listener, boolean primary) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(15);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(primary ? Color.rgb(9, 25, 27) : Color.rgb(217, 228, 239));
        view.setBackgroundTintList(null);
        view.setBackground(shape(primary ? Color.rgb(124, 232, 205) : Color.rgb(26, 34, 49), 16));
        view.setMinHeight(dp(50)); view.setPadding(dp(16), dp(10), dp(16), dp(10));
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(4));
        root.addView(view, params); return view;
    }
}
