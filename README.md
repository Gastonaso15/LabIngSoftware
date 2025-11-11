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

1. **Levantar la caja negra**  
   ```bash
   cd /home/gaston/IdeaProjects/lab\ ing\ soft\ con\ caja\ negra/cajaNegra-main/blackBox
   docker compose up --build -d
   ```
   Esto inicia:
   - Broker MQTT (`localhost:1883`)
   - Simulador con API HTTP en `http://localhost:8080`

2. **Levantar LabIngSoftware**  
   ```bash
   cd /home/gaston/IdeaProjects/lab\ ing\ soft\ con\ caja\ negra/LabIngSoftware/docker
   docker compose up --build -d
   ```
   El servicio queda disponible en `http://localhost:8081` (interior del contenedor escucha en 8080).  
   Variables relevantes:
   - `MQTT_BROKER=tcp://host.docker.internal:1883` (conectado al broker de la caja negra)
   - `CONFIG_PATH=/app/config/site-config.json` (montado desde `LabIngSoftware/config/site-config.json`)

3. **Verificar logs**
   ```bash
   docker compose logs -f           # dentro de LabIngSoftware/docker
   tail -f ../logs/temperature-control.log
   ```
   Deberías ver mensajes del tipo “Mensaje procesado exitosamente…”.

4. **Detener servicios**
   ```bash
   # LabIngSoftware
   docker compose down

   # Caja negra
   cd ../../cajaNegra-main/blackBox
   docker compose down
   ```

---

## 3. API REST

Base URL (por defecto): `http://localhost:8081/api`

### 3.1 POST `/sensor/reading`
- **Descripción**: Recibe una lectura de sensor (usado tanto por MQTT como para pruebas manuales).
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

### 3.4 GET `/rooms/{roomId}`
- **Descripción**: Estado de una habitación específica (`roomId` acepta `sensor_id` o `id` definido en configuración).
- **Errores**: `404` si no existe.

### 3.5 POST `/system/energy-cost-check`
- **Descripción**: Aplica política de apagado cuando la tarifa es alta.
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

- `docker compose logs -f` (ver conexiones MQTT).
- `tail -f logs/temperature-control.log` (operaciones, errores de switches).
- `curl http://localhost:8081/api/system/status | jq` (estado actual).
- `docker logs -f simulator | grep "/switch"` en la caja negra para confirmar que recibe los comandos HTTP.

---

## 6. Troubleshooting

- **No se conecta a MQTT**: asegurarse de que el broker de la caja negra está en `localhost:1883` antes de levantar LabIngSoftware. Revisar que la variable `MQTT_BROKER` apunte a `tcp://host.docker.internal:1883`.
- **Conflicto de puertos**: la caja negra usa `localhost:8080`; LabIngSoftware expone `localhost:8081`. Cambiar el mapeo en `docker/docker-compose.yml` si se requiere otro puerto.
- **Switches no responden**: revisar que `switchUrl` use `host.docker.internal:8080` (dentro del contenedor) y que la caja negra esté corriendo.
- **Después del restart del broker no vuelve a suscribirse**: el cliente tiene reconexión automática, pero chequear `logs/temperature-control.log` para ver si hubo errores al reactivar.

