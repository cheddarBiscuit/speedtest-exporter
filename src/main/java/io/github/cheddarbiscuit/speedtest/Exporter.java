package io.github.cheddarbiscuit.speedtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class Exporter {
    private static final Logger LOG = Logger.getLogger(Exporter.class.getName());
    private final Config config;
    private final Measurement measurement;
    private Metrics cached;
    private long cachedAt;

    @FunctionalInterface
    interface Measurement {
        Metrics run() throws IOException, InterruptedException;
    }

    Exporter(Config config, Measurement measurement) {
        this.config = config;
        this.measurement = measurement;
    }

    synchronized Metrics metrics() {
        if (cached != null && System.nanoTime() - cachedAt
                < TimeUnit.SECONDS.toNanos(config.cacheSeconds())) {
            return cached;
        }
        try {
            cached = measurement.run();
        } catch (IOException | IllegalArgumentException e) {
            LOG.warning("Speedtest failed: " + e.getMessage());
            cached = Metrics.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cached = Metrics.FAILED;
        }
        cachedAt = System.nanoTime();
        return cached;
    }

    HttpServer createServer(InetSocketAddress address) throws IOException {
        HttpServer server = HttpServer.create(address, 16);
        server.createContext("/", this::handle);
        return server;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                respond(exchange, 405, "text/plain", "Method not allowed\n");
            } else if ("/metrics".equals(path)) {
                respond(exchange, 200, "text/plain; version=0.0.4; charset=utf-8", metrics().prometheus());
            } else if ("/".equals(path)) {
                respond(exchange, 200, "text/html; charset=utf-8",
                        "<h1>Speedtest Exporter</h1><a href='/metrics'>Metrics</a>\n");
            } else {
                respond(exchange, 404, "text/plain", "Not found\n");
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String text)
            throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Config config = Config.fromEnvironment(System.getenv());
        Speedtest speedtest = new Speedtest(config);
        speedtest.checkBinary();
        HttpServer server = new Exporter(config, speedtest::run)
                .createServer(new InetSocketAddress(config.port()));
        var executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdownNow();
        }));
        server.start();
        LOG.info("Speedtest exporter listening on port " + config.port());
    }
}
