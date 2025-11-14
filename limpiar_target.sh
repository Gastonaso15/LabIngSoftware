#!/bin/bash

# Script para limpiar el directorio target con permisos elevados
# Útil cuando hay problemas de permisos después de ejecutar contenedores Docker

echo "🧹 Limpiando directorio target..."

if [ -d "target" ]; then
    # Intentar sin sudo primero
    if rm -rf target/ 2>/dev/null; then
        echo "✅ Directorio target eliminado exitosamente"
    else
        echo "⚠️  Se requieren permisos elevados..."
        echo "   Ejecutando: sudo rm -rf target/"
        sudo rm -rf target/
        if [ $? -eq 0 ]; then
            echo "✅ Directorio target eliminado exitosamente con sudo"
        else
            echo "❌ Error al eliminar directorio target"
            exit 1
        fi
    fi
else
    echo "ℹ️  El directorio target no existe"
fi

echo ""
echo "✅ Limpieza completada. Ahora puedes ejecutar ./iniciar_sistema.sh"

