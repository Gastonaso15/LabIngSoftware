# Cambios de Puertos

Se han actualizado los puertos del sistema para evitar conflictos cuando se ejecuten múltiples instancias del laboratorio en la misma máquina.

## Nuevos Puertos

| Servicio | Puerto Anterior | Puerto Nuevo | Descripción |
|----------|----------------|--------------|-------------|
| **API REST (Spring Boot)** | 8081 | **18081** | API principal del sistema de control |
| **Simulador HTTP** | 8080 | **18080** | API del simulador de switches |
| **Broker MQTT** | 1883 | **11883** | Broker MQTT para mensajes de sensores |

## Archivos Modificados

### Configuración Principal
- `src/main/resources/application.yml` - Puerto del servidor y broker MQTT
- `docker/docker-compose.yml` - Mapeo de puertos y variables de entorno
- `docker/Dockerfile` - Variable de entorno MQTT_BROKER

### Simulador (Caja Negra)
- `cajaNegra-main/cajaNegra-main/blackBox/docker-compose.yml` - Puertos del broker MQTT y simulador
- `cajaNegra-main/cajaNegra-main/blackBox/config/simulation_config.json` - URLs de switches

### Configuración del Sistema
- `config/site-config.json` - URLs de switches
- `src/main/resources/site-config.json` - URLs de switches

### Scripts
- `iniciar_sistema.sh` - Referencias a puertos en mensajes y health checks

## Notas Importantes

- Los puertos internos de los contenedores Docker **NO** han cambiado (siguen siendo 8080 y 1883)
- Solo se han cambiado los puertos **externos** (host) para evitar conflictos
- Los tests de integración usan testcontainers que mapean puertos dinámicamente, por lo que no requieren cambios

## Uso

Después de estos cambios, los endpoints estarán disponibles en:

- **API REST**: `http://localhost:18081/api`
- **Simulador**: `http://localhost:18080`
- **MQTT Broker**: `tcp://localhost:11883`

## Para Ejecutar Múltiples Instancias

Si necesitas ejecutar múltiples instancias del laboratorio en la misma máquina, puedes:

1. **Opción 1**: Usar variables de entorno para cambiar puertos dinámicamente
2. **Opción 2**: Modificar manualmente los puertos en los archivos de configuración
3. **Opción 3**: Usar diferentes rangos de puertos (ej: 18081, 28081, 38081, etc.)

