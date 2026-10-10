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
    private String filename = "Vídeo local";
    private long fileSize;
    private boolean loaded;
    private LinearLayout root;
    private PlaybackView playerView;

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
        TextView title = text("Cinema em companhia.", 24);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text("Escolha seu vídeo e encontrem-se na mesma sala.", 14);
        player = new LibVlcPlaybackEngine(this);
        playerView = new PlaybackView(this, player, this::audioMenu, this::subtitleMenu,
                () -> setFullscreen(!fullscreen, true));
        videoHost = new FrameLayout(this);
        videoHost.setBackground(shape(Color.rgb(4, 7, 13), 20));
        videoHost.setClipToOutline(true);
        playerView.setBackground(shape(Color.rgb(4, 7, 13), 20));
        playerView.setClipToOutline(true);
        // Keep the native texture attached to one window throughout fullscreen transitions.
        // The scrolling host reserves its portrait space; the player follows those bounds.
        screen.addView(playerView, new FrameLayout.LayoutParams(0, 0));
        screen.getViewTreeObserver().addOnPreDrawListener(() -> { updatePlayerBounds(); return true; });
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(-1, dp(210));
        videoParams.setMargins(0, dp(20), 0, dp(8));
        root.addView(videoHost, videoParams);
        media = text("Seu vídeo aparecerá aqui", 13);
        button("Escolher vídeo", view -> pick("*/*", PICK_VIDEO), true);
        subtitleButton = button("Adicionar legenda", view -> subtitleMenu(), false);
        Button fullscreenButton = button("Tela cheia", view -> setFullscreen(true, true), false);
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
                media.setText("Não foi possível reproduzir este vídeo. Escolha o arquivo novamente ou tente outro vídeo.");
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
                if (duration > 0) target = Math.min(target, duration);
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
                : shape(Color.rgb(4, 7, 13), 20));
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
        if (!loaded) { toast("Escolha o vídeo primeiro"); return; }
        java.util.List<PlaybackEngine.Track> tracks = player.audioTracks();
        if (tracks.isEmpty()) { toast("Aguarde o vídeo abrir para escolher o áudio"); return; }
        String[] labels = new String[tracks.size()];
        int selected = -1;
        for (int i = 0; i < tracks.size(); i++) {
            labels[i] = tracks.get(i).label;
            if (tracks.get(i).id == player.selectedAudio()) selected = i;
        }
        new AlertDialog.Builder(this).setTitle("Áudio").setSingleChoiceItems(labels, selected, (dialog, which) -> {
            if (!player.selectAudio(tracks.get(which).id)) toast("Não foi possível selecionar este áudio");
            dialog.dismiss();
        }).setNegativeButton("Fechar", null).show();
    }
    private void subtitleMenu() {
        if (!loaded) { toast("Escolha o vídeo primeiro"); return; }
        java.util.List<PlaybackEngine.Track> tracks = player.subtitleTracks();
        String[] labels = new String[tracks.size() + 3];
        labels[0] = "Nenhuma legenda";
        int selected = player.selectedSubtitle() < 0 ? 0 : -1;
        for (int i = 0; i < tracks.size(); i++) {
            labels[i + 1] = tracks.get(i).label;
            if (tracks.get(i).id == player.selectedSubtitle()) selected = i + 1;
        }
        labels[labels.length - 2] = "Adicionar legenda externa (.srt / .vtt)";
        labels[labels.length - 1] = "Ajustar tempo (" + subtitleOffset + " ms)";
        new AlertDialog.Builder(this).setTitle("Legendas").setSingleChoiceItems(labels, selected, (dialog, which) -> {
            dialog.dismiss();
            if (which == 0) player.selectSubtitle(-1);
            else if (which == labels.length - 2) pick("*/*", PICK_SUBTITLE);
            else if (which == labels.length - 1) subtitleTiming();
            else if (!player.selectSubtitle(tracks.get(which - 1).id)) toast("Não foi possível selecionar esta legenda");
        }).setNegativeButton("Fechar", null).show();
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
            subtitleExtension = extension.endsWith(".srt") ? "srt" : "vtt";
            subtitleSource = source;
            subtitleOffset = 0;
            File next = new File(getCacheDir(), "subtitle-" + (++subtitleRevision) + "." + subtitleExtension);
            Files.write(next.toPath(), subtitleSource.getBytes(StandardCharsets.UTF_8));
            player.addSubtitle(Uri.fromFile(next), subtitleName);
            refreshSubtitles();
        } catch (Exception error) { toast("Não foi possível ler a legenda. Escolha outro arquivo."); }
    }
    private void clearSubtitle() {
        subtitleSource = null;
        subtitleOffset = 0;
        if (player != null) { player.selectSubtitle(-1); player.setSubtitleOffset(0); }
        subtitleButton.setText("Adicionar legenda");
    }
    private void refreshSubtitles() {
        try {
            player.setSubtitleOffset(subtitleOffset);
            subtitleButton.setText(subtitleSource == null ? "Legendas" : "Legenda · " + subtitleOffset + " ms");
        } catch (Exception error) { toast("Não foi possível aplicar o ajuste da legenda"); }
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
