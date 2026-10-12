# BlocBuy backend image. Context: backend/.
#
# The executable Spring Boot JAR (blocbuy-backend-exec.jar) is built, tested, and verified by the
# ci-backend build-quality job before this image is created.
# Docker packages that exact verified artifact instead of rebuilding source.
#
# Runtime profile is NOT baked in: compose/deploy sets SPRING_PROFILES_ACTIVE
# (e.g. ec2).

# Explicit OS line and multi-architecture digest; keep in sync with Dockerfile.ci.
FROM eclipse-temurin:21-jre-noble@sha256:22138efd69393501fccd8176ae16b01791ed71ff801b28f0359415389b17c766

WORKDIR /app

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
