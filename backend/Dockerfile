# BlocBuy backend image. Context: backend/.
#
# The executable Spring Boot JAR is built, tested, and verified by the
# ci-backend build-quality job before this image is created.
# Docker packages that exact verified artifact instead of rebuilding source.
#
# Runtime profile is NOT baked in: compose/deploy sets SPRING_PROFILES_ACTIVE
# (e.g. ec2).

# Explicit OS line and multi-architecture digest; keep in sync with Dockerfile.ci.
FROM eclipse-temurin:21-jre-noble@sha256:22138efd69393501fccd8176ae16b01791ed71ff801b28f0359415389b17c766

# Temporary CVE-2026-84782 repair until a verified upstream rebuild includes it.
# Dependabot updates the base digest, not this package version; see the CI runbook.
ARG OPENSSL_FIXED_VERSION=3.0.13-0ubuntu3.16

WORKDIR /app

# Repair only older installed packages; newer bases skip APT and never downgrade.
RUN set -eux; \
    set --; \
    for package in libssl3t64 openssl; do \
        test "$(dpkg-query -W -f='${db:Status-Status}' "$package")" = installed; \
        installed="$(dpkg-query -W -f='${Version}' "$package")"; \
        if dpkg --compare-versions "$installed" lt "$OPENSSL_FIXED_VERSION"; then \
            set -- "$@" "$package=$OPENSSL_FIXED_VERSION"; \
        fi; \
    done; \
    if [ "$#" -gt 0 ]; then \
        apt-get update; \
        apt-get install -y --no-install-recommends --only-upgrade "$@"; \
    fi; \
    for package in libssl3t64 openssl; do \
        installed="$(dpkg-query -W -f='${Version}' "$package")"; \
        dpkg --compare-versions "$installed" ge "$OPENSSL_FIXED_VERSION"; \
    done; \
    rm -rf /var/lib/apt/lists/*; \
    rm -f /usr/bin/pebble; \
    groupadd --system blocbuy; \
    useradd --system --gid blocbuy blocbuy

# Produced by `./mvnw verify` in build-quality and restored into the Docker
# build context by the docker job.
COPY blocbuy-bootstrap/target/blocbuy-backend.jar app.jar

USER blocbuy

EXPOSE 8589

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Health probes are orchestrator-owned and target GET /api/actuator/health on :8589.
ENTRYPOINT ["java", "-jar", "app.jar"]
