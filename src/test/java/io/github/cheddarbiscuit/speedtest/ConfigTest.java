package io.github.cheddarbiscuit.speedtest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigTest {
    @Test
    void defaultsMatchUpstream() {
        assertEquals(new Config(9798, 90, 0, "", "speedtest"), Config.fromEnvironment(Map.of()));
    }

    @Test
    void acceptsConfiguration() {
        assertEquals(new Config(8080, 30, 600, "1234", "/usr/bin/speedtest"),
                Config.fromEnvironment(Map.of("SPEEDTEST_PORT", "8080", "SPEEDTEST_TIMEOUT", "30",
                        "SPEEDTEST_CACHE_FOR", "600", "SPEEDTEST_SERVER", "1234",
                        "SPEEDTEST_BINARY", "/usr/bin/speedtest")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "65536", "abc", "2147483648"})
    void rejectsInvalidPorts(String port) {
        assertThrows(IllegalArgumentException.class,
                () -> Config.fromEnvironment(Map.of("SPEEDTEST_PORT", port)));
    }

    @Test
    void rejectsInvalidTimeoutCacheAndServer() {
        for (var entry : Map.of("SPEEDTEST_TIMEOUT", "0", "SPEEDTEST_CACHE_FOR", "-1",
                "SPEEDTEST_SERVER", "123 --anything", "SPEEDTEST_BINARY", " ").entrySet()) {
            assertThrows(IllegalArgumentException.class,
                    () -> Config.fromEnvironment(Map.of(entry.getKey(), entry.getValue())));
        }
    }
}
