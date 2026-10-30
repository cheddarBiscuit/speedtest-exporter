package io.github.cheddarbiscuit.speedtest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class SpeedtestTest {
    static final String RESULT = """
            {"type":"result","server":{"id":1234},"ping":{"jitter":0.5,"latency":12.3},
             "download":{"bandwidth":12500000},"upload":{"bandwidth":2500000}}
            """;

    @TempDir
    Path directory;

    @Test
    void parsesOoklaResultAndConvertsBytesToBits() {
        assertEquals(new Metrics(1234, 0.5, 12.3, 100000000, 20000000, true), Speedtest.parse(RESULT));
    }

    @Test
    void rejectsErrorsMissingFieldsAndInvalidMeasurements() {
        for (String invalid : List.of("not json", "null", "[]", "{}", "{\"type\":\"log\"}",
                "{\"error\":\"socket failure\"}", RESULT.replace("\"bandwidth\":12500000", ""),
                RESULT.replace("12.3", "-1"), RESULT.replace("12.3", "\"NaN\""),
                RESULT.replace("12.3", "1e309"), RESULT.replace("12500000", "1e308"))) {
            assertThrows(IllegalArgumentException.class, () -> Speedtest.parse(invalid));
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void invokesOfficialCliWithSeparateArgumentsAndOptionalServer() throws Exception {
        Path arguments = directory.resolve("arguments");
        Speedtest cli = cli("""
                if [ "$1" = "--version" ]; then
                  echo "Speedtest by Ookla"
                  exit 0
                fi
                printf '%%s\\n' "$@" > '%s'
                cat <<'RESULT'
                %s
                RESULT
                """.formatted(arguments, RESULT), "42", 2);
        cli.checkBinary();
        assertTrue(cli.run().up());
        assertEquals(List.of("--format=json", "--progress=no", "--accept-license",
                "--accept-gdpr", "--server-id=42"), Files.readAllLines(arguments));

        assertTrue(cli("cat <<'RESULT'\n" + RESULT + "\nRESULT\n", "", 2).run().up());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void handlesNonzeroExitMissingBinaryAndWrongImplementation() throws Exception {
        assertThrows(IOException.class, () -> cli("exit 1\n", "", 2).run());
        assertThrows(IOException.class, () -> cli("echo unofficial\n", "", 2).checkBinary());
        assertThrows(IOException.class,
                () -> new Speedtest(new Config(9798, 1, 0, "", directory.resolve("missing").toString())).run());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void killsTimedOutProcess() throws Exception {
        Path pid = directory.resolve("pid");
        Speedtest cli = cli("echo $$ > '" + pid + "'\nexec sleep 30\n", "", 1);
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(IOException.class, cli::run));
        ProcessHandle process = ProcessHandle.of(Long.parseLong(Files.readString(pid).trim())).orElse(null);
        if (process != null) {
            process.onExit().get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertFalse(process.isAlive());
        }
    }

    private Speedtest cli(String body, String server, int timeout) throws IOException {
        Path binary = directory.resolve("speedtest");
        Files.writeString(binary, "#!/bin/sh\n" + body);
        Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwx------"));
        return new Speedtest(new Config(9798, timeout, 0, server, binary.toString()));
    }
}
