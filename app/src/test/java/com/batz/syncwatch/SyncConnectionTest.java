package com.batz.syncwatch;

import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class SyncConnectionTest {
    @Test public void realSocketNegotiatesAndDeliversHelloThenCloses() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch received = new CountDownLatch(1);
            CountDownLatch ended = new CountDownLatch(1);
            CountDownLatch serverDone = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread simulated = new Thread(() -> {
                try (Socket peer = server.accept()) {
                    peer.setSoTimeout(5000);
                    JSONObject request = new JSONObject(SyncConnection.readLine(peer.getInputStream()));
                    assertEquals("send", request.getJSONObject("TLS").getString("startTLS"));
                    peer.getOutputStream().write("{\"TLS\":{\"startTLS\":\"false\"}}\r\n".getBytes(StandardCharsets.UTF_8));
                    JSONObject hello = new JSONObject(SyncConnection.readLine(peer.getInputStream()));
                    assertEquals("Batz", hello.getJSONObject("Hello").getString("username"));
                    peer.getOutputStream().write("{\"Hello\":{\"username\":\"Batz\"}}\r\n".getBytes(StandardCharsets.UTF_8));
                } catch (Throwable e) { failure.set(e); }
                finally { serverDone.countDown(); }
            });
            AtomicReference<SyncConnection> holder = new AtomicReference<>();
            SyncConnection connection = new SyncConnection(Runnable::run, new SyncConnection.Events() {
                public void transport(boolean encrypted) {
                    assertFalse(encrypted);
                    holder.get().send(SyncProtocol.object("Hello", SyncProtocol.object("username", "Batz")));
                }
                public void message(JSONObject value) {
                    assertEquals("Batz", value.optJSONObject("Hello").optString("username"));
                    received.countDown();
                }
                public void failed(String reason) { ended.countDown(); }
            });
            holder.set(connection);
            simulated.start();
            try {
                connection.connect("127.0.0.1", server.getLocalPort(), false);
                assertTrue(received.await(8, TimeUnit.SECONDS));
                assertTrue(ended.await(8, TimeUnit.SECONDS));
                assertTrue(serverDone.await(8, TimeUnit.SECONDS));
                assertNull(failure.get());
            } finally { connection.close(); }
        }
    }

    @Test public void tlsRequiredRejectsUnencryptedServer() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread simulated = new Thread(() -> {
                try (Socket peer = server.accept()) {
                    SyncConnection.readLine(peer.getInputStream());
                    peer.getOutputStream().write("{\"TLS\":{\"startTLS\":\"false\"}}\r\n".getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) { }
            });
            CountDownLatch rejected = new CountDownLatch(1);
            AtomicReference<Boolean> transport = new AtomicReference<>(false);
            SyncConnection connection = new SyncConnection(Runnable::run, new SyncConnection.Events() {
                public void transport(boolean encrypted) { transport.set(true); }
                public void message(JSONObject value) { }
                public void failed(String reason) { if (reason.contains("TLS")) rejected.countDown(); }
            });
            simulated.start();
            try {
                connection.connect("127.0.0.1", server.getLocalPort(), true);
                assertTrue(rejected.await(8, TimeUnit.SECONDS));
                assertFalse(transport.get());
            } finally { connection.close(); }
            simulated.join(5000);
        }
    }

    @Test public void networkFrameIsBoundedAndHandlesCrLf() throws Exception {
        assertEquals("{\"ok\":true}", SyncConnection.readLine(new ByteArrayInputStream(
                "{\"ok\":true}\r\n".getBytes(StandardCharsets.UTF_8))));
        try {
            SyncConnection.readLine(new ByteArrayInputStream(new byte[65537]));
            fail("Oversized frames must be rejected");
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("grande")); }
    }
}
