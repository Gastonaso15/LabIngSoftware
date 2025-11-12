package com.iotest.unit.domain;

import com.iotest.domain.model.Controllers.TemperatureController;
import com.iotest.domain.model.POJOS.DataSensor;
import com.iotest.domain.model.POJOS.DataSwitch;
import com.iotest.domain.model.Operation;
import com.iotest.domain.model.POJOS.Room;
import com.iotest.domain.model.EnergyCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemperatureController - Tests Unitarios")
class TemperatureControllerTest {

            private TemperatureController controller;
            private Room room1;
            private Room room2;
            private DataSwitch switch1;
            private DataSwitch switch2;

    @BeforeEach
    void setUp() {
        // 1. Definimos las habitaciones (basado en el site config)
        // Room(sensorId, switchUrl, expectedTemp, energyConsumption)
        room1 = new Room("mqtt:topic1", "http://host:port/switch/1", 22.0, 2.0); // 2.0 kWh
        room2 = new Room("mqtt:topic2", "http://host:port/switch/2", 21.0, 2.0); // 2.0 kWh

        // 2. Definimos el estado inicial de los switches (apagados)
        switch1 = new DataSwitch("http://host:port/switch/1", false);
        switch2 = new DataSwitch("http://host:port/switch/2", false);

        // 3. Inicializamos el controlador con un límite de energía
        // Límite de 3.0 kWh (para forzar la lógica de priorización)
        double maxEnergy = 3.0;

        controller = new TemperatureController(
                maxEnergy,
                List.of(room1, room2),
                List.of(switch1, switch2)
        );
    }
            @Test
            @DisplayName("Debe encender calefactor si la habitación está fría y hay energía disponible")
            void shouldTurnOnHeaterWhenColdAndPowerAvailable() {
                // Arrange: Room 1 está fría (19°C) y su target es 22°C.
                // El consumo (2.0) es menor al max (3.0).
                DataSensor sensorData = new DataSensor("mqtt:topic1", 19.0, LocalDateTime.now());

                //Act
                List<Operation> operations = controller.processSensorData(sensorData);
                //Assert
                assertThat(operations).hasSize(1);
                assertThat(operations.get(0)).isEqualTo(new Operation("http://host:port/switch/1", "ON"));

            }

            @Test
            @DisplayName("Debe apagar todos los switches si el costo de energía es ALTO")
            void shouldTurnOffAllWhenEnergyCostIsHigh(){
                // Arrange: ambos switches encendidos
                switch1.setOn(true);
                switch2.setOn(true);

                // Act: pasar el tiempo como parámetro (NO consultar dentro del test)
                long testTimestamp = System.currentTimeMillis();
                EnergyCost.EnergyZone zone = EnergyCost.energyZone(EnergyCost.TEST_CONTRACT_30S, testTimestamp);
                int currentTariff = zone.current();
                List<Operation> operations = controller.turnSwitchOffWhenHighCost(EnergyCost.TEST_CONTRACT_30S, testTimestamp);

                // Assert según la tarifa actual
                if (currentTariff == EnergyCost.HIGH){
                    assertThat(operations).containsExactlyInAnyOrder(
                            new Operation("http://host:port/switch/1", "OFF"),
                            new Operation("http://host:port/switch/2", "OFF")
                    );
                } else {
                    assertThat(operations).isEmpty();
                }
            }
            @Test
            @DisplayName("Debe apagar calefactor si la habitación alcanza la temperatura deseada")
            void shouldTurnOffHeaterWhenWarm() {
                // Arrange: Simulamos que el switch 1 ya estaba encendido
                switch1.setOn(true);
                // Ahora llega una lectura de que la habitación 1 está cálida (23°C)
                DataSensor sensorData = new DataSensor("mqtt:topic1", 23.0, LocalDateTime.now());

                // Act
                List<Operation> operations = controller.processSensorData(sensorData);

                // Assert
                assertThat(operations).hasSize(1);
                assertThat(operations.get(0)).isEqualTo(new Operation("http://host:port/switch/1", "OFF"));
            }

            @Test
            @DisplayName("Debe priorizar la habitación MÁS fría si el límite de energía está alcanzado")
            void shouldPrioritizeColderRoomByTurningOffLessColdRoom() {
                // Arrange: Límite es 3.0 kWh.
                // Room 2 (target 21°C) está encendida y un poco fría (20.0°C). Consumiendo 2.0 kWh.
                switch2.setOn(true);
                room2.updateTemperature(20.0, LocalDateTime.now()); // Déficit de 1.0°C

                // Ahora, Room 1 (target 22°C) reporta MUCHO frío (15.0°C).
                // Déficit de 7.0°C.
                // Room 1 es prioritaria.
                DataSensor sensorData = new DataSensor("mqtt:topic1", 15.0, LocalDateTime.now());

                // Act: El controlador debe apagar room2 para encender room1.
                List<Operation> operations = controller.processSensorData(sensorData);

                // Assert
                assertThat(operations).hasSize(2);
                assertThat(operations).containsExactlyInAnyOrder(
                        new Operation("http://host:port/switch/2", "OFF"),
                        new Operation("http://host:port/switch/1", "ON")
                );
            }

            @Test
            @DisplayName("Debe retornar lista vacía cuando se recibe un sensorId desconocido")
            void shouldReturnEmptyListWhenSensorIdIsUnknown() {
                // Arrange: Sensor con ID que no corresponde a ninguna habitación
                DataSensor sensorData = new DataSensor("mqtt:unknown_sensor", 19.0, LocalDateTime.now());

                // Act
                List<Operation> operations = controller.processSensorData(sensorData);

                // Assert
                assertThat(operations).isEmpty();
            }

            @Test
            @DisplayName("Debe encender múltiples habitaciones si hay energía suficiente")
            void shouldTurnOnMultipleRoomsWhenEnergyIsAvailable() {
                // Arrange: Ambas habitaciones están frías
                // Límite es 5.0 kWh (suficiente para ambas que consumen 2.0 cada una)
                TemperatureController controllerWithMoreEnergy = new TemperatureController(
                        5.0,
                        List.of(room1, room2),
                        List.of(switch1, switch2)
                );

                // Primero actualizamos room1
                DataSensor sensorData1 = new DataSensor("mqtt:topic1", 19.0, LocalDateTime.now());
                List<Operation> operations1 = controllerWithMoreEnergy.processSensorData(sensorData1);

                // Luego actualizamos room2
                DataSensor sensorData2 = new DataSensor("mqtt:topic2", 18.0, LocalDateTime.now());
                List<Operation> operations2 = controllerWithMoreEnergy.processSensorData(sensorData2);

                // Assert: Primera operación enciende room1
                assertThat(operations1).hasSize(1);
                assertThat(operations1.get(0)).isEqualTo(new Operation("http://host:port/switch/1", "ON"));

                // Assert: Segunda operación enciende room2 (ya que hay energía disponible)
                assertThat(operations2).hasSize(1);
                assertThat(operations2.get(0)).isEqualTo(new Operation("http://host:port/switch/2", "ON"));
            }

            @Test
            @DisplayName("No debe generar operaciones cuando no hay energía y no se puede hacer swap")
            void shouldNotGenerateOperationsWhenNoEnergyAndNoSwapPossible() {
                // Arrange: Límite muy bajo (1.0 kWh), ambas habitaciones necesitan calefacción
                // Pero ninguna puede encenderse porque el consumo es 2.0 cada una
                TemperatureController controllerWithLowEnergy = new TemperatureController(
                        1.0,
                        List.of(room1, room2),
                        List.of(switch1, switch2)
                );

                // Room 1 necesita calefacción
                DataSensor sensorData = new DataSensor("mqtt:topic1", 15.0, LocalDateTime.now());

                // Act
                List<Operation> operations = controllerWithLowEnergy.processSensorData(sensorData);

                // Assert: No hay operaciones porque no hay energía suficiente
                assertThat(operations).isEmpty();
            }
}
