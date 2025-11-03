# API Pública del Componente de Control de Temperatura

## Resumen

Se ha diseñado una API REST completa que expone el componente de control de temperatura. La API permite:

- Recibir lecturas de sensores de temperatura
- Consultar el estado del sistema y las habitaciones
- Aplicar políticas de control energético (alto costo)
- Health check del sistema

## Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                    REST Controller                           │
│         TemperatureControlRestController                     │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    Service Layer                            │
│         TemperatureControlService                           │
└────────┬──────────────────────────┬─────────────────────────┘
         │                          │
         ▼                          ▼
┌──────────────────────┐  ┌──────────────────────┐
│  TemperatureController│  │  SwitchController   │
│  (Lógica de negocio)  │  │  (Comunicación REST)│
└──────────────────────┘  └──────────────────────┘
```

## Endpoints

### 1. POST /api/sensor/reading

**Descripción**: Recibe lecturas de temperatura de los sensores y procesa las operaciones necesarias.

**Request Body**:
```json
{
  "sensor_id": "mqtt:topic1",
  "temperature": 19.5,
  "time_stamp": "2024-10-27T10:30:00"
}
```

**Response** (200 OK):
```json
{
  "sensor_id": "mqtt:topic1",
  "operations_count": 1,
  "operations": [
    {
      "switch_url": "http://host:port/switch/1",
      "action": "ON",
      "success": true,
      "message": "Operación ejecutada exitosamente"
    }
  ],
  "current_energy_consumption": 2.0
}
```

**Errores**:
- `400 Bad Request`: Datos inválidos
- `500 Internal Server Error`: Error al procesar

---

### 2. GET /api/system/status

**Descripción**: Obtiene el estado general del sistema.

**Response** (200 OK):
```json
{
  "max_energy": 5.0,
  "current_energy_consumption": 3.0,
  "available_energy": 2.0,
  "rooms": [
    {
      "room_id": "living_room",
      "sensor_id": "mqtt:topic1",
      "name": "Living Room",
      "current_temperature": 19.5,
      "desired_temperature": 22.0,
      "temperature_tolerance": 0.5,
      "is_heating_on": true,
      "last_update": "2024-10-27T10:30:00",
      "needs_heating": true
    }
  ]
}
```

---

### 3. GET /api/rooms

**Descripción**: Obtiene el estado de todas las habitaciones.

**Response** (200 OK):
```json
[
  {
    "room_id": "living_room",
    "sensor_id": "mqtt:topic1",
    "name": "Living Room",
    "current_temperature": 19.5,
    "desired_temperature": 22.0,
    "temperature_tolerance": 0.5,
    "is_heating_on": true,
    "last_update": "2024-10-27T10:30:00",
    "needs_heating": true
  }
]
```

---

### 4. GET /api/rooms/{roomId}

**Descripción**: Obtiene el estado de una habitación específica.

**Path Parameters**:
- `roomId`: ID de la habitación (puede ser sensor_id o room_id)

**Response** (200 OK):
```json
{
  "room_id": "living_room",
  "sensor_id": "mqtt:topic1",
  "name": "Living Room",
  "current_temperature": 19.5,
  "desired_temperature": 22.0,
  "temperature_tolerance": 0.5,
  "is_heating_on": true,
  "last_update": "2024-10-27T10:30:00",
  "needs_heating": true
}
```

**Errores**:
- `404 Not Found`: Habitación no encontrada

---

### 5. POST /api/system/energy-cost-check

**Descripción**: Verifica el costo de energía actual y aplica la política de apagado si es necesario (cuando la tarifa es ALTA).

**Request Body** (opcional):
```json
{
  "contract": "testContract"
}
```

Si no se envía `contract`, se usa `testContract` por defecto.

**Response** (200 OK):
```json
{
  "sensor_id": "SYSTEM",
  "operations_count": 2,
  "operations": [
    {
      "switch_url": "http://host:port/switch/1",
      "action": "OFF",
      "success": true,
      "message": "Operación ejecutada exitosamente"
    },
    {
      "switch_url": "http://host:port/switch/2",
      "action": "OFF",
      "success": true,
      "message": "Operación ejecutada exitosamente"
    }
  ],
  "current_energy_consumption": 0.0
}
```

---

### 6. GET /api/health

**Descripción**: Health check del sistema.

**Response** (200 OK):
```json
{
  "status": "UP",
  "message": "Temperature Control System is running"
}
```

---

## Componentes Creados

### 1. TemperatureControlRestController
- **Ubicación**: `com.iotest.api.rest.TemperatureControlRestController`
- **Responsabilidad**: Expone los endpoints REST públicos
- **Anotaciones**: `@RestController`, `@RequestMapping("/api")`

### 2. TemperatureControlService
- **Ubicación**: `com.iotest.domain.service.TemperatureControlService`
- **Responsabilidad**: 
  - Coordina entre el REST Controller y el TemperatureController
  - Ejecuta operaciones sobre los switches
  - Mapea entidades de dominio a DTOs

### 3. TemperatureControlConfig
- **Ubicación**: `com.iotest.infrastructure.config.TemperatureControlConfig`
- **Responsabilidad**:
  - Carga la configuración desde `site-config.json`
  - Crea los beans necesarios (Rooms, Switches, Controllers)

### 4. TemperatureControlApplication
- **Ubicación**: `com.iotest.TemperatureControlApplication`
- **Responsabilidad**: Clase principal de Spring Boot

## Configuración

La API se configura mediante el archivo `site-config.json` ubicado en `src/main/resources/`:

```json
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
      "sensorTopic": "home/sensors/living_room/temperature",
      "switchUrl": "http://switch-living:8080/api/power"
    }
  ]
}
```

## Flujo de Datos

1. **Recepción de Sensor**:
   - Cliente → `POST /api/sensor/reading`
   - REST Controller → TemperatureControlService
   - Service → TemperatureController (lógica)
   - Service → SwitchController (ejecución)
   - Service → REST Controller (respuesta)

2. **Consulta de Estado**:
   - Cliente → `GET /api/system/status`
   - REST Controller → TemperatureControlService
   - Service → Mapea Rooms y Switches a DTOs
   - Service → REST Controller (respuesta)

## Integración con MQTT

Aunque la API REST está lista, el sistema está diseñado para recibir mensajes MQTT en el futuro. Los mensajes MQTT pueden ser convertidos a `SensorReadingRequest` y enviados al endpoint `POST /api/sensor/reading`.

## Próximos Pasos (Entrega 5)

- Implementar cliente MQTT que consuma mensajes y los envíe a la API
- Tests de integración para los endpoints REST
- Manejo de errores más robusto
- Logging y monitoreo

