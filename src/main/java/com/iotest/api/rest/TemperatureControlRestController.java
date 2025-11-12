package com.iotest.api.rest;

import com.iotest.domain.model.api.dto.ProcessOperationsResponse;
import com.iotest.domain.model.api.dto.RoomStatusResponse;
import com.iotest.domain.model.api.dto.SensorReadingRequest;
import com.iotest.domain.model.api.dto.SystemStatusResponse;
import com.iotest.domain.model.EnergyCost;
import com.iotest.domain.service.TemperatureControlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller que expone la API pública del componente de control de temperatura.
 * 
 * Endpoints principales:
 * - POST /api/sensor/reading - Recibe lecturas de sensores
 * - GET /api/system/status - Estado general del sistema
 * - GET /api/rooms - Estado de todas las habitaciones
 * - GET /api/rooms/{roomId} - Estado de una habitación específica
 * - POST /api/system/energy-cost-check - Verificar y aplicar política de alto costo
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TemperatureControlRestController {

    private final TemperatureControlService temperatureControlService;

    public TemperatureControlRestController(TemperatureControlService temperatureControlService) {
        this.temperatureControlService = temperatureControlService;
    }

    /**
     * Endpoint para recibir lecturas de temperatura de los sensores.
     * Este es el endpoint principal que procesa los mensajes de los sensores MQTT
     * (que también pueden llegar vía REST para pruebas o integración).
     * 
     * POST /api/sensor/reading
     * 
     * Body:
     * {
     *   "sensor_id": "mqtt:topic1",
     *   "temperature": 19.5,
     *   "time_stamp": "2024-10-27T10:30:00"
     * }
     */
    @PostMapping("/sensor/reading")
    public ResponseEntity<ProcessOperationsResponse> processSensorReading(
            @RequestBody SensorReadingRequest request) {
        try {
            ProcessOperationsResponse response = temperatureControlService.processSensorReading(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtiene el estado general del sistema.
     * 
     * GET /api/system/status
     * 
     * Retorna:
     * {
     *   "max_energy": 5000.0,
     *   "current_energy_consumption": 3000.0,
     *   "available_energy": 2000.0,
     *   "rooms": [...]
     * }
     */
    @GetMapping("/system/status")
    public ResponseEntity<SystemStatusResponse> getSystemStatus() {
        SystemStatusResponse response = temperatureControlService.getSystemStatus();
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene el estado de todas las habitaciones.
     * 
     * GET /api/rooms
     */
    @GetMapping("/rooms")
    public ResponseEntity<List<RoomStatusResponse>> getAllRooms() {
        SystemStatusResponse systemStatus = temperatureControlService.getSystemStatus();
        return ResponseEntity.ok(systemStatus.getRooms());
    }

    /**
     * Obtiene el estado de una habitación específica.
     * 
     * GET /api/rooms/{roomId}
     */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<RoomStatusResponse> getRoomStatus(@PathVariable String roomId) {
        try {
            RoomStatusResponse response = temperatureControlService.getRoomStatus(roomId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Verifica el costo de energía y aplica la política de apagado si es necesario.
     * 
     * POST /api/system/energy-cost-check
     * 
     * Body (opcional):
     * {
     *   "contract": "testContract"
     * }
     * 
     * Si no se envía contract, usa el contrato de prueba por defecto.
     * 
     * IMPORTANTE: El tiempo se obtiene aquí (capa de infraestructura) y se pasa como parámetro
     * al dominio. El controller NO consulta el tiempo directamente.
     */
    @PostMapping("/system/energy-cost-check")
    public ResponseEntity<ProcessOperationsResponse> checkEnergyCost(
            @RequestBody(required = false) EnergyCostCheckRequest request) {
        String contract = request != null && request.getContract() != null 
                ? request.getContract() 
                : EnergyCost.TEST_CONTRACT_30S;
        
        // Obtener el tiempo aquí (capa de infraestructura) y pasarlo como parámetro
        long currentTimestamp = System.currentTimeMillis();
        ProcessOperationsResponse response = temperatureControlService.checkAndApplyHighCostPolicy(contract, currentTimestamp);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     * 
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "Temperature Control System is running"));
    }

    // DTOs internos para requests
    private static class EnergyCostCheckRequest {
        private String contract;

        public String getContract() {
            return contract;
        }

        public void setContract(String contract) {
            this.contract = contract;
        }
    }

    private static class HealthResponse {
        private String status;
        private String message;

        public HealthResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}

