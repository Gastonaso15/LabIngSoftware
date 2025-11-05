# Documentación Completa de los DTOs del Sistema

## Introducción

Los DTOs (Data Transfer Objects) son objetos que transportan datos entre capas del sistema sin exponer la lógica de negocio interna. En este sistema, se usan para la comunicación REST entre el cliente y el servidor.

---

## 1. SensorReadingRequest

**Propósito**: Recibir lecturas de temperatura de sensores IoT

**Endpoint que lo usa**: `POST /api/v1/sensors/reading`

### Flujo de Uso

```
┌─────────────┐      SensorReadingRequest      ┌─────────────────┐
│   Sensor    │────────────────────────────►│  SensorController │
│   IoT       │                               │                  │
└─────────────┘                               └─────────────────┘
```

### Campos

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `sensor_id` | String | ✅ Sí | Identificador del sensor (ej: "mqtt:topic1") |
| `temperature` | Double | ✅ Sí | Temperatura en grados Celsius |
| `timestamp` | LocalDateTime | ❌ No | Momento de la lectura (opcional) |

### Ejemplo de Uso

```bash
curl -X POST http://localhost:8080/api/v1/sensors/reading \
  -H "Content-Type: application/json" \
  -d '{
    "sensor_id": "mqtt:topic1",
    "temperature": 19.5,
    "timestamp": "2024-01-15T10:30:00"
  }'
```

### Validaciones

- `sensor_id` debe estar configurado en `site-config.json`
- `temperature` debe ser un valor numérico válido
- Si `timestamp` es null, se usa `LocalDateTime.now()`

---

## 2. RoomStatusResponse

**Propósito**: Devolver el estado completo de una habitación

**Endpoints que lo usan**:
- `GET /api/v1/sensors/room/{roomId}`
- `GET /api/v1/sensors/rooms`
- `GET /api/v1/system/status`

### Flujo de Uso

```
┌─────────────────┐      RoomStatusResponse     ┌─────────────┐
│     Controller  │──────────────────────────►│   Cliente    │
│                 │                               │   (App/Dash)│
└─────────────────┘                               └─────────────┘
```

### Campos

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `room_id` | String | ID de la habitación |
| `sensor_id` | String | ID del sensor asociado |
| `name` | String | Nombre descriptivo |
| `current_temperature` | Double (nullable) | Temperatura actual |
| `desired_temperature` | Double | Temperatura objetivo |
| `temperature_tolerance` | Double | Tolerancia permitida |
| `is_heating_on` | Boolean | Si el calefactor está encendido |
| `last_update` | LocalDateTime (nullable) | Última lectura |
| `needs_heating` | Boolean | Si necesita calefacción |

### Lógica de Negocio

**Cálculo de `needs_heating`**:
```java
needs_heating = (current_temperature != null) && 
                (current_temperature < desired_temperature - temperature_tolerance)
```

**Ejemplo**:
- `desired_temperature` = 22.0°C
- `temperature_tolerance` = 1.0°C
- `current_temperature` = 20.5°C

Resultado: `needs_heating` = **false** (porque 20.5 >= 21.0)

### Ejemplo de Respuesta

```json
{
  "room_id": "mqtt:topic1",
  "sensor_id": "mqtt:topic1",
  "current_temperature": 19.5,
  "desired_temperature": 22.0,
  "temperature_tolerance": 1.0,
  "is_heating_on": true,
  "last_update": "2024-01-15T10:30:00",
  "needs_heating": true
}
```

---

## 3. SwitchOperationResponse

**Propósito**: Reportar una acción individual sobre un switch

**Endpoints que lo usan**:
- `POST /api/v1/sensors/reading` (dentro de ProcessOperationsResponse)

### Flujo de Uso

```
┌──────────────────┐      SwitchOperationResponse     ┌─────────────┐
│ TemperatureControl│───────────────────────────────►│  Cliente     │
│    Service        │                                 │              │
└──────────────────┘                                 └──────────────┘
```

### Campos

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `switch_url` | String | URL del switch |
| `action` | String | "ON" o "OFF" |
| `success` | Boolean | Si la operación fue exitosa |
| `message` | String | Mensaje descriptivo |

### Ejemplo de Respuesta

```json
{
  "switch_url": "http://host:port/switch/1",
  "action": "ON",
  "success": true,
  "message": "Operation queued"
}
```

### Casos de Uso

#### Caso 1: Encender un calefactor
```json
{
  "switch_url": "http://host:port/switch/1",
  "action": "ON",
  "success": true,
  "message": "Switch turned ON successfully"
}
```

#### Caso 2: Apagar un calefactor
```json
{
  "switch_url": "http://host:port/switch/2",
  "action": "OFF",
  "success": true,
  "message": "Switch turned OFF - room at target temperature"
}
```

#### Caso 3: Error
```json
{
  "switch_url": "http://host:port/switch/1",
  "action": "ON",
  "success": false,
  "message": "Error: timeout connecting to switch"
}
```

---

## 4. ProcessOperationsResponse

**Propósito**: Respuesta del procesamiento de una lectura de sensor

**Endpoint que lo usa**: `POST /api/v1/sensors/reading`

### Flujo de Uso

```
┌──────────────────────────────────────────────────────────┐
│  1. Cliente envía SensorReadingRequest                  │
│  2. Sistema procesa lectura                              │
│  3. Sistema ejecuta operaciones necesarias               │
│  4. Sistema devuelve ProcessOperationsResponse          │
└──────────────────────────────────────────────────────────┘
```

### Campos

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `sensor_id` | String | Sensor que originó la respuesta |
| `operations_count` | Integer | Cantidad de operaciones |
| `operations` | List<SwitchOperationResponse> | Detalle de operaciones |
| `current_energy_consumption` | Double | Consumo actual en kWh |

### Escenarios Comunes

#### Escenario 1: No se necesita acción
```json
{
  "sensor_id": "mqtt:topic1",
  "operations_count": 0,
  "operations": [],
  "current_energy_consumption": 2.0
}
```
**Por qué**: Temperatura está en el rango deseado

#### Escenario 2: Enciende un calefactor
```json
{
  "sensor_id": "mqtt:topic1",
  "operations_count": 1,
  "operations": [
    {
      "switch_url": "http://host:port/switch/1",
      "action": "ON",
      "success": true,
      "message": "Operation queued"
    }
  ],
  "current_energy_consumption": 4.0
}
```
**Por qué**: Habitación está fría y hay energía disponible

#### Escenario 3: Swap de switches (priorización)
```json
{
  "sensor_id": "mqtt:topic2",
  "operations_count": 2,
  "operations": [
    {
      "switch_url": "http://host:port/switch/1",
      "action": "OFF",
      "success": true,
      "message": "Operation queued"
    },
    {
      "switch_url": "http://host:port/switch/2",
      "action": "ON",
      "success": true,
      "message": "Operation queued"
    }
  ],
  "current_energy_consumption": 2.0
}
```
**Por qué**: Habitación 2 más fría que la 1, pero no hay energía libre

---

## 5. SystemStatusResponse

**Propósito**: Vista panorámica del sistema completo

**Endpoint que lo usa**: `GET /api/v1/system/status`

### Flujo de Uso

```
┌──────────────────────────────────────────────────────────────┐
│  Dashboard/Cliente consulta estado del sistema              │
│  └─► GET /api/v1/system/status                              │
│      └─► SystemStatusResponse                                │
│          ├─ Info energética                                   │
│          └─ Estado de todas las habitaciones                 │
└──────────────────────────────────────────────────────────────┘
```

### Campos

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `max_energy` | Double | Límite máximo en kWh |
| `current_energy_consumption` | Double | Consumo actual en kWh |
| `available_energy` | Double | Energía disponible en kWh |
| `rooms` | List<RoomStatusResponse> | Estado de habitaciones |

### Cálculos Implícitos

```java
available_energy = max_energy - current_energy_consumption
```

### Ejemplo de Respuesta Completa

```json
{
  "max_energy": 14.0,
  "current_energy_consumption": 4.0,
  "available_energy": 10.0,
  "rooms": [
    {
      "room_id": "mqtt:topic1",
      "sensor_id": "mqtt:topic1",
      "current_temperature": 19.5,
      "desired_temperature": 22.0,
      "temperature_tolerance": 1.0,
      "is_heating_on": true,
      "last_update": "2024-01-15T10:30:00",
      "needs_heating": true
    },
    {
      "room_id": "mqtt:topic2",
      "sensor_id": "mqtt:topic2",
      "current_temperature": 21.5,
      "desired_temperature": 21.0,
      "temperature_tolerance": 1.0,
      "is_heating_on": false,
      "last_update": null,
      "needs_heating": false
    }
  ]
}
```

### Uso en Dashboard

Este DTO es ideal para crear dashboards porque proporciona:
- **Energía total disponible**: Para alerts
- **Consumo actual**: Para gráficas
- **Estado de todas las habitaciones**: Para tablas/mapas

---

## Diagrama de Flujo Completo

```
┌─────────────────────────────────────────────────────────────────┐
│                         FLUJO COMPLETO                          │
└─────────────────────────────────────────────────────────────────┘

1. SENSOR → SensorReadingRequest → API
   └─ sensor_id: "mqtt:topic1"
   └─ temperature: 19.5

2. API → Procesamiento → ProcessOperationsResponse → API → Cliente
   └─ operations_count: 1
   └─ operations: [{ switch_url: "...", action: "ON" }]

3. CLIENTE → GET /system/status → SystemStatusResponse → Dashboard
   └─ Estado de todas las habitaciones
   └─ Información energética global

4. CLIENTE → GET /sensors/room/{id} → RoomStatusResponse → Detalle
   └─ Estado específico de una habitación
```

---

## Anotaciones Importantes

### @JsonProperty
- **Propósito**: Mapear campos Java a JSON con snake_case
- **Ejemplo**: `@JsonProperty("sensor_id")` → campo JSON `sensor_id`

### @Data (Lombok)
- Genera automáticamente:
  - Getters y setters
  - `equals()` y `hashCode()`
  - `toString()`

### @Builder (Lombok)
- Permite construcción fluida:
```java
RoomStatusResponse response = RoomStatusResponse.builder()
    .roomId("office1")
    .temperature(22.0)
    .isHeatingOn(true)
    .build();
```

---

## Validaciones y Consideraciones

### Valores Null
- `RoomStatusResponse.current_temperature`: Puede ser null si no hay lecturas
- `RoomStatusResponse.last_update`: Puede ser null si nunca se leyó
- `ProcessOperationsResponse.operations`: Lista vacía [], nunca null

### Valores Mínimos/Máximos
- `temperature`: -10°C a 50°C (razonable para oficinas)
- `energy`: 0 a max_energy configurado
- `operations_count`: 0 o más (siempre >= 0)

### Orden de Operaciones
En `ProcessOperationsResponse.operations`, el orden puede ser importante:
1. **Primero OFF**: Apagar switches menos prioritarios
2. **Después ON**: Encender switches más prioritarios

Esto asegura que siempre haya energía disponible antes de encender.

---

## Testing de DTOs

### Ejemplo de Test Unitario

```java
@Test
void testRoomStatusResponse() {
    RoomStatusResponse response = RoomStatusResponse.builder()
        .roomId("office1")
        .sensorId("mqtt:topic1")
        .currentTemperature(19.5)
        .desiredTemperature(22.0)
        .temperatureTolerance(1.0)
        .isHeatingOn(true)
        .lastUpdate(LocalDateTime.now())
        .needsHeating(true)
        .build();
    
    assertThat(response.getCurrentTemperature()).isEqualTo(19.5);
    assertThat(response.isHeatingOn()).isTrue();
    assertThat(response.needsHeating()).isTrue();
}
```

---

## Mejores Prácticas

1. **Inmutabilidad**: Los DTOs deberían ser inmutables cuando sea posible
2. **Validación**: Validar campos en los controllers antes de procesar
3. **Documentación**: Cada DTO debe tener JavaDoc explicando su propósito
4. **Serialización**: Usar @JsonProperty para mantener consistencia JSON
5. **Performance**: Evitar grandes colecciones en respuestas frecuentes

---

## Resumen

| DTO | Entrada/Salida | Propósito Principal | Complejidad |
|-----|---------------|---------------------|-------------|
| SensorReadingRequest | Entrada | Recibir lecturas de sensores | Baja |
| RoomStatusResponse | Salida | Estado de habitación | Media |
| SwitchOperationResponse | Salida | Resultado de operación | Baja |
| ProcessOperationsResponse | Salida | Resultado de procesamiento | Alta |
| SystemStatusResponse | Salida | Estado global del sistema | Alta |

**Todos los DTOs trabajan juntos para proporcionar una API REST completa y bien documentada.**

