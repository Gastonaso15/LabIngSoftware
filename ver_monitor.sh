#!/bin/bash

# Script para ver el monitor de terminal en tiempo real
# Ejecuta: ./ver_monitor.sh

echo "📊 Mostrando monitor de terminal en tiempo real..."
echo "   Presiona Ctrl+C para salir"
echo ""

cd docker
docker compose logs -f labingsoftware

