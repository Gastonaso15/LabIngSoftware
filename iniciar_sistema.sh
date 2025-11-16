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

# Esperar a que el broker MQTT y el simulador estén listos
echo -e "${YELLOW}⏳ Esperando 5 segundos para que el broker MQTT/simulador estén listos...${NC}"
sleep 5
echo ""

# Paso 1.1: Actualizar config/site-config.json desde el simulador y adaptar URLs
echo -e "${YELLOW}🧩 Actualizando config/site-config.json desde el simulador...${NC}"
CONFIG_DIR="config"
CONFIG_FILE="${CONFIG_DIR}/site-config.json"
TMP_FILE="${CONFIG_FILE}.tmp"
if curl -sSf -X GET "http://localhost:8080/site-config" -o "${TMP_FILE}"; then
    echo "   Config recibido desde simulador (http://localhost:8080/site-config)"
    # Adaptar URLs de switches de localhost -> host.docker.internal y completar sensores sim/ht -> sim/ht/<n>
    if command -v jq >/dev/null 2>&1; then
        jq '(.rooms[]) |= (
              .switch |= sub("http://localhost:8080/switch/"; "http://host.docker.internal:8080/switch/")
            | .sensor = (
                if (.sensor | tostring) | test("^sim/ht/?$") then
                  "sim/ht/" + (.switch | capture("switch/(?<n>[0-9]+)$").n)
                else
                  .sensor
                end)
          )' "${TMP_FILE}" > "${TMP_FILE}.adapted" && mv "${TMP_FILE}.adapted" "${TMP_FILE}"
    else
        # Fallback sin jq: usar python para transformar JSON
        python3 - "$TMP_FILE" << 'PY'
import json, re, sys, io
path = sys.argv[1]
with open(path, 'r', encoding='utf-8') as f:
    data = json.load(f)
rooms = data.get('rooms', [])
for room in rooms:
    sw = room.get('switch', '')
    # Adaptar URL de switch
    room['switch'] = sw.replace('http://localhost:8080/switch/', 'http://host.docker.internal:8080/switch/')
    # Completar sensor si es sim/ht o sim/ht/
    sensor = room.get('sensor', '')
    if sensor in ('sim/ht', 'sim/ht/'):
        m = re.search(r'switch/(\d+)$', room['switch'])
        if m:
            room['sensor'] = f"sim/ht/{m.group(1)}"
with open(path, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
PY
        # Asegurar también el reemplazo básico de URLs si python no alteró todas
        sed -E 's#http://localhost:8080/switch/#http://host.docker.internal:8080/switch/#g' "${TMP_FILE}" > "${TMP_FILE}.adapted" && mv "${TMP_FILE}.adapted" "${TMP_FILE}"
    fi
    # Mover al archivo definitivo actualizado
    mv "${TMP_FILE}" "${CONFIG_FILE}"
    echo -e "${GREEN}✅ config/site-config.json actualizado y URLs adaptadas${NC}"
else
    echo -e "${YELLOW}⚠️  No se pudo obtener site-config del simulador. Se mantiene el archivo existente.${NC}"
    rm -f "${TMP_FILE}" 2>/dev/null || true
fi
echo ""

# Paso 2: Detener contenedores existentes de labingsoftware (si están corriendo)
echo -e "${YELLOW}🛑 Paso 2: Deteniendo contenedores existentes de labingsoftware (si están corriendo)...${NC}"
DOCKER_DIR="docker"

if [ -d "$DOCKER_DIR" ]; then
    cd "$DOCKER_DIR"
    if [ -f "docker-compose.yml" ]; then
        echo "   Deteniendo contenedores existentes..."
        docker compose down 2>/dev/null || docker-compose down 2>/dev/null || true
        
        # Esperar un momento para que los contenedores se detengan completamente
        sleep 2
        
        # Verificar si el contenedor aún está corriendo y forzar detención
        if docker ps --filter "name=labingsoftware" --format "{{.Names}}" | grep -q labingsoftware; then
            echo "   Forzando detención del contenedor..."
            docker stop labingsoftware 2>/dev/null || true
            docker rm labingsoftware 2>/dev/null || true
            sleep 1
        fi
        
        # Eliminar contenedores detenidos también
        docker rm $(docker ps -aq --filter "name=labingsoftware") 2>/dev/null || true
        
        echo -e "${GREEN}✅ Contenedores detenidos${NC}"
    fi
    cd - >/dev/null
fi

# Verificar si hay problemas de permisos en target
TARGET_HAS_PERMISSION_ISSUES=false
if [ -d "target" ]; then
    echo "   Verificando permisos del directorio target..."
    # Intentar cambiar permisos primero
    if ! chmod -R u+w target/ 2>/dev/null; then
        TARGET_HAS_PERMISSION_ISSUES=true
    fi
    # Intentar eliminar un archivo de prueba
    if [ -f "target/test.txt" ]; then
        if ! rm -f target/test.txt 2>/dev/null; then
            TARGET_HAS_PERMISSION_ISSUES=true
        fi
    fi
    
    if [ "$TARGET_HAS_PERMISSION_ISSUES" = true ]; then
        echo -e "${YELLOW}⚠️  Detectado problema de permisos en target/${NC}"
        echo -e "${YELLOW}   Por favor ejecuta manualmente antes de continuar:${NC}"
        echo -e "${CYAN}   sudo rm -rf target/${NC}"
        echo ""
        echo -e "${RED}❌ No se puede continuar sin limpiar el directorio target/${NC}"
        exit 1
    else
        echo "   Limpiando directorio target..."
        rm -rf target/ 2>/dev/null || true
        sleep 1
    fi
fi
echo ""

# Paso 3: Compilar labingsoftware
echo -e "${YELLOW}🔨 Paso 3: Compilando labingsoftware...${NC}"

if command_exists mvn; then
    if mvn clean package -DskipTests; then
        echo -e "${GREEN}✅ Compilación completada${NC}"
    else
        echo -e "${RED}❌ Error en la compilación. Revisa los mensajes de error arriba.${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}⚠️  Maven no encontrado, usando Maven Wrapper...${NC}"
    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        if ./mvnw clean package -DskipTests; then
            echo -e "${GREEN}✅ Compilación completada${NC}"
        else
            echo -e "${RED}❌ Error en la compilación. Revisa los mensajes de error arriba.${NC}"
            exit 1
        fi
    else
        echo -e "${RED}❌ No se encontró Maven ni Maven Wrapper${NC}"
        exit 1
    fi
fi
echo ""

# Paso 4: Iniciar labingsoftware con docker-compose
echo -e "${YELLOW}🐳 Paso 4: Iniciando labingsoftware con docker-compose...${NC}"
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

