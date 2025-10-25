#!/bin/bash

# ============================================
# Script de Setup para LabingSoftware
# ============================================

set -e  # Exit on error

echo "=========================================="
echo "LabingSoftware - Setup del Proyecto"
echo "=========================================="
echo ""

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 1. Verificar que estamos en el directorio correcto
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Error: No se encuentra pom.xml${NC}"
    echo "Por favor ejecuta este script desde el directorio raíz del proyecto"
    exit 1
fi

echo -e "${BLUE}1. Creando estructura de directorios...${NC}"

# Crear estructura main
mkdir -p src/main/java/com/iotest/domain/model
mkdir -p src/main/java/com/iotest/domain/service
mkdir -p src/main/java/com/iotest/control
mkdir -p src/main/java/com/iotest/infrastructure/mqtt
mkdir -p src/main/java/com/iotest/infrastructure/rest
mkdir -p src/main/java/com/iotest/infrastructure/config
mkdir -p src/main/java/com/iotest/api/dto
mkdir -p src/main/resources

# Crear estructura test
mkdir -p src/test/java/com/iotest/unit/domain
mkdir -p src/test/java/com/iotest/unit/control
mkdir -p src/test/java/com/iotest/unit/infrastructure
mkdir -p src/test/java/com/iotest/integration

# Otros directorios
mkdir -p docker
mkdir -p config
mkdir -p logs
mkdir -p docs

echo -e "${GREEN}✓ Estructura creada${NC}"
echo ""

# 2. Crear archivos de configuración básicos
echo -e "${BLUE}2. Creando archivos de configuración...${NC}"

# application.yml
cat > src/main/resources/application.yml << 'EOF'
spring:
  application:
    name: labingsoftware-temperature-control

# MQTT Configuration
mqtt:
  broker: ${MQTT_BROKER:tcp://localhost:1883}
  client-id: temp-controller-${random.uuid}
  auto-reconnect: true

# System Configuration
temperature-control:
  config-file: ${CONFIG_PATH:classpath:site-config.json}
  sensor-timeout-seconds: 300
  monitoring-interval-seconds: 30

# Logging
logging:
  level:
    root: INFO
    com.iotest: DEBUG
  file:
    name: logs/temperature-control.log
EOF

# site-config.json
cat > src/main/resources/site-config.json << 'EOF'
{
  "siteName": "Casa Principal",
  "maxPowerWatts": 5000,
  "rooms": [
    {
      "id": "living_room",
      "name": "Living Room",
      "desiredTemperature": 22.0,
      "temperatureTolerance": 0.5,
      "powerConsumptionWatts": 1500,
      "priority": 1,
      "sensorTopic": "home/sensors/living_room/temperature",
      "switchUrl": "http://switch-living:8080/api/power"
    }
  ]
}
EOF

# .gitignore
cat > .gitignore << 'EOF'
# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
.mvn/

# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# Logs
logs/
*.log

# OS
.DS_Store
Thumbs.db

# Config
config/production_config.json
EOF

echo -e "${GREEN}✓ Archivos de configuración creados${NC}"
echo ""

# 3. Descargar Maven Wrapper si no existe
if [ ! -f "mvnw" ]; then
    echo -e "${BLUE}3. Descargando Maven Wrapper...${NC}"
    mvn wrapper:wrapper
    chmod +x mvnw
    echo -e "${GREEN}✓ Maven Wrapper instalado${NC}"
else
    echo -e "${GREEN}✓ Maven Wrapper ya existe${NC}"
fi
echo ""

# 4. Compilar proyecto
echo -e "${BLUE}4. Compilando proyecto...${NC}"
./mvnw clean compile

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Compilación exitosa${NC}"
else
    echo -e "${RED}✗ Error en compilación${NC}"
    exit 1
fi
echo ""

# 5. Crear README.md
echo -e "${BLUE}5. Creando README.md...${NC}"
cat > README.md << 'EOF'
# LabingSoftware - Sistema de Control de Temperatura IoT

Sistema de control inteligente de calefacción con gestión de potencia y resiliencia.

## Inicio Rápido

```bash
# Compilar
./mvnw clean compile

# Tests
./mvnw test

# Ejecutar
./mvnw spring-boot:run
```

## Estructura

```
src/main/java/com/iotest/
├── domain/          # Modelos de dominio
├── control/         # Lógica de control
├── infrastructure/  # Integraciones
└── api/            # API REST
```

## Desarrollo TDD

1. Escribir test que falle
2. Implementar código mínimo
3. Refactorizar

## Comandos Útiles

```bash
# Tests con cobertura
./mvnw test jacoco:report

# Solo tests unitarios
./mvnw test -Dtest=*Test

# Package
./mvnw package -DskipTests

# Ver cobertura
open target/site/jacoco/index.html
```

## Entregas

- Entrega 4 (27/10): Tests unitarios con TDD
- Entrega 5 (10/11): Tests de integración
- Entrega 6 (12/11): Sistema completo en Docker

## Equipo

UTEC - Ingeniería de Software
EOF

echo -e "${GREEN}✓ README.md creado${NC}"
echo ""

# 6. Mostrar resumen
echo -e "${GREEN}=========================================="
echo "Setup completado exitosamente!"
echo "==========================================${NC}"
echo ""
echo "Estructura del proyecto:"
tree -L 3 src/ 2>/dev/null || find src/ -type d | head -20
echo ""
echo -e "${BLUE}Próximos pasos:${NC}"
echo "1. Copiar las clases base a src/main/java/com/iotest/"
echo "2. Copiar los tests a src/test/java/com/iotest/"
echo "3. Ejecutar: ./mvnw test"
echo "4. Verificar cobertura: ./mvnw test jacoco:report"
echo ""
echo -e "${GREEN}¡Listo para empezar con TDD!${NC}"
echo ""

# 7. Crear Makefile
echo -e "${BLUE}6. Creando Makefile...${NC}"
cat > Makefile << 'EOF'
.PHONY: help clean test test-unit test-integration test-coverage package run docker-build

# Colores
BLUE := \033[0;34m
GREEN := \033[0;32m
NC := \033[0m

help: ## Muestra esta ayuda
	@echo "$(BLUE)LabingSoftware - Comandos disponibles:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-20s$(NC) %s\n", $1, $2}'

clean: ## Limpia archivos temporales
	./mvnw clean
	@echo "$(GREEN)✓ Limpieza completada$(NC)"

compile: ## Compila el proyecto
	./mvnw compile
	@echo "$(GREEN)✓ Compilación exitosa$(NC)"

test: ## Ejecuta todos los tests
	./mvnw test
	@echo "$(GREEN)✓ Tests completados$(NC)"

test-unit: ## Ejecuta solo tests unitarios
	./mvnw test -Dtest=*Test
	@echo "$(GREEN)✓ Tests unitarios completados$(NC)"

test-integration: ## Ejecuta tests de integración
	./mvnw verify
	@echo "$(GREEN)✓ Tests de integración completados$(NC)"

test-coverage: ## Tests con reporte de cobertura
	./mvnw test jacoco:report
	@echo "$(GREEN)✓ Reporte generado en: target/site/jacoco/index.html$(NC)"

test-watch: ## Ejecuta un test específico (usar TEST=NombreTest)
	@if [ -z "$(TEST)" ]; then \
		echo "Uso: make test-watch TEST=RoomTest"; \
	else \
		./mvnw test -Dtest=$(TEST); \
	fi

package: ## Genera JAR
	./mvnw clean package -DskipTests
	@echo "$(GREEN)✓ JAR generado en: target/$(NC)"

run: ## Ejecuta la aplicación
	./mvnw spring-boot:run

run-debug: ## Ejecuta en modo debug
	./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

docker-build: ## Construye imagen Docker
	./mvnw package -DskipTests
	docker build -f docker/Dockerfile -t labingsoftware:latest .
	@echo "$(GREEN)✓ Imagen Docker construida$(NC)"

docker-run: ## Ejecuta contenedor Docker
	docker run --name labingsoftware \
		-p 8080:8080 \
		-e MQTT_BROKER=tcp://localhost:1883 \
		-v $(pwd)/config:/app/config:ro \
		-v $(pwd)/logs:/app/logs \
		labingsoftware:latest

docker-stop: ## Detiene contenedor Docker
	docker stop labingsoftware || true
	docker rm labingsoftware || true

# Entregas
entrega4-check: ## Verifica requisitos de entrega 4
	@echo "$(BLUE)=== Verificación Entrega 4 ===$(NC)"
	@echo ""
	@echo "1. Verificando estructura..."
	@test -f src/main/java/com/iotest/domain/model/Room.java || (echo "✗ Falta Room.java" && exit 1)
	@test -f src/main/java/com/iotest/control/PowerManager.java || (echo "✗ Falta PowerManager.java" && exit 1)
	@test -f src/main/java/com/iotest/control/TemperatureController.java || (echo "✗ Falta TemperatureController.java" && exit 1)
	@echo "$(GREEN)✓ Estructura correcta$(NC)"
	@echo ""
	@echo "2. Ejecutando tests unitarios..."
	@./mvnw test
	@echo ""
	@echo "3. Verificando cobertura (mínimo 80%)..."
	@./mvnw jacoco:check
	@echo ""
	@echo "$(GREEN)✓ Entrega 4 lista$(NC)"

entrega5-test: ## Tests para entrega 5
	@echo "$(BLUE)=== Entrega 5: Tests de Integración ===$(NC)"
	./mvnw verify
	@echo "$(GREEN)✓ Entrega 5 completada$(NC)"

entrega6-package: ## Genera paquete para entrega 6
	@echo "$(BLUE)=== Generando paquete Entrega 6 ===$(NC)"
	./mvnw clean package -DskipTests
	docker build -f docker/Dockerfile -t labingsoftware:latest .
	mkdir -p entrega6
	docker save labingsoftware:latest -o entrega6/labingsoftware-image.tar
	cp -r config entrega6/
	cp -r docs entrega6/
	cp README.md entrega6/
	tar -czf entrega6.tar.gz entrega6/
	rm -rf entrega6
	@echo "$(GREEN)✓ Paquete generado: entrega6.tar.gz$(NC)"

# Utilidades
logs: ## Muestra logs
	tail -f logs/temperature-control.log

tree: ## Muestra estructura del proyecto
	tree -L 4 -I 'target|.idea|.mvn' src/

info: ## Información del sistema
	@echo "$(BLUE)=== Información del Sistema ===$(NC)"
	@echo "Java: $(java -version 2>&1 | head -1)"
	@echo "Maven: $(./mvnw -version | head -1)"
	@echo "Docker: $(docker --version 2>/dev/null || echo 'No instalado')"

status: ## Estado del proyecto
	@echo "$(BLUE)=== Estado del Proyecto ===$(NC)"
	@echo ""
	@echo "Tests unitarios:"
	@./mvnw test -q 2>&1 | grep "Tests run:" || echo "No ejecutados"
	@echo ""
	@echo "Cobertura:"
	@./mvnw jacoco:report -q 2>&1 | grep "Total" || echo "No generada"
	@echo ""
	@echo "Líneas de código:"
	@find src/main/java -name "*.java" -exec wc -l {} + | tail -1

# Shortcuts
t: test ## Shortcut para test
tc: test-coverage ## Shortcut para test-coverage
r: run ## Shortcut para run
c: clean ## Shortcut para clean
EOF

chmod +x Makefile
echo -e "${GREEN}✓ Makefile creado$(NC)"
echo ""

# 8. Crear Dockerfile
echo -e "${BLUE}7. Creando Dockerfile...${NC}"
cat > docker/Dockerfile << 'EOF'
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
EOF

echo -e "${GREEN}✓ Dockerfile creado$(NC)"
echo ""

# 9. Crear docker-compose.yml
cat > docker/docker-compose.yml << 'EOF'
version: '3.8'

services:
  mosquitto:
    image: eclipse-mosquitto:2.0
    container_name: mqtt-broker
    ports:
      - "1883:1883"
    networks:
      - iotest-network

  labingsoftware:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: labingsoftware
    depends_on:
      - mosquitto
    environment:
      - MQTT_BROKER=tcp://mosquitto:1883
      - CONFIG_PATH=/app/config/site-config.json
    volumes:
      - ../config:/app/config:ro
      - ../logs:/app/logs
    ports:
      - "8080:8080"
    networks:
      - iotest-network

networks:
  iotest-network:
    driver: bridge
EOF

echo -e "${GREEN}✓ docker-compose.yml creado$(NC)"
echo ""

echo -e "${GREEN}=========================================="
echo "¡Setup completado exitosamente!"
echo "==========================================${NC}"
echo ""
echo -e "${BLUE}Comandos disponibles:${NC}"
echo "  make help          - Ver todos los comandos"
echo "  make test          - Ejecutar tests"
echo "  make test-coverage - Tests con cobertura"
echo "  make run           - Ejecutar aplicación"
echo ""
echo -e "${BLUE}Para empezar con TDD (Entrega 4):${NC}"
echo "  1. Crea las clases en src/main/java/com/iotest/"
echo "  2. Crea los tests en src/test/java/com/iotest/"
echo "  3. make test"
echo ""
echo -e "${GREEN}¡Todo listo para desarrollar! 🚀${NC}"