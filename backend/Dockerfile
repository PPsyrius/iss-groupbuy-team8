# BlocBuy backend image. Context: backend/ (multi-module build needs all module poms).
# Tests/quality gates already ran in ci-backend build-quality — skipped here intentionally.
# Runtime profile is NOT baked in: compose/deploy sets SPRING_PROFILES_ACTIVE (e.g. docker).

# ---------- build ----------
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency layer first for cache reuse
COPY pom.xml ./
COPY blocbuy-client/pom.xml blocbuy-client/
COPY blocbuy-domain/pom.xml blocbuy-domain/
COPY blocbuy-application/pom.xml blocbuy-application/
COPY blocbuy-adapter/pom.xml blocbuy-adapter/
COPY blocbuy-infrastructure/pom.xml blocbuy-infrastructure/
COPY blocbuy-bootstrap/pom.xml blocbuy-bootstrap/
RUN mvn -B -q dependency:go-offline -pl blocbuy-bootstrap -am || true

COPY . .
RUN mvn -B -q package -pl blocbuy-bootstrap -am \
      -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true

# ---------- runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Base image ships pebble (Let's Encrypt's ACME test server) at /usr/bin/pebble —
# a Go test tool the app never runs, and its stdlib CVEs trip the Trivy image gate.
RUN rm -f /usr/bin/pebble \
 && groupadd --system blocbuy && useradd --system --gid blocbuy blocbuy
USER blocbuy

COPY --from=build /build/blocbuy-bootstrap/target/blocbuy-bootstrap-*.jar app.jar

EXPOSE 8589
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# No in-image HEALTHCHECK: JRE base has no curl/wget. Orchestrator probes
# (compose healthcheck / k8s liveness) hit GET /actuator/health on :8589.
ENTRYPOINT ["java", "-jar", "app.jar"]
