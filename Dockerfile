FROM maven:3-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre
ARG TARGETARCH
ARG SPEEDTEST_VERSION=1.2.0
RUN case "$TARGETARCH" in \
        amd64) arch=x86_64 ;; \
        arm64) arch=aarch64 ;; \
        *) echo "unsupported architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac \
    && curl -fsSL "https://install.speedtest.net/app/cli/ookla-speedtest-${SPEEDTEST_VERSION}-linux-${arch}.tgz" \
        | tar -xz -C /usr/local/bin speedtest \
    && useradd --system --create-home speedtest
USER speedtest
COPY --from=build /src/target/speedtest-exporter.jar /app/speedtest-exporter.jar
EXPOSE 9798
ENTRYPOINT ["java", "-jar", "/app/speedtest-exporter.jar"]
