FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Crear usuario no-root
RUN addgroup -g 1000 iotest && \
    adduser -u 1000 -G iotest -s /bin/sh -D iotest

# Copiar JAR
COPY target/temperature-control-system-*.jar app.jar

# Crear directorios
RUN mkdir -p /app/config /app/logs && \
    chown -R iotest:iotest /app

USER iotest

# Variables de entorno
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV MQTT_BROKER="tcp://localhost:1883"
ENV CONFIG_PATH="/app/config/site-config.json"

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
