package io.github.cheddarbiscuit.speedtest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExporterTest {
    @Test
    void cachesConcurrentScrapes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Exporter exporter = exporter(60, () -> {
            calls.incrementAndGet();
            return Speedtest.parse(SpeedtestTest.RESULT);
        });
        var executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = java.util.stream.IntStream.range(0, 8)
                    .<java.util.concurrent.Callable<Metrics>>mapToObj(i -> exporter::metrics).toList();
            for (var result : executor.invokeAll(tasks)) {
                assertTrue(result.get().up());
            }
            assertEquals(1, calls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void zeroCacheRunsEveryScrape() {
        AtomicInteger calls = new AtomicInteger();
        Exporter exporter = exporter(0, () -> {
            calls.incrementAndGet();
            return Metrics.FAILED;
        });
        exporter.metrics();
        exporter.metrics();
        assertEquals(2, calls.get());
    }

    @Test
    void expiresCacheAfterConfiguredDuration() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Exporter exporter = exporter(1, () -> {
            calls.incrementAndGet();
            return Metrics.FAILED;
        });
        exporter.metrics();
        Thread.sleep(1100);
        exporter.metrics();
        assertEquals(2, calls.get());
    }

    @Test
    void failuresZeroAllMetricsAndAreCached() {
        AtomicInteger calls = new AtomicInteger();
        Exporter exporter = exporter(60, () -> {
            calls.incrementAndGet();
            throw new IOException("CLI failed");
        });
        assertEquals(Metrics.FAILED, exporter.metrics());
        assertEquals(Metrics.FAILED, exporter.metrics());
        assertEquals(1, calls.get());
        assertEquals(6, Metrics.FAILED.prometheus().lines().filter(line -> line.endsWith(" 0.0")).count());
    }

    @Test
    void failedScrapeDoesNotRetainOldSuccessfulMetrics() {
        AtomicInteger calls = new AtomicInteger();
        Exporter exporter = exporter(0, () -> {
            if (calls.incrementAndGet() == 1) {
                return Speedtest.parse(SpeedtestTest.RESULT);
            }
            return Speedtest.parse("{}");
        });
        assertTrue(exporter.metrics().up());
        assertEquals(Metrics.FAILED, exporter.metrics());
    }

    @Test
    void preservesInterruption() {
        Exporter exporter = exporter(0, () -> { throw new InterruptedException(); });
        try {
            assertEquals(Metrics.FAILED, exporter.metrics());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void servesPrometheusGaugesAndExactRoutes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Exporter exporter = exporter(0, () -> {
            calls.incrementAndGet();
            return Speedtest.parse(SpeedtestTest.RESULT);
        });
        var server = exporter.createServer(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            var response = get(client, base + "/metrics");
            assertEquals(200, response.statusCode());
            assertEquals("text/plain; version=0.0.4; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow());
            String metrics = response.body();
            assertEquals(6, metrics.lines().filter(line -> line.startsWith("# HELP ")).count());
            assertEquals(6, metrics.lines().filter(line -> line.startsWith("# TYPE ")).count());
            assertTrue(metrics.contains("speedtest_server_id 1234.0\n"));
            assertTrue(metrics.contains("speedtest_jitter_latency_milliseconds 0.5\n"));
            assertTrue(metrics.contains("speedtest_ping_latency_milliseconds 12.3\n"));
            assertTrue(metrics.contains("speedtest_download_bits_per_second 1.0E8\n"));
            assertTrue(metrics.contains("speedtest_upload_bits_per_second 2.0E7\n"));
            assertTrue(metrics.endsWith("speedtest_up 1.0\n"));
            assertEquals(200, get(client, base + "/").statusCode());
            assertEquals(404, get(client, base + "/metrics-extra").statusCode());
            var post = client.send(HttpRequest.newBuilder(URI.create(base + "/metrics"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(405, post.statusCode());
            assertEquals("GET", post.headers().firstValue("Allow").orElseThrow());
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    private static Exporter exporter(int cacheSeconds, Exporter.Measurement measurement) {
        return new Exporter(new Config(9798, 90, cacheSeconds, "", "speedtest"), measurement);
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
