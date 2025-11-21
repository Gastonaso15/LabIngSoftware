#!/bin/bash

# Script simplificado para ver el monitor de terminal
# Muestra solo información relevante (filtra logs DEBUG repetitivos)
# Ejecuta: ./ver_monitor_simplificado.sh

echo "📊 Monitor simplificado - Estado de habitaciones y energía"
echo "   Presiona Ctrl+C para salir"
echo "   Mostrando solo el output formateado del monitor + tarifa"
echo ""

cd docker

# Verificar que el contenedor está corriendo
if ! docker compose ps labingsoftware 2>/dev/null | grep -q labingsoftware; then
    echo "⚠️  El contenedor 'labingsoftware' no está corriendo"
    echo "   Ejecuta primero: ./iniciar_sistema.sh"
    exit 1
fi

# Función para calcular la tarifa actual (testContract cambia cada 30 segundos)
# Mismo algoritmo que EnergyCost.energyZone() en Java
get_current_tariff() {
    local current_time_ms=$(($(date +%s) * 1000))
    local zone_duration_ms=30000  # 30 segundos en milisegundos
    local base=$((current_time_ms / zone_duration_ms))
    local zone=$((base % 2))
    
    # Si zone == 1 es HIGH, si zone == 0 es LOW
    if [ $zone -eq 1 ]; then
        echo "HIGH"
    else
        echo "LOW"
    fi
}

# Configuración: delay entre actualizaciones mostradas (en segundos)
# Por defecto: 5 segundos (el monitor se actualiza cada 5 segundos, así que muestra cada actualización)
DELAY_BETWEEN_UPDATES=${1:-5}

echo "Mostrando solo el estado del monitor (habitaciones y energía)..."
echo "Delay entre actualizaciones: ${DELAY_BETWEEN_UPDATES} segundos"
echo ""

# Variable para rastrear si ya mostramos una actualización
last_update_shown=false
block_buffer=""
in_block=false

# Función para limpiar códigos ANSI de una línea (más completa)
clean_ansi() {
    echo "$1" | sed -E 's/\x1b\[[0-9;]*[a-zA-Z]//g' | sed 's/\x1b\[H//g' | sed 's/\x1b\[2J//g' | sed 's/\x1b\[J//g' | sed 's/\x1b\[K//g' | tr -d '\r'
}

docker compose logs -f labingsoftware 2>&1 | \
    while IFS= read -r line || [ -n "$line" ]; do
        # Extraer solo el contenido después del prefijo de Docker (si existe)
        # Formato típico: "labingsoftware  | contenido" o solo "contenido"
        if echo "$line" | grep -q "|"; then
            content_line=$(echo "$line" | sed -E 's/^[^|]*\|[[:space:]]*//')
        else
            content_line="$line"
        fi
        
        # Limpiar códigos ANSI de la línea
        cleaned_line=$(clean_ansi "$content_line")
        
        # Saltar líneas vacías después de limpiar ANSI
        if [ -z "$cleaned_line" ] || [ "$cleaned_line" = "" ]; then
            continue
        fi
        
        # Filtrar solo líneas relevantes del monitor
        if echo "$cleaned_line" | grep -qE "(║|╔|╚|│|┌|└|├|╗|╝|SISTEMA|ENERG|HABITACIONES|Presiona Ctrl|Temperatura|Calefaccion|Sensor|Habitacion|Máxima|Consumo|Disponible|°C|Ultima act|deseada)"; then
            # Detectar inicio de un bloque de monitor (línea con ╔)
            if echo "$cleaned_line" | grep -q "╔"; then
                # Si ya mostramos una actualización antes, esperar antes de mostrar la siguiente
                if [ "$last_update_shown" = true ]; then
                    sleep "$DELAY_BETWEEN_UPDATES"
                fi
                # Limpiar buffer y empezar nuevo bloque
                block_buffer="$cleaned_line"
                in_block=true
                last_update_shown=false
            # Detectar fin de un bloque de monitor (línea con "Presiona Ctrl")
            elif echo "$cleaned_line" | grep -q "Presiona Ctrl"; then
                if [ "$in_block" = true ]; then
                    block_buffer="${block_buffer}\n${cleaned_line}"
                    # Procesar el bloque completo y agregar tarifa dentro del bloque de ENERGÍA
                    current_tariff=$(get_current_tariff)
                    # Insertar la tarifa justo después de "Disponible:" y antes del cierre del bloque (└)
                    # Usar awk para insertar la línea en el lugar correcto
                    processed_block=$(echo -e "$block_buffer" | awk -v tariff="$current_tariff" '
                        / │ Disponible:/ {
                            print $0
                            printf " │ Tarifa:          %-8s                                           │\n", tariff
                            next
                        }
                        { print }
                    ')
                    # Mostrar el bloque procesado
                    echo -e "$processed_block"
                    echo ""
                    last_update_shown=true
                    block_buffer=""
                    in_block=false
                fi
            # Acumular líneas dentro del bloque
            elif [ "$in_block" = true ]; then
                block_buffer="${block_buffer}\n${cleaned_line}"
            fi
        fi
    done

