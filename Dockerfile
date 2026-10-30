FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre
ARG TARGETARCH
ARG SPEEDTEST_VERSION=1.2.0
ARG SPEEDTEST_SHA256_AMD64=5690596c54ff9bed63fa3732f818a05dbc2db19ad36ed68f21ca5f64d5cfeeb7
ARG SPEEDTEST_SHA256_ARM64=3953d231da3783e2bf8904b6dd72767c5c6e533e163d3742fd0437affa431bd3
RUN case "$TARGETARCH" in \
        amd64) arch=x86_64; sha256=$SPEEDTEST_SHA256_AMD64 ;; \
        arm64) arch=aarch64; sha256=$SPEEDTEST_SHA256_ARM64 ;; \
        *) echo "unsupported architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac \
    && curl -fsSL -o /tmp/speedtest.tgz \
        "https://install.speedtest.net/app/cli/ookla-speedtest-${SPEEDTEST_VERSION}-linux-${arch}.tgz" \
    && echo "$sha256  /tmp/speedtest.tgz" | sha256sum -c - \
    && tar -xz -C /usr/local/bin -f /tmp/speedtest.tgz speedtest \
    && rm /tmp/speedtest.tgz \
    && useradd --system --create-home speedtest
USER speedtest
COPY --from=build /src/target/speedtest-exporter.jar /app/speedtest-exporter.jar
EXPOSE 9798
ENTRYPOINT ["java", "-jar", "/app/speedtest-exporter.jar"]
