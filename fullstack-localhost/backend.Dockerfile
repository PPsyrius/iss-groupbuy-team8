# Localhost-only backend image built from source. Context: backend/.
# The delivery images (backend/Dockerfile, Dockerfile.ci) package the JAR that CI verified; this one
# compiles it so a machine without JDK 21 can run the stack. Tests and quality gates run in CI.

# Keep the JDK on 21 (the enforcer gate requires [21,22)); match the wrapper's Maven version.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build
# Only what the Maven reactor reads, never the whole context: the root pom, the files its plugin
# configuration names, and the six modules it lists.
COPY pom.xml checkstyle.xml spotbugs-excludes.xml ./
COPY blocbuy-client blocbuy-client
COPY blocbuy-domain blocbuy-domain
COPY blocbuy-application blocbuy-application
COPY blocbuy-adapter blocbuy-adapter
COPY blocbuy-infrastructure blocbuy-infrastructure
COPY blocbuy-bootstrap blocbuy-bootstrap
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -pl blocbuy-bootstrap -am \
      -DskipTests -Djacoco.skip=true \
      -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true

FROM eclipse-temurin:21.0.12_8-jre
WORKDIR /app
# As in backend/Dockerfile: drop the unused pebble binary the base image ships, then run unprivileged.
RUN rm -f /usr/bin/pebble \
 && groupadd --system blocbuy \
 && useradd --system --gid blocbuy blocbuy
COPY --from=build /build/blocbuy-bootstrap/target/blocbuy-backend-exec.jar app.jar
USER blocbuy
EXPOSE 8589
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "app.jar"]
