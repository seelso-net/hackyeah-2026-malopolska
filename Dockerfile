# Builds the API from source and runs it on a small JRE image.
#   docker build -t needs-platform .
# Needs a PostgreSQL with pgvector: see docker-compose.yml, or set DB_URL / DB_USER / DB_PASSWORD.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml .
# Cache dependencies in their own layer; the real build below fails loudly if something is missing.
RUN mvn -B -q dependency:go-offline -DskipTests || true
COPY backend/src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data/media && chown -R 1001:0 /app/data
COPY --from=build --chown=1001:0 /src/target/quarkus-app/lib/ lib/
COPY --from=build --chown=1001:0 /src/target/quarkus-app/*.jar ./
COPY --from=build --chown=1001:0 /src/target/quarkus-app/app/ app/
COPY --from=build --chown=1001:0 /src/target/quarkus-app/quarkus/ quarkus/
USER 1001
ENV MEDIA_DIR=/app/data/media \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
