# Implementación de DTOs en el Sistema de Control de Temperatura

## Resumen

Este documento explica cómo se implementaron y utilizaron los DTOs (Data Transfer Objects) en el commit `a6d19a8`, que introdujo la API REST y la integración MQTT completa del sistema de control de temperatura.

---

## 📋 DTOs Implementados

El sistema utiliza 5 DTOs principales para la comunicación entre capas:

1. **SensorReadingRequest** - Entrada de datos de sensores
2. **ProcessOperationsResponse** - Respuesta de procesamiento de lecturas
3. **RoomStatusResponse** - Estado de una habitación
4. **SwitchOperationResponse** - Resultado de una operación sobre un switch
5. **SystemStatusResponse** - Estado general del sistema

---

## 🔄 Flujo de Datos con DTOs

### 1. Flujo Principal: Lectura de Sensor → Procesamiento → Respuesta

```
┌─────────────────────────────────────────────────────────────────┐
│                    FLUJO COMPLETO CON DTOs                      │
└─────────────────────────────────────────────────────────────────┘

[A] ENTRADA MQTT o REST
   │
   ├─► MqttSensorSubscriber.messageArrived()
   │   └─► Extrae datos del JSON MQTT
   │   └─► Crea: SensorReadingRequest
   │       {
   │         "sensor_id": "mqtt:topic1",
   │         "temperature": 19.5,
   │         "time_stamp": "2024-11-03T10:30:00"
   │       }
   │
   └─► TemperatureControlRestController.processSensorReading()
       └─► Recibe: @RequestBody SensorReadingRequest
       └─► Llama: temperatureControlService.processSensorReading(request)

[B] PROCESAMIENTO EN SERVICIO
   │
   └─► TemperatureControlService.processSensorReading()
       │
       ├─► [CONVERSIÓN DTO → DOMINIO]
       │   SensorReadingRequest → DataSensor
       │   ┌─────────────────────────────────────┐
       │   │ DataSensor sensorData = new DataSensor(
       │   │   request.getSensorId(),
       │   │   request.getTemperature(),
       │   │   request.getTimeStamp() != null ? 
       │   │     request.getTimeStamp() : LocalDateTime.now()
       │   │ );
       │   └─────────────────────────────────────┘
       │
       ├─► [LÓGICA DE NEGOCIO]
       │   temperatureController.processSensorData(sensorData)
       │   └─► Retorna: List<Operation>
       │
       ├─► [EJECUCIÓN DE OPERACIONES]
       │   executeOperations(operations)
       │   └─► Operation → SwitchOperationResponse
       │       ┌─────────────────────────────────────┐
       │       │ SwitchOperationResponse.builder()
       │       │   .switchUrl(operation.getSwitchUrl())
       │       │   .action(operation.getAction())
       │       │   .success(true/false)
       │       │   .message("...")
       │       │   .build()
       │       └─────────────────────────────────────┘
       │
       └─► [CONSTRUCCIÓN DE RESPUESTA]
           ProcessOperationsResponse.builder()
           └─► Retorna: ProcessOperationsResponse

[C] RESPUESTA AL CLIENTE
   │
   └─► TemperatureControlRestController
       └─► ResponseEntity.ok(ProcessOperationsResponse)
       └─► JSON Serializado:
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
             "current_energy_consumption": 4.0
           }
```

---

## 🔧 Implementación Detallada

### A. TemperatureControlRestController (Capa de API)

**Responsabilidad**: Recibir requests HTTP y devolver respuestas usando DTOs.

#### Endpoint 1: POST /api/sensor/reading

```java
@PostMapping("/sensor/reading")
public ResponseEntity<ProcessOperationsResponse> processSensorReading(
        @RequestBody SensorReadingRequest request) {
    try {
        // El DTO entra directamente como parámetro
        ProcessOperationsResponse response = 
            temperatureControlService.processSensorReading(request);
        return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().build();
    }
}
```

**Flujo**:
1. Spring deserializa automáticamente el JSON a `SensorReadingRequest`
2. Se pasa el DTO al servicio (sin conversión en el controller)
3. El servicio retorna `ProcessOperationsResponse`
4. Spring serializa automáticamente a JSON

#### Endpoint 2: GET /api/system/status

```java
@GetMapping("/system/status")
public ResponseEntity<SystemStatusResponse> getSystemStatus() {
    SystemStatusResponse response = temperatureControlService.getSystemStatus();
    return ResponseEntity.ok(response);
}
```

**Flujo**:
1. El servicio construye `SystemStatusResponse` con todos los datos
2. Incluye lista de `RoomStatusResponse` dentro
3. Se serializa automáticamente a JSON

#### Endpoint 3: GET /api/rooms/{roomId}

```java
@GetMapping("/rooms/{roomId}")
public ResponseEntity<RoomStatusResponse> getRoomStatus(@PathVariable String roomId) {
    try {
        RoomStatusResponse response = temperatureControlService.getRoomStatus(roomId);
        return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
        return ResponseEntity.notFound().build();
    }
}
```

---

### B. TemperatureControlService (Capa de Servicio)

**Responsabilidad**: Convertir entre DTOs y objetos de dominio, ejecutar lógica de negocio.

#### Método Principal: processSensorReading()

```java
public ProcessOperationsResponse processSensorReading(SensorReadingRequest request) {
    // ═══════════════════════════════════════════════════════════
    // PASO 1: CONVERSIÓN DTO → DOMINIO
    // ═══════════════════════════════════════════════════════════
    DataSensor sensorData = new DataSensor(
        request.getSensorId(),
        request.getTemperature(),
        request.getTimeStamp() != null ? request.getTimeStamp() : LocalDateTime.now()
    );

    // ═══════════════════════════════════════════════════════════
    // PASO 2: LÓGICA DE NEGOCIO (usa objetos de dominio)
    // ═══════════════════════════════════════════════════════════
    List<Operation> operations = temperatureController.processSensorData(sensorData);

    // ═══════════════════════════════════════════════════════════
    // PASO 3: EJECUCIÓN Y CONVERSIÓN DOMINIO → DTO
    // ═══════════════════════════════════════════════════════════
    List<SwitchOperationResponse> executedOperations = executeOperations(operations);

    // ═══════════════════════════════════════════════════════════
    // PASO 4: CONSTRUCCIÓN DE RESPUESTA DTO
    // ═══════════════════════════════════════════════════════════
    double currentConsumption = calculateCurrentConsumption();

    return ProcessOperationsResponse.builder()
        .sensorId(request.getSensorId())
        .operationscount(executedOperations.size())
        .operations(executedOperations)
        .currentEnergyConsumption(currentConsumption)
        .build();
}
```

**Puntos Clave**:
- ✅ El servicio es el único lugar donde se hace conversión DTO ↔ Dominio
- ✅ La lógica de negocio siempre trabaja con objetos de dominio (`DataSensor`, `Operation`, `Room`)
- ✅ Los DTOs solo se usan en los bordes (entrada/salida de la API)

#### Método: executeOperations()

```java
private List<SwitchOperationResponse> executeOperations(List<Operation> operations) {
    List<SwitchOperationResponse> results = new ArrayList<>();

    for (Operation operation : operations) {
        try {
            boolean desiredState = "ON".equals(operation.getAction());
            String response = switchController.postSwitchStatus(
                operation.getSwitchUrl(), 
                desiredState
            );

            // Conversión Operation → SwitchOperationResponse
            results.add(SwitchOperationResponse.builder()
                .switchUrl(operation.getSwitchUrl())
                .action(operation.getAction())
                .success(true)
                .message("Operación ejecutada exitosamente: " + response)
                .build());
        } catch (IOException | InterruptedException e) {
            results.add(SwitchOperationResponse.builder()
                .switchUrl(operation.getSwitchUrl())
                .action(operation.getAction())
                .success(false)
                .message("Error al ejecutar operación: " + e.getMessage())
                .build());
        }
    }

    return results;
}
```

#### Método: getSystemStatus()

```java
public SystemStatusResponse getSystemStatus() {
    double currentConsumption = calculateCurrentConsumption();
    double maxEnergy = temperatureController.getMaxEnergy();
    double availableEnergy = maxEnergy - currentConsumption;

    // Conversión List<Room> → List<RoomStatusResponse>
    List<RoomStatusResponse> roomStatuses = rooms.stream()
        .map(this::mapRoomToStatus)
        .collect(Collectors.toList());

    return SystemStatusResponse.builder()
        .maxEnergy(maxEnergy)
        .currentEnergyConsumption(currentConsumption)
        .availableEnergy(availableEnergy)
        .rooms(roomStatuses)
        .build();
}
```

#### Método: mapRoomToStatus() - Mapeo Room → RoomStatusResponse

```java
private RoomStatusResponse mapRoomToStatus(Room room) {
    // Buscar el switch asociado a la habitación
    DataSwitch roomSwitch = switches.stream()
        .filter(s -> s.getSwitchUrl().equals(room.getSwitchUrl()))
        .findFirst()
        .orElse(null);

    // Construir DTO desde objeto de dominio
    return RoomStatusResponse.builder()
        .roomId(room.getId() != null ? room.getId() : room.getSensorId())
        .sensorId(room.getSensorId())
        .name(room.getName())
        .currentTemperature(room.getCurrentTemperature() != null ? 
            room.getCurrentTemperature() : 0.0)
        .desiredTemperature(room.getDesiredTemperature())
        .temperatureTolerance(room.getTemperatureTolerance() != null ? 
            room.getTemperatureTolerance() : 1.0)
        .isHeatingOn(roomSwitch != null && roomSwitch.isOn())
        .lastUpdate(room.getLastUpdate())
        .needsHeating(room.needsHeating())  // Lógica de negocio del dominio
        .build();
}
```

**Puntos Clave**:
- ✅ Combina datos de múltiples objetos de dominio (`Room` + `DataSwitch`)
- ✅ Calcula campos derivados (`needsHeating`)
- ✅ Maneja valores null de forma segura

---

### C. MqttSensorSubscriber (Integración MQTT)

**Responsabilidad**: Recibir mensajes MQTT y convertirlos a DTOs.

#### Método: messageArrived()

```java
@Override
public void messageArrived(String topic, MqttMessage message) throws Exception {
    try {
        // Parsear JSON del mensaje MQTT
        String payload = new String(message.getPayload());
        JsonNode jsonNode = objectMapper.readTree(payload);

        // Extraer datos del JSON
        String sensorId = extractSensorId(topic, jsonNode);
        double temperature = extractTemperature(jsonNode);
        LocalDateTime timestamp = extractTimestamp(jsonNode);

        // ═══════════════════════════════════════════════════════════
        // CREAR DTO DESDE MENSAJE MQTT
        // ═══════════════════════════════════════════════════════════
        SensorReadingRequest request = new SensorReadingRequest(
            sensorId,
            temperature,
            timestamp
        );

        // Procesar usando el mismo servicio que el REST endpoint
        temperatureControlService.processSensorReading(request);
        
        logger.info("Mensaje procesado exitosamente - Sensor: {}, Temperatura: {}", 
            sensorId, temperature);

    } catch (Exception e) {
        logger.error("Error al procesar mensaje MQTT del tópico {}: {}", 
            topic, e.getMessage(), e);
    }
}
```

**Puntos Clave**:
- ✅ MQTT y REST usan el mismo DTO (`SensorReadingRequest`)
- ✅ Ambos terminan llamando al mismo método del servicio
- ✅ La lógica de negocio es independiente del origen de datos

---

## 🎯 Patrones y Principios Aplicados

### 1. Separación de Responsabilidades

```
┌─────────────────────────────────────────────────────────────┐
│  CAPA DE API (REST Controller)                            │
│  - Recibe DTOs                                             │
│  - Valida entrada                                          │
│  - Maneja errores HTTP                                     │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  CAPA DE SERVICIO (Service)                                │
│  - Convierte DTO ↔ Dominio                                 │
│  - Coordina lógica de negocio                              │
│  - Ejecuta operaciones externas                           │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  CAPA DE DOMINIO (TemperatureController)                  │
│  - Lógica de negocio pura                                  │
│  - Trabaja solo con objetos de dominio                     │
│  - No conoce DTOs                                          │
└─────────────────────────────────────────────────────────────┘
```

### 2. Builder Pattern

Todos los DTOs de respuesta usan `@Builder` de Lombok:

```java
// En lugar de:
ProcessOperationsResponse response = new ProcessOperationsResponse();
response.setSensorId("mqtt:topic1");
response.setOperationscount(1);
// ...

// Se usa:
ProcessOperationsResponse response = ProcessOperationsResponse.builder()
    .sensorId("mqtt:topic1")
    .operationscount(1)
    .operations(executedOperations)
    .currentEnergyConsumption(currentConsumption)
    .build();
```

**Ventajas**:
- ✅ Código más legible
- ✅ Inmutabilidad (si se usa correctamente)
- ✅ Flexibilidad para campos opcionales

### 3. @JsonProperty para Serialización

```java
@JsonProperty("sensor_id")
private String sensorId;

@JsonProperty("operations_count")
private int operationscount;
```

**Razón**: Mantener consistencia con convenciones JSON (snake_case) mientras se usa camelCase en Java.

---

## 📊 Mapeo DTO ↔ Dominio

### Entrada: SensorReadingRequest → DataSensor

| DTO (SensorReadingRequest) | Dominio (DataSensor) |
|---------------------------|---------------------|
| `sensorId` (String) | `sensorId` (String) |
| `temperature` (double) | `temperature` (double) |
| `timeStamp` (LocalDateTime) | `timestamp` (LocalDateTime) |

**Conversión**:
```java
DataSensor sensorData = new DataSensor(
    request.getSensorId(),
    request.getTemperature(),
    request.getTimeStamp() != null ? request.getTimeStamp() : LocalDateTime.now()
);
```

### Salida: Operation → SwitchOperationResponse

| Dominio (Operation) | DTO (SwitchOperationResponse) |
|---------------------|------------------------------|
| `switchUrl` (String) | `switchUrl` (String) |
| `action` (String) | `action` (String) |
| - | `success` (boolean) ← Resultado de ejecución |
| - | `message` (String) ← Mensaje descriptivo |

**Conversión**:
```java
SwitchOperationResponse.builder()
    .switchUrl(operation.getSwitchUrl())
    .action(operation.getAction())
    .success(true/false)  // ← Se determina durante la ejecución
    .message("...")       // ← Se construye durante la ejecución
    .build();
```

### Salida: Room → RoomStatusResponse

| Dominio (Room) | DTO (RoomStatusResponse) |
|---------------|------------------------|
| `id` / `sensorId` | `roomId` |
| `sensorId` | `sensorId` |
| `name` | `name` |
| `currentTemperature` | `currentTemperature` |
| `desiredTemperature` | `desiredTemperature` |
| `temperatureTolerance` | `temperatureTolerance` |
| - | `isHeatingOn` ← De `DataSwitch` |
| `lastUpdate` | `lastUpdate` |
| `needsHeating()` | `needsHeating` ← Método calculado |

**Conversión**:
```java
// Combina Room + DataSwitch
RoomStatusResponse.builder()
    .roomId(room.getId() != null ? room.getId() : room.getSensorId())
    .sensorId(room.getSensorId())
    .name(room.getName())
    .currentTemperature(room.getCurrentTemperature() != null ? 
        room.getCurrentTemperature() : 0.0)
    .desiredTemperature(room.getDesiredTemperature())
    .temperatureTolerance(room.getTemperatureTolerance() != null ? 
        room.getTemperatureTolerance() : 1.0)
    .isHeatingOn(roomSwitch != null && roomSwitch.isOn())  // ← De DataSwitch
    .lastUpdate(room.getLastUpdate())
    .needsHeating(room.needsHeating())  // ← Método del dominio
    .build();
```

---

## 🔍 Ejemplos de Uso Real

### Ejemplo 1: Lectura de Sensor vía REST

**Request**:
```bash
POST /api/sensor/reading
Content-Type: application/json

{
  "sensor_id": "mqtt:topic1",
  "temperature": 19.5,
  "time_stamp": "2024-11-03T10:30:00"
}
```

**Flujo**:
1. Spring deserializa → `SensorReadingRequest`
2. `TemperatureControlService.processSensorReading(request)`
3. Convierte → `DataSensor`
4. Procesa → `List<Operation>`
5. Ejecuta → `List<SwitchOperationResponse>`
6. Construye → `ProcessOperationsResponse`

**Response**:
```json
{
  "sensor_id": "mqtt:topic1",
  "operations_count": 1,
  "operations": [
    {
      "switch_url": "http://host:port/switch/1",
      "action": "ON",
      "success": true,
      "message": "Operación ejecutada exitosamente: OK"
    }
  ],
  "current_energy_consumption": 4.0
}
```

### Ejemplo 2: Lectura de Sensor vía MQTT

**Mensaje MQTT** (tópico: `mqtt:topic1`):
```json
{
  "temperature": 19.5,
  "timestamp": "2024-11-03T10:30:00"
}
```

**Flujo**:
1. `MqttSensorSubscriber.messageArrived()`
2. Extrae datos del JSON
3. Crea → `SensorReadingRequest`
4. **Mismo flujo que REST** → `TemperatureControlService.processSensorReading()`

### Ejemplo 3: Consulta de Estado del Sistema

**Request**:
```bash
GET /api/system/status
```

**Flujo**:
1. `TemperatureControlService.getSystemStatus()`
2. Calcula consumo actual
3. Mapea todas las habitaciones → `List<RoomStatusResponse>`
4. Construye → `SystemStatusResponse`

**Response**:
```json
{
  "max_energy": 14.0,
  "current_energy_consumption": 4.0,
  "available_energy": 10.0,
  "rooms": [
    {
      "room_id": "mqtt:topic1",
      "sensor_id": "mqtt:topic1",
      "name": "Living Room",
      "current_temperature": 19.5,
      "desired_temperature": 22.0,
      "temperature_tolerance": 1.0,
      "is_heating_on": true,
      "last_update": "2024-11-03T10:30:00",
      "needs_heating": true
    },
    {
      "room_id": "mqtt:topic2",
      "sensor_id": "mqtt:topic2",
      "name": "Bedroom",
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

---

## ✅ Ventajas de esta Implementación

1. **Separación Clara de Capas**
   - Los controllers no conocen objetos de dominio
   - La lógica de negocio no conoce DTOs
   - El servicio actúa como adaptador

2. **Reutilización**
   - MQTT y REST usan el mismo DTO y servicio
   - Un solo punto de entrada para procesar lecturas

3. **Mantenibilidad**
   - Cambios en la API no afectan la lógica de negocio
   - Cambios en el dominio no afectan la API

4. **Testabilidad**
   - Se pueden testear DTOs independientemente
   - Se puede mockear el servicio en tests de controllers

5. **Documentación Implícita**
   - Los DTOs documentan el contrato de la API
   - Los nombres de campos son autoexplicativos

---

## 🚨 Puntos de Atención

1. **Conversión de Nulls**
   - `timeStamp` puede ser null → se usa `LocalDateTime.now()`
   - `currentTemperature` puede ser null → se usa `0.0` en el DTO

2. **Nombres de Campos**
   - Java usa camelCase (`sensorId`)
   - JSON usa snake_case (`sensor_id`)
   - Se resuelve con `@JsonProperty`

3. **Inmutabilidad**
   - Los DTOs usan `@Builder` pero no son inmutables por defecto
   - Considerar hacerlos `final` si es necesario

4. **Validación**
   - No hay validación explícita en los DTOs (usar `@Valid` si se necesita)
   - Las validaciones se hacen en el servicio

---

## 📝 Resumen Ejecutivo

En el commit `a6d19a8`, se implementó una arquitectura limpia usando DTOs para:

1. **Separar** la capa de API de la lógica de negocio
2. **Unificar** el procesamiento de datos desde MQTT y REST
3. **Estandarizar** las respuestas de la API
4. **Facilitar** el mantenimiento y testing

Los DTOs actúan como contratos entre capas, permitiendo que el sistema evolucione sin afectar otras partes del código.

