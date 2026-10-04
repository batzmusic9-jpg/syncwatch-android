package com.batz.syncwatch;

import org.json.JSONObject;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One reader and one writer; callbacks are delivered on the supplied UI executor. */
public final class SyncConnection implements AutoCloseable {
    public interface Events {
        void transport(boolean encrypted);
        void message(JSONObject message);
        void failed(String reason);
    }
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.Executor dispatcher;
    private final Events events;
    private volatile Socket socket;
    private volatile boolean closed;

    public SyncConnection(java.util.concurrent.Executor dispatcher, Events events) {
        this.dispatcher = dispatcher;
        this.events = events;
    }

    public void connect(String host, int port, boolean requireTls) {
        new Thread(() -> {
            try {
                Socket tcp = new Socket();
                socket = tcp;
                if (closed) { tcp.close(); return; }
                tcp.connect(new InetSocketAddress(host, port), 10000);
                tcp.setSoTimeout(15000);
                tcp.setTcpNoDelay(true);
                write(SyncProtocol.object("TLS", SyncProtocol.object("startTLS", "send")));
                JSONObject answer = new JSONObject(readLine(tcp.getInputStream()));
                JSONObject tls = answer.optJSONObject("TLS");
                boolean encrypted = tls != null && "true".equals(tls.optString("startTLS"));
                if (encrypted) {
                    SSLSocket secure = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                            .createSocket(tcp, host, port, true);
                    socket = secure;
                    SSLParameters parameters = secure.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    secure.setSSLParameters(parameters);
                    secure.setSoTimeout(15000);
                    secure.startHandshake();
                } else if (tls == null || requireTls) {
                    throw new IOException("O servidor não ofereceu TLS. Confira servidor e porta.");
                }
                if (closed) return;
                socket.setSoTimeout(30000);
                dispatcher.execute(() -> { if (!closed) events.transport(encrypted); });
                InputStream input = socket.getInputStream();
                while (!closed) {
                    JSONObject message = new JSONObject(readLine(input));
                    dispatcher.execute(() -> { if (!closed) events.message(message); });
                }
            } catch (Exception e) {
                fail(e);
            }
        }, "SyncWatch-reader").start();
    }

    static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') return bytes.toString(StandardCharsets.UTF_8.name()).trim();
            if (bytes.size() >= 65536) throw new IOException("Mensagem do servidor muito grande");
            bytes.write(value);
        }
        throw new EOFException("Conexão encerrada pelo servidor");
    }

    public void send(JSONObject message) {
        if (closed) return;
        try {
            writer.execute(() -> {
                try { if (!closed) write(message); } catch (Exception e) { fail(e); }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }

    private void write(JSONObject message) throws IOException {
        Socket current = socket;
        if (current == null) throw new IOException("Sem conexão");
        synchronized (this) {
            current.getOutputStream().write((message.toString() + "\r\n").getBytes(StandardCharsets.UTF_8));
            current.getOutputStream().flush();
        }
    }

    private synchronized void fail(Exception e) {
        if (closed) return;
        close();
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        dispatcher.execute(() -> events.failed(reason));
    }

    @Override public synchronized void close() {
        closed = true;
        writer.shutdownNow();
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }
}
