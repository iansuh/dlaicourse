# Number Baseball game server.
# Build context: the number-baseball-app directory (it holds backend/ and docs/play/, whose texts become the web pages):
#   docker build -f backend/server/Dockerfile -t numbaseball-server .
# Run:
#   docker run -p 8080:8080 -v nbdata:/data -e ADMIN_TOKEN=... numbaseball-server

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY backend/ backend/
COPY docs/play/ docs/play/
WORKDIR /src/backend
RUN ./gradlew --no-daemon --console=plain :server:installDist -x test

FROM eclipse-temurin:21-jre-noble
# Runs as an unprivileged user; the database lives on the /data volume (a persistent disk in production).
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /data && chown app:app /data
WORKDIR /app
COPY --from=build /src/backend/server/build/install/server/ /app/
ENV PORT=8080 \
    DATA_DIR=/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
VOLUME ["/data"]
EXPOSE 8080
USER app
CMD ["/app/bin/server"]
