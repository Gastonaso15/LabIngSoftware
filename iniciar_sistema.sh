#!/bin/bash

# Script para iniciar el sistema completo de control de temperatura
# Levanta el simulador, el broker MQTT, y labingsoftware con el monitor de terminal

set -e  # Salir si hay algún error

echo "🚀 Iniciando Sistema de Control de Temperatura..."
echo ""

# Colores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Función para verificar si un comando existe
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Verificar Docker
echo -e "${YELLOW}📦 Verificando Docker...${NC}"
if ! command_exists docker; then
    echo -e "${RED}❌ Docker no está instalado. Por favor instala Docker primero.${NC}"
    exit 1
fi

if ! docker ps >/dev/null 2>&1; then
    echo -e "${RED}❌ Docker no está corriendo. Por favor inicia Docker.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker está corriendo${NC}"
echo ""

# Paso 1: Iniciar el simulador (caja negra)
echo -e "${YELLOW}🐳 Paso 1: Iniciando simulador (caja negra)...${NC}"
SIMULATOR_DIR="cajaNegra-main/cajaNegra-main/blackBox"

if [ -d "$SIMULATOR_DIR" ]; then
    cd "$SIMULATOR_DIR"
    echo "   Construyendo simulador..."
    docker compose build --no-cache simulator >/dev/null 2>&1 || true
    echo "   Iniciando servicios (broker MQTT + simulador)..."
    docker compose up -d
    cd - >/dev/null
    echo -e "${GREEN}✅ Simulador iniciado${NC}"
else
    echo -e "${YELLOW}⚠️  Directorio del simulador no encontrado: $SIMULATOR_DIR${NC}"
    echo -e "${YELLOW}   Continuando sin simulador...${NC}"
fi
echo ""

# Esperar a que el broker MQTT esté listo
echo -e "${YELLOW}⏳ Esperando 5 segundos para que el broker MQTT esté listo...${NC}"
sleep 5
echo ""

# Paso 2: Compilar labingsoftware
echo -e "${YELLOW}🔨 Paso 2: Compilando labingsoftware...${NC}"
if command_exists mvn; then
    mvn clean package -DskipTests >/dev/null 2>&1
    echo -e "${GREEN}✅ Compilación completada${NC}"
else
    echo -e "${YELLOW}⚠️  Maven no encontrado, usando Maven Wrapper...${NC}"
    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        ./mvnw clean package -DskipTests >/dev/null 2>&1
        echo -e "${GREEN}✅ Compilación completada${NC}"
    else
        echo -e "${RED}❌ No se encontró Maven ni Maven Wrapper${NC}"
        exit 1
    fi
fi
echo ""

# Paso 3: Iniciar labingsoftware con docker-compose
echo -e "${YELLOW}🐳 Paso 3: Iniciando labingsoftware con docker-compose...${NC}"
cd docker
echo "   Ejecutando: docker compose up -d --build"
docker compose up -d --build
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ labingsoftware iniciado (docker-compose levantado)${NC}"
else
    echo -e "${RED}❌ Error al levantar docker-compose${NC}"
    exit 1
fi
cd - >/dev/null
echo ""

# Esperar a que Spring Boot inicie
echo -e "${YELLOW}⏳ Esperando 10 segundos para que Spring Boot inicie completamente...${NC}"
sleep 10
echo ""

# Verificar que el sistema está funcionando
echo -e "${YELLOW}📊 Verificando estado del sistema...${NC}"
echo ""

MAX_RETRIES=5
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -s http://localhost:8081/api/health >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Sistema está funcionando correctamente!${NC}"
        echo ""
        
        # Mostrar información del sistema
        echo -e "${CYAN}📋 Información del Sistema:${NC}"
        echo -e "${CYAN}   API REST: http://localhost:8081/api${NC}"
        echo -e "${CYAN}   Health: http://localhost:8081/api/health${NC}"
        echo -e "${CYAN}   Estado: http://localhost:8081/api/system/status${NC}"
        echo ""
        
        # Mostrar estado actual
        echo -e "${CYAN}📊 Estado Actual:${NC}"
        curl -s http://localhost:8081/api/system/status | python3 -m json.tool 2>/dev/null || \
        curl -s http://localhost:8081/api/system/status | jq 2>/dev/null || \
        curl -s http://localhost:8081/api/system/status
        echo ""
        
        echo -e "${GREEN}✅ Todo listo!${NC}"
        echo ""
        echo -e "${CYAN}📝 Comandos útiles:${NC}"
        echo -e "${CYAN}   Ver logs: docker compose -f docker/docker-compose.yml logs -f${NC}"
        echo -e "${CYAN}   Ver estado: curl http://localhost:8081/api/system/status | jq${NC}"
        echo -e "${CYAN}   Detener: docker compose -f docker/docker-compose.yml down${NC}"
        echo ""
        echo -e "${YELLOW}💡 El monitor de terminal está habilitado y mostrará el estado cada 5 segundos en los logs${NC}"
        echo -e "${YELLOW}   Ver logs: docker compose -f docker/docker-compose.yml logs -f labingsoftware${NC}"
        exit 0
    else
        RETRY_COUNT=$((RETRY_COUNT + 1))
        if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then
            echo -e "${YELLOW}   Intento $RETRY_COUNT/$MAX_RETRIES: Esperando...${NC}"
            sleep 3
        fi
    fi
done

echo -e "${YELLOW}⚠️  El sistema está iniciando, puede tardar unos segundos más...${NC}"
echo -e "${CYAN}   Intenta: curl http://localhost:8081/api/health${NC}"
echo ""
echo -e "${CYAN}📝 Ver logs: docker compose -f docker/docker-compose.yml logs -f labingsoftware${NC}"

