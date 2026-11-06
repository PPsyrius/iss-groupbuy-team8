# BlocBuy backend image. Context: backend/.
#
# The executable Spring Boot JAR (blocbuy-backend-exec.jar) is built, tested, and verified by the
# ci-backend build-quality job before this image is created.
# Docker packages that exact verified artifact instead of rebuilding source.
#
# Runtime profile is NOT baked in: compose/deploy sets SPRING_PROFILES_ACTIVE
# (e.g. ec2).

# Explicit OS line and multi-architecture digest; keep in sync with Dockerfile.ci.
FROM eclipse-temurin:21-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f

WORKDIR /app

# Temporary CVE-2026-109518 / CVE-2026-111497 repair; keep in sync with Dockerfile.ci.
# Use HTTPS and disable retries while upgrading the vulnerable APT client.
# Remove when the pinned upstream image meets all three minimum versions.
RUN sed -i 's|http://|https://|g' /etc/apt/sources.list.d/ubuntu.sources \
 && apt-get -o Acquire::Retries=0 update \
 && apt-get -o Acquire::Retries=0 install -y --no-install-recommends --only-upgrade \
      apt libapt-pkg6.0t64 libbrotli1 \
 && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' apt)" ge '2.8.4ubuntu0.1' \
 && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' libapt-pkg6.0t64)" ge '2.8.4ubuntu0.1' \
 && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' libbrotli1)" ge '1.1.0-2ubuntu0.1' \
 && rm -rf /var/lib/apt/lists/*

# Remove the unused pebble test server; its Go runtime is outside our dependency updates.
RUN rm -f /usr/bin/pebble \
 && groupadd --system blocbuy \
 && useradd --system --gid blocbuy blocbuy

# Produced by `./mvnw verify` in build-quality and restored into the Docker
# build context by the docker job.
COPY blocbuy-bootstrap/target/blocbuy-backend-exec.jar app.jar

USER blocbuy

# The root filesystem is read-only in both deployments; Tomcat and the JVM write only to
# /tmp. Compose mounts a tmpfs there; an ECS Fargate scratch volume copies the
# permissions of a declared VOLUME (1777 here) and is otherwise root-owned 0755.
VOLUME ["/tmp"]

EXPOSE 8589

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Health probes are orchestrator-owned and target GET /api/actuator/health on :8589.
ENTRYPOINT ["java", "-jar", "app.jar"]
