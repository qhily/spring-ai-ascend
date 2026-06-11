package com.huawei.ascend.examples.a2a.scenarios;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Deterministic stand-in for the bank's exchange-rate HTTP service: serves a
 * scripted rate as JSON on {@code /rates} and records every request it
 * receives, so a test can assert both sides of the tool call — what the agent
 * sent and what the wire answer carried.
 */
final class StubExchangeRateService implements AutoCloseable {

    record RecordedCall(String method, URI uri) {
    }

    private final HttpServer server;
    private final List<RecordedCall> calls = new CopyOnWriteArrayList<>();
    private final CountDownLatch firstCall = new CountDownLatch(1);

    private StubExchangeRateService(HttpServer server) {
        this.server = server;
    }

    static StubExchangeRateService start(String pair, String rate) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        StubExchangeRateService stub = new StubExchangeRateService(server);
        byte[] payload = ("{\"pair\":\"" + pair + "\",\"rate\":\"" + rate + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        server.createContext("/rates", exchange -> {
            stub.calls.add(new RecordedCall(exchange.getRequestMethod(), exchange.getRequestURI()));
            stub.firstCall.countDown();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        return stub;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    RecordedCall awaitFirstCall(Duration deadline) throws InterruptedException {
        if (!firstCall.await(deadline.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new AssertionError("stub exchange-rate service was never called within " + deadline);
        }
        return calls.get(0);
    }

    List<RecordedCall> calls() {
        return List.copyOf(calls);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
