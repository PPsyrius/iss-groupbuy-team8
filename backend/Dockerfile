# BlocBuy backend image. Context: backend/.
#
# The executable Spring Boot JAR is built, tested, and verified by the
# ci-backend build-quality job before this image is created.
# Docker packages that exact verified artifact instead of rebuilding source.
#
# Runtime profile is NOT baked in: compose/deploy sets SPRING_PROFILES_ACTIVE
# (e.g. ec2).

FROM eclipse-temurin:24-jre

WORKDIR /app

# Base image ships pebble (Let's Encrypt's ACME test server) at /usr/bin/pebble —
# a Go test tool the app never runs, and its stdlib CVEs trip the Trivy image gate.
RUN rm -f /usr/bin/pebble \
 && groupadd --system blocbuy \
 && useradd --system --gid blocbuy blocbuy

# Produced by `./mvnw verify` in build-quality and restored into the Docker
# build context by the docker job.
COPY blocbuy-bootstrap/target/blocbuy-backend.jar app.jar

USER blocbuy

EXPOSE 8589

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# No in-image HEALTHCHECK: JRE base has no curl/wget. Orchestrator probes
# (compose healthcheck / k8s liveness) hit GET /actuator/health on :8589.
ENTRYPOINT ["java", "-jar", "app.jar"]
