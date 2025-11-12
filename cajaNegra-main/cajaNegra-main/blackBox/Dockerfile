FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copiá tu jar sombreado al contenedor
COPY /simulator-1.0-SNAPSHOT-shaded.jar /app/app.jar

# Carpetas para volúmenes
RUN mkdir -p /config /logs

# Vars de entorno (podés ajustarlas en compose)
ENV SIMULATION_CONFIG="/config/simulation_config.json" \
    LOG_PATH="/logs/app.log" \
    LOG_LEVEL="INFO" \
    JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
