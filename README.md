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
  -p 9798:9798 -e SPEEDTEST_CACHE_FOR=300 speedtest-exporter
curl http://localhost:9798/metrics
```

Stop it with `docker stop speedtest-exporter`.

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

GitHub Actions runs `mvn verify` on pushes and pull requests. A separate
**Dependency and vulnerability updates** workflow runs Renovate daily at
06:17 UTC, or manually through **Actions → Run workflow**. It opens PRs for Maven
dependencies/build plugins and GitHub Actions, including pinned action digests.
Updates are never automatically merged.

One-time repository setup:

1. Create a dedicated bot's fine-grained personal access token scoped to this
   repository with **Contents: read/write**, **Pull requests: read/write**,
   **Workflows: read/write** (to update Actions), and **Dependabot alerts: read**.
   Store it as the Actions repository secret `RENOVATE_TOKEN`. Do not put it in
   a file. A bot token is used instead of `GITHUB_TOKEN` so its PRs trigger CI.
2. Enable **Dependency graph** and **Dependabot alerts** in the repository's
   security settings. For broader transitive coverage, also enable automatic
   dependency submission for Maven where available.
3. Merge these workflows into the default branch, enable Actions/scheduled
   workflows if needed, then run the update workflow manually to verify access.

`renovate.json` enables OSV vulnerability checks for direct Maven dependencies
and GitHub vulnerability-alert fixes for dependencies present in the dependency
graph. Fix PRs require an available patched version; this is not a complete
security audit. Without the token, the update workflow cannot open PRs.