#!/bin/bash

# Script para detener el sistema completo de control de temperatura
# Detiene todos los contenedores Docker relacionados con el sistema

set -e  # Salir si hay algún error

echo "🛑 Deteniendo Sistema de Control de Temperatura..."
echo ""

# Colores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Función para verificar si un directorio existe
dir_exists() {
    [ -d "$1" ]
}

# Paso 1: Detener contenedores de labingsoftware
echo -e "${YELLOW}🛑 Paso 1: Deteniendo contenedores de labingsoftware...${NC}"
DOCKER_DIR="docker"

if dir_exists "$DOCKER_DIR"; then
    cd "$DOCKER_DIR"
    if [ -f "docker-compose.yml" ]; then
        echo "   Deteniendo contenedores..."
        docker compose down 2>/dev/null || docker-compose down 2>/dev/null || true
        echo -e "${GREEN}✅ Contenedores de labingsoftware detenidos${NC}"
    else
        echo -e "${YELLOW}⚠️  No se encontró docker-compose.yml en $DOCKER_DIR${NC}"
    fi
    cd - >/dev/null
else
    echo -e "${YELLOW}⚠️  Directorio $DOCKER_DIR no encontrado${NC}"
fi
echo ""

# Paso 2: Detener simulador (caja negra) y broker MQTT
echo -e "${YELLOW}🛑 Paso 2: Deteniendo simulador y broker MQTT...${NC}"
SIMULATOR_DIR="cajaNegra-main/cajaNegra-main/blackBox"

if dir_exists "$SIMULATOR_DIR"; then
    cd "$SIMULATOR_DIR"
    if [ -f "docker-compose.yml" ]; then
        echo "   Deteniendo servicios (simulador + broker MQTT)..."
        docker compose down 2>/dev/null || docker-compose down 2>/dev/null || true
        echo -e "${GREEN}✅ Simulador y broker MQTT detenidos${NC}"
    else
        echo -e "${YELLOW}⚠️  No se encontró docker-compose.yml en $SIMULATOR_DIR${NC}"
    fi
    cd - >/dev/null
else
    echo -e "${YELLOW}⚠️  Directorio del simulador no encontrado: $SIMULATOR_DIR${NC}"
fi
echo ""

# Paso 3: Verificar que todos los contenedores relacionados están detenidos
echo -e "${YELLOW}🔍 Paso 3: Verificando estado de contenedores...${NC}"

# Buscar contenedores relacionados que aún estén corriendo
RUNNING_CONTAINERS=$(docker ps --filter "name=labingsoftware|simulator|broker-mqtt" --format "{{.Names}}" 2>/dev/null || true)

if [ -z "$RUNNING_CONTAINERS" ]; then
    echo -e "${GREEN}✅ Todos los contenedores del sistema están detenidos${NC}"
else
    echo -e "${YELLOW}⚠️  Los siguientes contenedores aún están corriendo:${NC}"
    echo "$RUNNING_CONTAINERS" | while read container; do
        echo "   - $container"
    done
    echo ""
    echo -e "${YELLOW}   Puedes detenerlos manualmente con:${NC}"
    echo -e "${CYAN}   docker stop $RUNNING_CONTAINERS${NC}"
fi
echo ""

# Paso 4: Limpiar contenedores detenidos (opcional)
echo -e "${YELLOW}🧹 Paso 4: Limpiando contenedores detenidos...${NC}"
docker container prune -f >/dev/null 2>&1 || true
echo -e "${GREEN}✅ Limpieza completada${NC}"
echo ""

echo -e "${GREEN}✅ Sistema detenido completamente${NC}"
echo ""
echo -e "${CYAN}📝 Comandos útiles:${NC}"
echo -e "${CYAN}   Ver contenedores corriendo: docker ps${NC}"
echo -e "${CYAN}   Ver todos los contenedores: docker ps -a${NC}"
echo -e "${CYAN}   Iniciar sistema de nuevo: ./iniciar_sistema.sh${NC}"
echo ""

