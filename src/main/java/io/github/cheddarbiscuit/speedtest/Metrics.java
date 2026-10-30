package io.github.cheddarbiscuit.speedtest;

record Metrics(double server, double jitter, double ping, double download, double upload, boolean up) {
    static final Metrics FAILED = new Metrics(0, 0, 0, 0, 0, false);

    String prometheus() {
        return gauge("speedtest_server_id", "Speedtest server ID used to test", server)
                + gauge("speedtest_jitter_latency_milliseconds", "Speedtest current Jitter in ms", jitter)
                + gauge("speedtest_ping_latency_milliseconds", "Speedtest current Ping in ms", ping)
                + gauge("speedtest_download_bits_per_second", "Speedtest current Download Speed in bit/s", download)
                + gauge("speedtest_upload_bits_per_second", "Speedtest current Upload speed in bits/s", upload)
                + gauge("speedtest_up", "Speedtest status whether the scrape worked", up ? 1 : 0);
    }

    private static String gauge(String name, String help, double value) {
        return "# HELP " + name + " " + help + "\n# TYPE " + name + " gauge\n"
                + name + " " + value + "\n";
    }
}
