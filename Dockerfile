# Build on the host first (avoids Maven TLS issues inside some Docker networks):
#   .\gradlew.bat installDist
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY build/install/leetcode-progress-tracker/ ./
USER app
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/leetcode-progress-tracker"]
