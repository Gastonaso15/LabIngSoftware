package com.iotest.domain.service;

import com.iotest.domain.model.Controllers.TemperatureController;
import com.iotest.domain.model.EnergyCost;
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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger logger = LoggerFactory.getLogger(TemperatureControlService.class);

    private final TemperatureController temperatureController;
    private final ISwitchController switchController;
    private final List<Room> rooms;
    private final List<DataSwitch> switches;
    private final String energyContract;

    public TemperatureControlService(
            TemperatureController temperatureController,
            ISwitchController switchController,
            List<Room> rooms,
            List<DataSwitch> switches,
            @Value("${temperature-control.energy-contract:testContract}") String energyContract) {
        this.temperatureController = temperatureController;
        this.switchController = switchController;
        this.rooms = rooms;
        this.switches = switches;
        this.energyContract = energyContract;
    }

    /**
     * Sincroniza el estado de los switches al inicio de la aplicación.
     */
    @PostConstruct
    public void initializeSwitchStates() {
        logger.info("Inicializando y sincronizando estados de switches...");
        synchronizeSwitchStates();
        logger.info("Sincronización de switches completada");
    }

    /**
     * Procesa una lectura de sensor y ejecuta las operaciones necesarias.
     * 
     * @param request Datos del sensor recibidos
     * @return Respuesta con las operaciones ejecutadas
     */
    public ProcessOperationsResponse processSensorReading(SensorReadingRequest request) {
        // Sincronizar estado real de switches antes de tomar decisiones
        synchronizeSwitchStates();
        
        // Verificar la tarifa actual - si es HIGH, no permitir encender switches
        long currentTime = System.currentTimeMillis();
        EnergyCost.EnergyZone zone = EnergyCost.energyZone(energyContract, currentTime);
        boolean isHighTariff = zone.current() == EnergyCost.HIGH;
        
        if (isHighTariff) {
            logger.debug("Tarifa actual es HIGH - bloqueando operaciones de encendido de switches");
        }
        
        // Convertir DTO a modelo de dominio
        DataSensor sensorData = new DataSensor(
                request.getSensorId(),
                request.getTemperature(),
                request.getTimeStamp() != null ? request.getTimeStamp() : LocalDateTime.now()
        );

        // Obtener operaciones del controlador
        List<Operation> operations = temperatureController.processSensorData(sensorData);
        
        // Si la tarifa es HIGH, filtrar operaciones de "ON" (solo permitir "OFF")
        if (isHighTariff) {
            List<Operation> filteredOperations = new ArrayList<>();
            for (Operation op : operations) {
                if ("OFF".equals(op.getAction())) {
                    filteredOperations.add(op);
                    logger.debug("Permitiendo operación OFF en tarifa HIGH: {}", op.getSwitchUrl());
                } else {
                    logger.info("Bloqueando operación ON en tarifa HIGH: {} - La tarifa alta no permite encender switches", op.getSwitchUrl());
                }
            }
            operations = filteredOperations;
        }

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
     * IMPORTANTE: Actualiza el estado interno del switch SOLO DESPUÉS de que la operación física se ejecute exitosamente.
     * Esto evita desincronización entre el estado interno y el estado real del switch.
     */
    private List<SwitchOperationResponse> executeOperations(List<Operation> operations) {
        List<SwitchOperationResponse> results = new ArrayList<>();

        for (Operation operation : operations) {
            try {
                boolean desiredState = "ON".equals(operation.getAction());
                String response = switchController.postSwitchStatus(operation.getSwitchUrl(), desiredState);

                // Actualizar el estado interno del switch SOLO DESPUÉS de que la operación física se ejecute exitosamente
                DataSwitch switchToUpdate = switches.stream()
                        .filter(s -> s.getSwitchUrl().equals(operation.getSwitchUrl()))
                        .findFirst()
                        .orElse(null);
                
                if (switchToUpdate != null) {
                    switchToUpdate.setOn(desiredState);
                }

                results.add(SwitchOperationResponse.builder()
                        .switchUrl(operation.getSwitchUrl())
                        .action(operation.getAction())
                        .success(true)
                        .message("Operación ejecutada exitosamente: " + response)
                        .build());
            } catch (IOException | InterruptedException e) {
                // Si la operación falla, NO actualizamos el estado interno
                // El estado interno se mantendrá como estaba, reflejando el estado real del switch
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
     * IMPORTANTE: Recibe el tiempo como parámetro, NO lo consulta internamente.
     * 
     * @param contract Contrato de energía
     * @param timestamp Timestamp actual (en milisegundos desde epoch)
     */
    public ProcessOperationsResponse checkAndApplyHighCostPolicy(String contract, long timestamp) {
        List<Operation> operations = temperatureController.turnSwitchOffWhenHighCost(contract, timestamp);
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

    /**
     * Ejecuta operaciones para eventos de tiempo (cambios de tarifa).
     * Este método es usado por el EnergyCostMonitor para ejecutar operaciones
     * manteniendo la sincronización del estado interno.
     */
    public void executeOperationsForTimeEvent(List<Operation> operations) {
        // Sincronizar estado antes de ejecutar
        synchronizeSwitchStates();
        
        // Ejecutar operaciones (esto actualiza el estado interno)
        executeOperations(operations);
        
        // Sincronizar estado después de ejecutar para asegurar consistencia
        synchronizeSwitchStates();
    }

    /**
     * Sincroniza el estado interno de los switches con su estado real consultándolos.
     * Esto asegura que el sistema siempre tenga el estado correcto antes de tomar decisiones.
     * 
     * Este método es público para que pueda ser llamado desde otros componentes (como EnergyCostMonitor).
     */
    public void synchronizeSwitchStates() {
        for (DataSwitch dataSwitch : switches) {
            try {
                String statusJson = switchController.getSwitchStatus(dataSwitch.getSwitchUrl());
                // El switch devuelve {"id":1,"state":true/false}
                boolean actualState = statusJson.contains("\"state\":true") || statusJson.contains("\"state\": true");
                
                if (dataSwitch.isOn() != actualState) {
                    logger.warn("🔄 Sincronizando switch {}: estado interno era {}, estado real es {}", 
                        dataSwitch.getSwitchUrl(), dataSwitch.isOn(), actualState);
                    dataSwitch.setOn(actualState);
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Error al sincronizar estado del switch {}: {}", 
                    dataSwitch.getSwitchUrl(), e.getMessage());
                // No lanzamos excepción, solo registramos el error para no interrumpir el flujo
            }
        }
    }
}

