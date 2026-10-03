# One image: the API with the web app inside it.
#   docker build -t needs-platform .
# Needs a PostgreSQL with pgvector: see docker-compose.yml, or set DB_URL / DB_USER / DB_PASSWORD.

# 1. The web app (Ionic React + Vite), built to static files
FROM node:22-slim AS web
WORKDIR /app
COPY app/package.json app/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY app/ ./
RUN npm run build

# 2. The API (Quarkus), serving the web app from META-INF/resources
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml .
# Cache dependencies in their own layer; the real build below fails loudly if something is missing.
RUN mvn -B -q dependency:go-offline -DskipTests || true
COPY backend/src src
COPY --from=web /app/dist src/main/resources/META-INF/resources
RUN mvn -B -q -DskipTests package

# 3. A small runtime image
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
# Health: GET /q/health/live (process up) and /q/health/ready (database reachable)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
