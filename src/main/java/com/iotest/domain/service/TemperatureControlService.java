package com.iotest.domain.service;

import com.iotest.domain.model.Controllers.TemperatureController;
import com.iotest.domain.model.Logica.ISwitchController;
import com.iotest.domain.model.Operation;
import com.iotest.domain.model.POJOS.DataSensor;
import com.iotest.domain.model.POJOS.DataSwitch;
import com.iotest.domain.model.POJOS.Room;
import com.iotest.domain.model.api.dto.ProcessOperationsResponse;
import com.iotest.domain.model.api.dto.RoomStatusResponse;
import com.iotest.domain.model.api.dto.SensorReadingRequest;
import com.iotest.domain.model.api.dto.SwitchOperationResponse;
import com.iotest.domain.model.api.dto.SystemStatusResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio que coordina la lógica de control de temperatura.
 * Actúa como intermediario entre el REST Controller y el TemperatureController,
 * además de ejecutar las operaciones sobre los switches.
 */
@Service
public class TemperatureControlService {

    private final TemperatureController temperatureController;
    private final ISwitchController switchController;
    private final List<Room> rooms;
    private final List<DataSwitch> switches;

    public TemperatureControlService(
            TemperatureController temperatureController,
            ISwitchController switchController,
            List<Room> rooms,
            List<DataSwitch> switches) {
        this.temperatureController = temperatureController;
        this.switchController = switchController;
        this.rooms = rooms;
        this.switches = switches;
    }

    /**
     * Procesa una lectura de sensor y ejecuta las operaciones necesarias.
     * 
     * @param request Datos del sensor recibidos
     * @return Respuesta con las operaciones ejecutadas
     */
    public ProcessOperationsResponse processSensorReading(SensorReadingRequest request) {
        // Convertir DTO a modelo de dominio
        DataSensor sensorData = new DataSensor(
                request.getSensorId(),
                request.getTemperature(),
                request.getTimeStamp() != null ? request.getTimeStamp() : LocalDateTime.now()
        );

        // Obtener operaciones del controlador
        List<Operation> operations = temperatureController.processSensorData(sensorData);

        // Ejecutar operaciones sobre los switches
        List<SwitchOperationResponse> executedOperations = executeOperations(operations);

        // Calcular consumo actual
        double currentConsumption = calculateCurrentConsumption();

        return ProcessOperationsResponse.builder()
                .sensorId(request.getSensorId())
                .operationscount(executedOperations.size())
                .operations(executedOperations)
                .currentEnergyConsumption(currentConsumption)
                .build();
    }

    /**
     * Ejecuta las operaciones sobre los switches físicos.
     */
    private List<SwitchOperationResponse> executeOperations(List<Operation> operations) {
        List<SwitchOperationResponse> results = new ArrayList<>();

        for (Operation operation : operations) {
            try {
                boolean desiredState = "ON".equals(operation.getAction());
                String response = switchController.postSwitchStatus(operation.getSwitchUrl(), desiredState);

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

    /**
     * Obtiene el estado actual del sistema.
     */
    public SystemStatusResponse getSystemStatus() {
        double currentConsumption = calculateCurrentConsumption();
        double maxEnergy = temperatureController.getMaxEnergy();
        double availableEnergy = maxEnergy - currentConsumption;

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

    /**
     * Obtiene el estado de una habitación específica.
     */
    public RoomStatusResponse getRoomStatus(String roomId) {
        Room room = rooms.stream()
                .filter(r -> r.getSensorId().equals(roomId) || 
                           (r.getId() != null && r.getId().equals(roomId)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Habitación no encontrada: " + roomId));

        return mapRoomToStatus(room);
    }

    /**
     * Verifica y aplica la política de apagado por alto costo de energía.
     */
    public ProcessOperationsResponse checkAndApplyHighCostPolicy(String contract) {
        List<Operation> operations = temperatureController.turnSwitchOffWhenHighCost(contract);
        List<SwitchOperationResponse> executedOperations = executeOperations(operations);

        double currentConsumption = calculateCurrentConsumption();

        return ProcessOperationsResponse.builder()
                .sensorId("SYSTEM")
                .operationscount(executedOperations.size())
                .operations(executedOperations)
                .currentEnergyConsumption(currentConsumption)
                .build();
    }

    /**
     * Mapea un Room a RoomStatusResponse.
     */
    private RoomStatusResponse mapRoomToStatus(Room room) {
        DataSwitch roomSwitch = switches.stream()
                .filter(s -> s.getSwitchUrl().equals(room.getSwitchUrl()))
                .findFirst()
                .orElse(null);

        return RoomStatusResponse.builder()
                .roomId(room.getId() != null ? room.getId() : room.getSensorId())
                .sensorId(room.getSensorId())
                .name(room.getName())
                .currentTemperature(room.getCurrentTemperature() != null ? room.getCurrentTemperature() : 0.0)
                .desiredTemperature(room.getDesiredTemperature())
                .temperatureTolerance(room.getTemperatureTolerance() != null ? room.getTemperatureTolerance() : 1.0)
                .isHeatingOn(roomSwitch != null && roomSwitch.isOn())
                .lastUpdate(room.getLastUpdate())
                .needsHeating(room.needsHeating())
                .build();
    }

    /**
     * Calcula el consumo actual de energía.
     */
    private double calculateCurrentConsumption() {
        return rooms.stream()
                .filter(room -> {
                    DataSwitch sw = switches.stream()
                            .filter(s -> s.getSwitchUrl().equals(room.getSwitchUrl()))
                            .findFirst()
                            .orElse(null);
                    return sw != null && sw.isOn();
                })
                .mapToDouble(Room::getEnergyConsumption)
                .sum();
    }
}

