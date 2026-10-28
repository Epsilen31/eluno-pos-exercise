package com.eluno.pos.client;

import static org.assertj.core.api.Assertions.*;

import com.eluno.pos.exception.UpstreamApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExternalApiClientTest {
    @Test
    void retriesKnown429And503ButDoesNotRetryUnknownServerWriteFailure() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        var throttle = new AtomicInteger();
        var terminal = new AtomicInteger();
        var unknown = new AtomicInteger();
        server.createContext(
                "/orders",
                exchange -> {
                    int status = throttle.incrementAndGet() == 1 ? 429 : 200;
                    exchange.getResponseHeaders().set("Retry-After", "0");
                    byte[] b = "{}".getBytes();
                    exchange.sendResponseHeaders(status, b.length);
                    exchange.getResponseBody().write(b);
                    exchange.close();
                });
        server.createContext(
                "/terminal/charges",
                exchange -> {
                    int status = terminal.incrementAndGet() == 1 ? 503 : 200;
                    exchange.getResponseHeaders().set("Retry-After", "0");
                    byte[] b = "{}".getBytes();
                    exchange.sendResponseHeaders(status, b.length);
                    exchange.getResponseBody().write(b);
                    exchange.close();
                });
        server.createContext(
                "/unknown",
                exchange -> {
                    unknown.incrementAndGet();
                    byte[] b = "{}".getBytes();
                    exchange.sendResponseHeaders(500, b.length);
                    exchange.getResponseBody().write(b);
                    exchange.close();
                });
        server.start();
        try {
            var http =
                    new ExternalApiClient(
                            "http://localhost:" + server.getAddress().getPort(),
                            new ObjectMapper());
            http.post("/orders", Map.of());
            http.post("/terminal/charges", Map.of());
            assertThat(throttle.get()).isEqualTo(2);
            assertThat(terminal.get()).isEqualTo(2);
            assertThatThrownBy(() -> http.post("/unknown", Map.of()))
                    .isInstanceOfSatisfying(
                            UpstreamApiException.class,
                            e -> assertThat(e.isOutcomeUncertain()).isTrue());
            assertThat(unknown.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }
}
