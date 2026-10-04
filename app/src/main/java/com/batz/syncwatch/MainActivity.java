package com.batz.syncwatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
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
    private static final int PICK_VIDEO = 10;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private SyncProtocol protocol;
    private SyncConnection connection;
    private int generation;
    private boolean connected, applyingRemote, firstState = true;
    private EditText server, port, name, room, message;
    private TextView status, media, participants, chat;
    private CheckBox requireTls, ready;
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(16, 24, 39));
        // Android 15 edge-to-edge: retain system-bar and keyboard insets.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(dp(20) + insets.getSystemWindowInsetLeft(),
                    dp(12) + insets.getSystemWindowInsetTop(),
                    dp(20) + insets.getSystemWindowInsetRight(),
                    dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.addView(root);
        setContentView(scroll);
        TextView title = text("SyncWatch", 30);
        title.setTextColor(Color.rgb(110, 231, 183));
        text("Seu vídeo. Sua sala. Assistam juntos.", 16);
        text("Cada pessoa abre a mesma cópia do vídeo e entra no mesmo servidor e sala.", 14);
        player = new ExoPlayer.Builder(this).build();
        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setKeepScreenOn(true);
        root.addView(playerView, new LinearLayout.LayoutParams(-1, dp(220)));
        media = text("Nenhum vídeo selecionado", 14);
        button("Escolher vídeo do aparelho", view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_VIDEO);
        });
        text("Conectar à sala", 21);
        server = field("Servidor", prefs.getString("server", "syncplay.pl"));
        port = field("Porta", prefs.getString("port", "8997"));
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        name = field("Seu nome", prefs.getString("name", "Batz"));
        room = field("Nome da sala", prefs.getString("room", "SyncWatch-" + UUID.randomUUID().toString().substring(0, 8)));
        requireTls = new CheckBox(this);
        requireTls.setText("Exigir conexão criptografada (TLS)");
        requireTls.setChecked(prefs.getBoolean("tls", false));
        root.addView(requireTls);
        connect = button("Entrar na sala", view -> {
            if (connection != null) disconnect("Desconectado"); else join();
        });
        status = text("Desconectado · reprodução local disponível", 14);
        participants = text("Participantes aparecerão aqui", 14);
        ready = new CheckBox(this);
        ready.setText("Estou pronto para assistir");
        ready.setEnabled(false);
        root.addView(ready);
        ready.setOnCheckedChangeListener((button, checked) -> {
            if (protocol != null) protocol.ready(checked);
        });
        button("Compartilhar dados da sala", view -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, "Vamos assistir no SyncWatch/Syncplay!\nServidor: "
                    + server.getText() + ":" + port.getText() + "\nSala: " + room.getText()
                    + "\nAbra a mesma cópia do vídeo no seu aparelho.");
            startActivity(Intent.createChooser(intent, "Compartilhar sala"));
        });
        text("Conversa da sala", 21);
        chat = text("", 14);
        message = field("Mensagem", "");
        message.setMaxLines(3);
        button("Enviar mensagem", view -> {
            String content = message.getText().toString().trim();
            if (!connected) { toast("Entre na sala para conversar"); return; }
            if (!content.isEmpty()) {
                if (content.length() > 500) { toast("Use até 500 caracteres"); return; }
                protocol.chat(content);
                message.setText("");
            }
        });
        text("Play, pausa e saltos no player são compartilhados. Ao sair do app, a reprodução é pausada. O vídeo não é enviado ao servidor.", 13);
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
        String host = server.getText().toString().trim();
        String user = name.getText().toString().trim();
        String roomName = room.getText().toString().trim();
        int number;
        try { number = Integer.parseInt(port.getText().toString()); }
        catch (NumberFormatException e) { toast("Informe uma porta de 1 a 65535"); return; }
        if (host.isEmpty() || host.contains("://") || host.contains("/") || host.contains(" ") ||
                user.isEmpty() || roomName.isEmpty() || number < 1 || number > 65535 ||
                user.length() > 100 || roomName.length() > 100) {
            toast("Confira o servidor (sem https://), porta, nome e sala (até 100 caracteres)"); return;
        }
        saveSettings();
        firstState = true;
        connected = false;
        setFieldsEnabled(false);
        status.setText("Conectando a " + host + ":" + number + "…");
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
                appendChat("Você entrou na sala " + roomName + ". Marque pronto quando abrir o vídeo.");
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
            public void chat(String value) { if (token == generation) appendChat(value); }
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
        connection.connect(host, number, requireTls.isChecked());
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
        if (request == PICK_VIDEO && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            catch (SecurityException ignored) { }
            openVideo(uri, 0);
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
        prefs.edit().putString("server", server.getText().toString().trim())
                .putString("port", port.getText().toString()).putString("name", name.getText().toString().trim())
                .putString("room", room.getText().toString().trim()).putBoolean("tls", requireTls.isChecked()).apply();
    }
    private void setFieldsEnabled(boolean value) {
        server.setEnabled(value); port.setEnabled(value); name.setEnabled(value); room.setEnabled(value);
        requireTls.setEnabled(value);
    }
    private void appendChat(String value) {
        String content = chat.getText() + (chat.length() == 0 ? "" : "\n") + value;
        if (content.length() > 6000) content = content.substring(content.length() - 6000);
        chat.setText(content);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(229, 231, 235));
        view.setPadding(0, dp(7), 0, dp(7)); root.addView(view); return view;
    }
    private EditText field(String label, String value) {
        text(label, 13);
        EditText view = new EditText(this);
        view.setHint(label); view.setText(value); view.setTextSize(16); view.setSingleLine(true);
        view.setPadding(dp(8), dp(4), dp(8), dp(4));
        view.setMinimumHeight(dp(48)); root.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private Button button(String label, View.OnClickListener listener) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false);
        view.setOnClickListener(listener); root.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
}
