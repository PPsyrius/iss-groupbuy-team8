# Localhost-only backend image built from source. Context: backend/.
# The delivery images (backend/Dockerfile, Dockerfile.ci) package the JAR that CI verified; this one
# compiles it so a machine without JDK 21 can run the stack. Tests and quality gates run in CI.

# Keep the JDK on 21 (the enforcer gate requires [21,22)); match the wrapper's Maven version.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -pl blocbuy-bootstrap -am \
      -DskipTests -Djacoco.skip=true \
      -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system blocbuy && useradd --system --gid blocbuy blocbuy
USER blocbuy
COPY --from=build /build/blocbuy-bootstrap/target/blocbuy-backend.jar app.jar
EXPOSE 8589
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "app.jar"]
