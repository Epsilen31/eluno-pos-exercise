package com.eluno.pos.client;

import com.eluno.pos.exception.UpstreamApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ExternalApiClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String base;

    public ExternalApiClient(@Value("${pos.upstream}") String base, ObjectMapper objectMapper) {
        this.base = base;
        this.objectMapper = objectMapper;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public JsonNode get(String path) {
        return executeRequest(HttpMethod.GET, path, null);
    }

    public JsonNode post(String path, Object body) {
        return executeRequest(HttpMethod.POST, path, body);
    }

    private JsonNode executeRequest(HttpMethod method, String path, Object body) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                var request = restClient.method(method).uri(URI.create(base + path));
                // The provided Python server only reads Content-Length, not chunked bodies.
                if (body != null) {
                    byte[] bytes;
                    try {
                        bytes = objectMapper.writeValueAsBytes(body);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Cannot serialize upstream request", e);
                    }
                    request.contentType(MediaType.APPLICATION_JSON)
                            .contentLength(bytes.length)
                            .body(bytes);
                }
                var reply =
                        request.exchange(
                                (httpRequest, response) ->
                                        new Reply(
                                                response.getStatusCode().value(),
                                                response.getHeaders().getFirst("Retry-After"),
                                                new String(
                                                        response.getBody().readAllBytes(),
                                                        java.nio.charset.StandardCharsets.UTF_8)));
                boolean knownRetryable =
                        reply.status == 429
                                || reply.status == 503
                                        && (method == HttpMethod.GET
                                                || path.equals("/terminal/charges"));
                // In this supplied mock, 429 and terminal 503 are emitted before any mutation.
                if (knownRetryable) {
                    if (attempt == 3)
                        throw new UpstreamApiException(
                                "Upstream temporarily unavailable; retry this operation with the same reference",
                                false);
                    waitBeforeRetry(calculateRetryDelay(reply.retryAfter, attempt));
                    continue;
                }
                if (reply.status >= 400)
                    throw new UpstreamApiException(
                            "Upstream rejected "
                                    + method
                                    + " "
                                    + path
                                    + " (HTTP "
                                    + reply.status
                                    + ")",
                            reply.status >= 500);
                try {
                    return objectMapper.readTree(reply.body);
                } catch (Exception e) {
                    throw new UpstreamApiException(
                            "Unreadable upstream response; reconcile before retrying a write",
                            method != HttpMethod.GET);
                }
            } catch (ResourceAccessException e) {
                if (method == HttpMethod.GET && attempt < 3) {
                    waitBeforeRetry(calculateRetryDelay(null, attempt));
                    continue;
                }
                throw new UpstreamApiException(
                        "Upstream connection interrupted; outcome "
                                + (method == HttpMethod.GET
                                        ? "unavailable"
                                        : "unknown, reconciliation required"),
                        method != HttpMethod.GET);
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private static long calculateRetryDelay(String retryAfter, int attempt) {
        if (retryAfter != null) {
            try {
                return Math.min(
                        30_000,
                        Math.max(
                                0,
                                new java.math.BigDecimal(retryAfter)
                                        .multiply(java.math.BigDecimal.valueOf(1000))
                                        .longValue()));
            } catch (NumberFormatException ignored) {
                /* The supplied mock uses seconds. */
            }
        }
        return Math.min(4000, 200L << attempt)
                + java.util.concurrent.ThreadLocalRandom.current().nextLong(100);
    }

    private static void waitBeforeRetry(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamApiException("Request interrupted; reconcile in-flight writes", true);
        }
    }

    private record Reply(int status, String retryAfter, String body) {}
}
