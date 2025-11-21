# Temperature Control System (LabIngSoftware)

Este servicio procesa lecturas de sensores MQTT, evalúa reglas de control térmico y opera los switches HTTP expuestos por la “caja negra” del simulador.

---

## 1. Prerrequisitos

- Docker y Docker Compose instalados.
- Repositorios clonados:
  - `cajaNegra-main/blackBox` (simulador + broker MQTT).
  - `LabIngSoftware` (este servicio).

---

## 2. Puesta en marcha

### 2.1. Método Automático (Recomendado)

El proyecto incluye scripts para facilitar el inicio y detención del sistema:

#### Iniciar el sistema completo

```bash
cd LabIngSoftware
./iniciar_sistema.sh
```

Este script:
1. ✅ Verifica que Docker esté corriendo
2. 🐳 Inicia el simulador (caja negra) con el broker MQTT
3. 🛑 Detiene contenedores existentes de labingsoftware (si están corriendo)
4. 🔨 Compila el proyecto (Maven)
5. 🐳 Inicia labingsoftware con Docker Compose
6. ⏳ Espera a que el sistema esté listo y muestra el estado

**Endpoints disponibles después del inicio:**
- API REST: `http://localhost:8081/api`
- Health Check: `http://localhost:8081/api/health`
- Estado del Sistema: `http://localhost:8081/api/system/status`
- Simulador: `http://localhost:8080`
- Broker MQTT: `tcp://localhost:1883`

#### Detener el sistema completo

```bash
./detener_sistema.sh
```

Este script detiene todos los contenedores Docker relacionados:
- Contenedores de labingsoftware
- Contenedores del simulador (caja negra)
- Broker MQTT

#### Limpiar directorio target (si hay problemas de permisos)

Si encuentras problemas de permisos al compilar:

```bash
./limpiar_target.sh
```

O manualmente:
```bash
sudo rm -rf target/
```

---

### 2.2. Método Manual (Paso a Paso)

Si prefieres ejecutar los comandos manualmente:

#### Paso 1: Iniciar la caja negra (simulador + broker MQTT)

```bash
cd cajaNegra-main/cajaNegra-main/blackBox
docker compose up --build -d
```

Esto inicia:
- Broker MQTT en `localhost:1883` (puerto externo)
- Simulador con API HTTP en `http://localhost:8080`

**Verificar que está corriendo:**
```bash
docker compose ps
```

**Ver logs:**
```bash
docker compose logs -f
```

#### Paso 2: Compilar LabIngSoftware

```bash
cd LabIngSoftware
mvn clean package -DskipTests
```

Si hay problemas de permisos con el directorio `target/`:
```bash
sudo rm -rf target/
mvn clean package -DskipTests
```

#### Paso 3: Iniciar LabIngSoftware

```bash
cd docker
docker compose up --build -d
```

El servicio queda disponible en `http://localhost:8081`.

**Variables relevantes:**
- `MQTT_BROKER=tcp://host.docker.internal:1883` (conectado al broker de la caja negra)
- `CONFIG_PATH=/app/config/site-config.json` (montado desde `LabIngSoftware/config/site-config.json`)

#### Paso 4: Verificar que todo está funcionando

**Ver logs de LabIngSoftware:**
```bash
# Desde el directorio docker/
docker compose logs -f

# O desde el directorio raíz
tail -f logs/temperature-control.log
```

Deberías ver mensajes del tipo "Mensaje procesado exitosamente…" y "Monitor de energía iniciado".

**Verificar estado del sistema:**
```bash
curl http://localhost:8081/api/health | jq
curl http://localhost:8081/api/system/status | jq
```

**Verificar contenedores:**
```bash
docker ps
```

Deberías ver:
- `broker-mqtt` (del simulador)
- `simulator` (del simulador)
- `labingsoftware` (de LabIngSoftware)

#### Paso 5: Detener servicios

**Detener LabIngSoftware:**
```bash
cd LabIngSoftware/docker
docker compose down
```

**Detener la caja negra:**
```bash
cd cajaNegra-main/cajaNegra-main/blackBox
docker compose down
```

O usar el script:
```bash
cd LabIngSoftware
./detener_sistema.sh
```

---

## 3. API REST

Base URL (por defecto): `http://localhost:8081/api`

> **Nota**: Puertos del sistema:
> - API REST: `8081`
> - Simulador: `8080`
> - Broker MQTT: `1883`

### 3.1 POST `/sensor/reading`
- **Descripción**: Recibe una lectura de sensor (usado tanto por MQTT como para pruebas manuales).
- **curl**:
  ```bash
  curl -X POST http://localhost:8081/api/sensor/reading \
       -H "Content-Type: application/json" \
       -d '{"sensor_id":"sim/ht/1","temperature":18.5,"time_stamp":"2025-11-11T03:40:00"}'
  ```
- **Request**:
  ```json
  {
    "sensor_id": "sim/ht/1",
    "temperature": 18.5,
    "time_stamp": "2025-11-11T03:40:00"
  }
  ```
- **Response**:
  ```json
  {
    "sensor_id": "sim/ht/1",
    "operations_count": 1,
    "operations": [
      {
        "switch_url": "http://localhost:8080/switch/1",
        "action": "ON",
        "success": true,
        "message": "Operación ejecutada exitosamente..."
      }
    ],
    "current_energy_consumption": 1.5
  }
  ```

### 3.2 GET `/system/status`
- **Descripción**: Estado global (energía máxima, consumo actual, habitaciones).
- **curl**:
  ```bash
  curl http://localhost:8081/api/system/status | jq
  ```
- **Response**:
  ```json
  {
    "max_energy": 14.0,
    "current_energy_consumption": 3.0,
    "available_energy": 11.0,
    "rooms": [
      {
        "room_id": "1",
        "sensor_id": "sim/ht/1",
        "name": "office1",
        "current_temperature": 19.1,
        "desired_temperature": 22.0,
        "temperature_tolerance": 0.5,
        "is_heating_on": true,
        "last_update": "2025-11-11T03:40:00",
        "needs_heating": true
      }
    ]
  }
  ```

### 3.3 GET `/rooms`
- **Descripción**: Lista el estado de todas las habitaciones.
- **curl**:
  ```bash
  curl http://localhost:8081/api/rooms | jq
  ```

### 3.4 GET `/rooms/{roomId}`
- **Descripción**: Estado de una habitación específica (`roomId` acepta `sensor_id` o `id` definido en configuración).
- **curl**:
  ```bash
  curl http://localhost:8081/api/rooms/1 | jq
  ```
- **Errores**: `404` si no existe.

### 3.5 POST `/system/energy-cost-check`
- **Descripción**: Aplica política de apagado cuando la tarifa es alta.
- **curl**:
  ```bash
  curl -X POST http://localhost:8081/api/system/energy-cost-check \
       -H "Content-Type: application/json" \
       -d '{"contract":"testContract"}'
  ```
- **Request opcional**:
  ```json
  { "contract": "testContract" }
  ```
- **Response**:
  ```json
  {
    "sensor_id": "SYSTEM",
    "operations_count": 2,
    "operations": [
      { "switch_url": ".../switch/1", "action": "OFF", "success": true, "message": "..." },
      { "switch_url": ".../switch/2", "action": "OFF", "success": true, "message": "..." }
    ],
    "current_energy_consumption": 0.0
  }
  ```

### 3.6 GET `/health`
- **Descripción**: Health check simple.
- **curl**:
  ```bash
  curl http://localhost:8081/api/health | jq
  ```
- **Response**:
  ```json
  {
    "status": "UP",
    "message": "Temperature Control System is running"
  }
  ```

---

## 4. Configuración (`site-config.json`)

- Ubicado en `LabIngSoftware/config/site-config.json` (montado dentro del contenedor).
- Ejemplo alineado con la caja negra:
  ```json
  {
    "siteName": "oficinaA",
    "maxPowerWatts": 14000,
    "rooms": [
      {
        "id": "1",
        "name": "office1",
        "desiredTemperature": 22.0,
        "temperatureTolerance": 0.5,
        "powerConsumptionWatts": 1800,
        "sensorTopic": "sim/ht/1",
        "switchUrl": "http://host.docker.internal:8080/switch/1"
      },
      ...
    ]
  }
  ```
- Cambiar este archivo y reiniciar el contenedor si se agregan habitaciones o se modifican topologías.

---

## 5. Monitoreo rápido

### Scripts de monitoreo

**Monitor simplificado (recomendado):**
```bash
# Con delay por defecto (5 segundos - muestra cada actualización)
./ver_monitor_simplificado.sh

# Si quieres más lento, puedes especificar un delay mayor
./ver_monitor_simplificado.sh 10
```

Este script muestra solo el estado formateado del monitor (habitaciones y energía) con la tarifa actual, filtrando los logs DEBUG repetitivos.

**Ver logs en tiempo real:**
```bash
# Logs de LabIngSoftware
docker compose -f docker/docker-compose.yml logs -f labingsoftware

# Logs del simulador
cd cajaNegra-main/cajaNegra-main/blackBox
docker compose logs -f simulator

# Logs del broker MQTT
docker compose logs -f broker-mqtt
```

**Ver logs del archivo:**
```bash
tail -f logs/temperature-control.log
```

### Comandos útiles

**Estado del sistema:**
```bash
curl http://localhost:8081/api/system/status | jq
```

**Health check:**
```bash
curl http://localhost:8081/api/health | jq
```

**Verificar switches del simulador:**
```bash
# Ver logs del simulador que muestran las peticiones HTTP a los switches
cd cajaNegra-main/cajaNegra-main/blackBox
docker compose logs -f simulator | grep "/switch"
```

**Ver contenedores en ejecución:**
```bash
docker ps
```

**Ver uso de recursos:**
```bash
docker stats
```

---

## 6. Troubleshooting

### Problemas comunes

**No se conecta a MQTT:**
- Asegurarse de que el broker de la caja negra está corriendo antes de levantar LabIngSoftware
- Verificar que la variable `MQTT_BROKER` apunte a `tcp://host.docker.internal:1883` (dentro del contenedor)
- Verificar que el puerto del broker sea `1883` (puerto externo)
- Revisar logs: `docker compose -f docker/docker-compose.yml logs labingsoftware | grep -i mqtt`

**Conflicto de puertos:**
- La caja negra usa `localhost:8080` (simulador) y `localhost:1883` (MQTT)
- LabIngSoftware expone `localhost:8081` (API REST)
- Si necesitas cambiar puertos, edita:
  - `docker/docker-compose.yml` para LabIngSoftware
  - `cajaNegra-main/cajaNegra-main/blackBox/docker-compose.yml` para el simulador
  - `src/main/resources/application.yml` para la configuración de Spring

**Switches no responden:**
- Revisar que `switchUrl` en `config/site-config.json` use `http://host.docker.internal:8080/switch/X`
- Verificar que la caja negra esté corriendo: `docker ps | grep simulator`
- Ver logs del simulador: `cd cajaNegra-main/cajaNegra-main/blackBox && docker compose logs simulator`

**Problemas de permisos al compilar:**
- Si `mvn clean` falla con "Permiso denegado", ejecutar:
  ```bash
  ./limpiar_target.sh
  # o manualmente:
  sudo rm -rf target/
  ```

**Después del restart del broker no vuelve a suscribirse:**
- El cliente tiene reconexión automática habilitada
- Verificar logs: `tail -f logs/temperature-control.log | grep -i reconnect`
- El sistema debería reconectarse automáticamente en 5 segundos

**El sistema no apaga switches cuando la tarifa es HIGH:**
- Verificar que el monitor de energía esté habilitado en `application.yml`:
  ```yaml
  energy-cost-monitor:
    enabled: true
    check-interval-seconds: 5
  ```
- Verificar logs: `docker compose -f docker/docker-compose.yml logs labingsoftware | grep -i "tarifa\|energy"`
- El contrato `testContract` cambia cada 30 segundos entre HIGH y LOW

**Contenedores no se detienen:**
- Usar el script: `./detener_sistema.sh`
- O manualmente:
  ```bash
  docker compose -f docker/docker-compose.yml down
  cd cajaNegra-main/cajaNegra-main/blackBox
  docker compose down
  ```

### Verificar configuración

**Verificar puertos en uso:**
```bash
netstat -tuln | grep -E "8081|8080|1883"
# o
ss -tuln | grep -E "8081|8080|1883"
```

**Verificar variables de entorno del contenedor:**
```bash
docker compose -f docker/docker-compose.yml exec labingsoftware env | grep -E "MQTT|CONFIG"
```

**Verificar configuración del sitio:**
```bash
cat config/site-config.json | jq
```

