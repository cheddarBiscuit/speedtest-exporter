package io.github.cheddarbiscuit.speedtest;

import java.util.Map;

record Config(int port, int timeoutSeconds, int cacheSeconds, String server, String binary) {
    static Config fromEnvironment(Map<String, String> env) {
        String server = env.getOrDefault("SPEEDTEST_SERVER", "");
        if (!server.isEmpty() && !server.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("SPEEDTEST_SERVER must be a positive numeric server ID");
        }
        String binary = env.getOrDefault("SPEEDTEST_BINARY", "speedtest");
        if (binary.isBlank()) {
            throw new IllegalArgumentException("SPEEDTEST_BINARY must not be blank");
        }
        return new Config(
                integer(env, "SPEEDTEST_PORT", 9798, 1, 65535),
                integer(env, "SPEEDTEST_TIMEOUT", 90, 1, Integer.MAX_VALUE),
                integer(env, "SPEEDTEST_CACHE_FOR", 0, 0, Integer.MAX_VALUE),
                server, binary);
    }

    private static int integer(Map<String, String> env, String name, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(env.getOrDefault(name, Integer.toString(fallback)));
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(name + " must be an integer between " + min + " and " + max);
    }
}
