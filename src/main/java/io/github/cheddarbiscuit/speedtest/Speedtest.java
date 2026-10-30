package io.github.cheddarbiscuit.speedtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class Speedtest {
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private final Config config;

    Speedtest(Config config) {
        this.config = config;
    }

    void checkBinary() throws IOException, InterruptedException {
        if (!execute(List.of(config.binary(), "--version")).contains("Speedtest by Ookla")) {
            throw new IOException("Install the official Ookla Speedtest CLI: https://www.speedtest.net/apps/cli");
        }
    }

    Metrics run() throws IOException, InterruptedException {
        var command = new ArrayList<>(List.of(config.binary(), "--format=json", "--progress=no",
                "--accept-license", "--accept-gdpr"));
        if (!config.server().isEmpty()) {
            command.add("--server-id=" + config.server());
        }
        return parse(execute(command));
    }

    private String execute(List<String> command) throws IOException, InterruptedException {
        // A file avoids filling a pipe while waiting for the CLI to exit.
        Path output = Files.createTempFile("speedtest-", ".json");
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS)) {
                throw new IOException("Speedtest CLI timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Speedtest CLI exited with status " + process.exitValue());
            }
            try (var input = Files.newInputStream(output)) {
                byte[] bytes = input.readNBytes(MAX_OUTPUT_BYTES + 1);
                if (bytes.length > MAX_OUTPUT_BYTES) {
                    throw new IOException("Speedtest CLI output exceeds size limit");
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(output);
        }
    }

    static Metrics parse(String json) {
        try {
            JsonObject result = JsonParser.parseString(json).getAsJsonObject();
            if (result.has("error") || !"result".equals(result.get("type").getAsString())) {
                throw new IllegalArgumentException("Not a successful speedtest result");
            }
            return new Metrics(number(result.getAsJsonObject("server"), "id"),
                    number(result.getAsJsonObject("ping"), "jitter"),
                    number(result.getAsJsonObject("ping"), "latency"),
                    bitsPerSecond(result.getAsJsonObject("download")),
                    bitsPerSecond(result.getAsJsonObject("upload")), true);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid Speedtest CLI result", e);
        }
    }

    private static double bitsPerSecond(JsonObject transfer) {
        double bits = number(transfer, "bandwidth") * 8;
        if (!Double.isFinite(bits)) {
            throw new IllegalArgumentException("Bandwidth out of range");
        }
        return bits;
    }

    private static double number(JsonObject object, String field) {
        var value = object.getAsJsonPrimitive(field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException("Expected numeric " + field);
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number < 0) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return number;
    }
}
