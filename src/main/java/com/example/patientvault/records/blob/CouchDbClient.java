package com.example.patientvault.records.blob;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal CouchDB HTTP client on {@link HttpClient}. Request and response bodies of attachments are streamed
 * (never buffered whole), calls have connect/read timeouts, idempotent calls are retried with back-off and
 * a consecutive-failure counter feeds the health check. Nothing here logs URLs with ids of people or any body.
 */
public class CouchDbClient {

    /** JSON reply of a small request. {@code body} is null when the server sent none. */
    public record Reply(int status, JsonNode body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** Streaming reply: the caller must close {@code body} (null when the status is not 2xx). */
    public record StreamReply(int status, InputStream body) {
    }

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    static final Duration TRANSFER_TIMEOUT = Duration.ofMinutes(15);
    private static final int ATTEMPTS = 3;
    private static final int UNHEALTHY_AFTER_FAILURES = 3;

    private final String baseUrl;
    private final String authorization;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong lastLatencyMillis = new AtomicLong();

    public CouchDbClient(String baseUrl, String user, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authorization = "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(CONNECT_TIMEOUT).build();
    }

    public JsonMapper mapper() {
        return json;
    }

    /** True while recent calls succeeded; flips after several consecutive transport/5xx failures. */
    public boolean looksHealthy() {
        return consecutiveFailures.get() < UNHEALTHY_AFTER_FAILURES;
    }

    public long lastLatencyMillis() {
        return lastLatencyMillis.get();
    }

    /** Small JSON request. {@code body} may be a JsonNode, a String of JSON or null. */
    public Reply json(String method, String path, Object body, boolean idempotent) {
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : json.writeValueAsString(body));
        for (int attempt = 1; ; attempt++) {
            HttpRequest.Builder request = base(path, REQUEST_TIMEOUT).method(method, publisher);
            if (body != null) {
                request.header("Content-Type", "application/json");
            }
            try {
                HttpResponse<byte[]> response = timed(() -> http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray()));
                recordOutcome(response.statusCode());
                if (response.statusCode() >= 500 && idempotent && attempt < ATTEMPTS) {
                    backoff(attempt);
                    continue;
                }
                byte[] bytes = response.body();
                return new Reply(response.statusCode(), bytes == null || bytes.length == 0 ? null : json.readTree(bytes));
            } catch (IOException e) {
                consecutiveFailures.incrementAndGet();
                if (!idempotent || attempt >= ATTEMPTS) {
                    throw new BlobStoreException("CouchDB request failed", e);
                }
                backoff(attempt);
            }
        }
    }

    /** HEAD of a document; returns the current revision (without quotes) or null when it does not exist. */
    public String currentRev(String docPath) {
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<Void> response = timed(() -> http.send(
                        base(docPath, REQUEST_TIMEOUT).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.discarding()));
                recordOutcome(response.statusCode());
                if (response.statusCode() == 404) {
                    return null;
                }
                if (response.statusCode() >= 500 && attempt < ATTEMPTS) {
                    backoff(attempt);
                    continue;
                }
                if (response.statusCode() != 200) {
                    throw new BlobStoreException("CouchDB answered " + response.statusCode());
                }
                String etag = response.headers().firstValue("ETag").orElseThrow(() -> new BlobStoreException("No revision"));
                return etag.replace("\"", "");
            } catch (IOException e) {
                consecutiveFailures.incrementAndGet();
                if (attempt >= ATTEMPTS) {
                    throw new BlobStoreException("CouchDB request failed", e);
                }
                backoff(attempt);
            }
        }
    }

    /** Streams {@code content} (exactly {@code length} bytes) as the request body. Not retried: the stream is consumed. */
    public Reply upload(String path, InputStream content, long length) {
        HttpRequest request = base(path, TRANSFER_TIMEOUT)
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> content), length))
                .build();
        try {
            HttpResponse<byte[]> response = timed(() -> http.send(request, HttpResponse.BodyHandlers.ofByteArray()));
            recordOutcome(response.statusCode());
            byte[] bytes = response.body();
            return new Reply(response.statusCode(), bytes == null || bytes.length == 0 ? null : json.readTree(bytes));
        } catch (IOException e) {
            consecutiveFailures.incrementAndGet();
            throw new BlobStoreException("CouchDB upload failed", e);
        } finally {
            try {
                content.close();
            } catch (IOException ignored) {
                // nothing to recover: the upload outcome is already decided
            }
        }
    }

    /** Streaming GET. The retry covers only getting the response headers. */
    public StreamReply stream(String path) {
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<InputStream> response = timed(() -> http.send(
                        base(path, TRANSFER_TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofInputStream()));
                recordOutcome(response.statusCode());
                if (response.statusCode() >= 500 && attempt < ATTEMPTS) {
                    response.body().close();
                    backoff(attempt);
                    continue;
                }
                if (response.statusCode() / 100 != 2) {
                    response.body().close();
                    return new StreamReply(response.statusCode(), null);
                }
                return new StreamReply(response.statusCode(), response.body());
            } catch (IOException e) {
                consecutiveFailures.incrementAndGet();
                if (attempt >= ATTEMPTS) {
                    throw new BlobStoreException("CouchDB download failed", e);
                }
                backoff(attempt);
            }
        }
    }

    private HttpRequest.Builder base(String path, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout).header("Authorization", authorization)
                .header("Accept", "application/json, */*");
    }

    private void recordOutcome(int status) {
        if (status >= 500) {
            consecutiveFailures.incrementAndGet();
        } else {
            consecutiveFailures.set(0);
        }
    }

    private interface IoCall<T> {
        T run() throws IOException, InterruptedException;
    }

    private <T> T timed(IoCall<T> call) throws IOException {
        long started = System.nanoTime();
        try {
            return call.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } finally {
            lastLatencyMillis.set((System.nanoTime() - started) / 1_000_000);
        }
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(150L * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
