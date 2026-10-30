# Speedtest Exporter

A Java 17 Prometheus exporter inspired by
[MiguelNdeCarvalho/speedtest-exporter](https://github.com/MiguelNdeCarvalho/speedtest-exporter).
It runs the official Ookla Speedtest CLI on demand and preserves the upstream
metric names and configuration. No Python runtime is needed.

## Build and run

Requirements: JDK 17+, Maven 3.6.3+, and the
[official Ookla Speedtest CLI](https://www.speedtest.net/apps/cli) installed on
`PATH` (not the Python `speedtest-cli` package).

```sh
mvn verify
java -jar target/speedtest-exporter.jar
curl http://localhost:9798/metrics
```

The exporter verifies the CLI at startup. Each uncached `GET /metrics` runs a
speed test. `/` provides a link to the metrics endpoint. Tests use controlled CLI
fixtures and do not require Ookla or consume Internet speed-test traffic.

**Terms and bandwidth:** Running a test passes `--accept-license` and
`--accept-gdpr` to Ookla. Review Ookla's terms before use. Tests consume bandwidth;
configure caching and a sensible scrape interval. The service listens on all
interfaces and has no authentication; expose it only to trusted monitoring
clients, using firewall rules or an authenticated reverse proxy.

### Docker

The `Dockerfile` builds the jar and bundles the Ookla CLI (amd64 and arm64), so
neither Java nor the CLI is needed on the host.

```sh
docker build -t speedtest-exporter .
docker run -d --name speedtest-exporter --restart unless-stopped \
  -p 9798:9798 -e SPEEDTEST_TIMEOUT=55 speedtest-exporter
curl http://localhost:9798/metrics
```

Stop it with `docker stop speedtest-exporter`.

Merges to `main` publish a multi-arch image to
`ghcr.io/cheddarbiscuit/speedtest-exporter` (`latest`, a short-SHA tag, and
`X.Y.Z` for `vX.Y.Z` release tags), so you can `docker pull` it instead of building.

A test takes tens of seconds, so give Prometheus a long scrape timeout and keep
`SPEEDTEST_TIMEOUT` below it:

```yaml
scrape_configs:
  - job_name: 'speedtest-exporter'
    scrape_interval: 60m
    scrape_timeout: 1m
    static_configs:
      - targets: ['<host>:9798']
```

If you scrape more often than you want to test, set `SPEEDTEST_CACHE_FOR` (seconds)
so extra scrapes reuse the last result.

### Configuration

| Environment variable | Default | Description |
| --- | --- | --- |
| `SPEEDTEST_PORT` | `9798` | HTTP port (1–65535). |
| `SPEEDTEST_SERVER` | unset | Optional positive numeric Ookla server ID. |
| `SPEEDTEST_TIMEOUT` | `90` | Positive CLI timeout in seconds. Timed-out processes are killed. |
| `SPEEDTEST_CACHE_FOR` | `0` | Nonnegative cache lifetime in seconds; zero runs a test on every scrape. |
| `SPEEDTEST_BINARY` | `speedtest` | Executable name or path to the official CLI. |

Concurrent cache refreshes are serialized. Failed tests return HTTP 200 with all
gauges zero, including `speedtest_up`, and are cached for the same lifetime as
successful tests. Invalid configuration causes startup to fail.

### Metrics

All metrics are Prometheus gauges:

| Metric | Value |
| --- | --- |
| `speedtest_server_id` | Selected Ookla server ID. |
| `speedtest_jitter_latency_milliseconds` | Ping jitter in milliseconds. |
| `speedtest_ping_latency_milliseconds` | Ping latency in milliseconds. |
| `speedtest_download_bits_per_second` | Download bandwidth, converted from CLI bytes/second × 8. |
| `speedtest_upload_bits_per_second` | Upload bandwidth, converted from CLI bytes/second × 8. |
| `speedtest_up` | 1 for a successful test, 0 otherwise. |

Example Prometheus configuration (adjust the address for your deployment):

```yaml
scrape_configs:
  - job_name: speedtest
    scrape_interval: 5m
    scrape_timeout: 100s
    static_configs:
      - targets: ['localhost:9798']
```

Keep `scrape_timeout` greater than `SPEEDTEST_TIMEOUT` and no greater than
`scrape_interval`. Prometheus's default scrape timeout is too short for most
uncached tests.

## Automated dependency and vulnerability updates

GitHub Actions runs `mvn verify` and builds and smoke-tests the Docker image on
pull requests and pushes to `main`. Dependabot (`.github/dependabot.yml`) opens
weekly PRs for Maven dependencies and plugins, GitHub Actions, and the Docker base
images, and Dependabot alerts and security updates open PRs for known
vulnerabilities. Updates are never automatically merged.

The bundled Ookla CLI is pinned by version and SHA-256 checksum in the
`Dockerfile`. Dependabot cannot update it; when bumping `SPEEDTEST_VERSION`, update
both checksums too.
