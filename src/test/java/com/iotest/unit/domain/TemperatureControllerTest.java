package com.iotest.unit.domain;

import com.iotest.domain.model.Controllers.TemperatureController;
import com.iotest.domain.model.DataSensor;
import com.iotest.domain.model.DataSwitch;
import com.iotest.domain.model.Operation;
import com.iotest.domain.model.Room;
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
        room1 = new Room(
                "R1", // 1. id (ID interno de la habitación)
                "office1", // 2. name (Nombre de la habitación)
                22.0, // 3. desiredTemperature
                2.0,  // 4. powerConsumption (double)
                "http://host:port/switch/1", // 5. switchUrl
                "mqtt:topic1" // 6. sensorId
        );

        room2 = new Room(
                "R2", // 1. id
                "office2", // 2. name
                21.0, // 3. desiredTemperature
                2.0,  // 4. powerConsumption (double)
                "http://host:port/switch/2", // 5. switchUrl
                "mqtt:topic2" // 6. sensorId
        );
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
    @DisplayName("No debe hacer nada si el sensor no está configurado")
    void shouldIgnoreUnknownSensor() {
        DataSensor unknown = new DataSensor("mqtt:unknown", 15.0, LocalDateTime.now());
        List<Operation> ops = controller.processSensorData(unknown);
        assertThat(ops).isEmpty();
    }

    @Test
    @DisplayName("Debe encender ambas habitaciones si hay energía suficiente")
    void shouldTurnOnBothRoomsWhenEnergyAvailable() {
        // Configurar controller con maxEnergy = 5.0 (suficiente para ambas)
        Room r1 = new Room("R1", "office1", 22.0, 2.0,
                "http://host:port/switch/1", "mqtt:topic1");
        Room r2 = new Room("R2", "office2", 21.0, 2.0,
                "http://host:port/switch/2", "mqtt:topic2");

        DataSwitch sw1 = new DataSwitch("http://host:port/switch/1", false);
        DataSwitch sw2 = new DataSwitch("http://host:port/switch/2", false);

        TemperatureController ctrl = new TemperatureController(5.0, List.of(r1, r2), List.of(sw1, sw2));

        // Ambas habitaciones frías
        ctrl.processSensorData(new DataSensor("mqtt:topic1", 18.0, LocalDateTime.now()));
        List<Operation> ops = ctrl.processSensorData(new DataSensor("mqtt:topic2", 17.0, LocalDateTime.now()));

        // Debe encender ambas (total: 4.0 kWh < 5.0 max)
        assertThat(ops).hasSize(2);
    }

    @Test
    @DisplayName("No debe generar operaciones cuando todas las habitaciones están OK")
    void shouldDoNothingWhenAllRoomsAtTarget() {
        room1.updateTemperature(22.5, LocalDateTime.now());
        room2.updateTemperature(21.5, LocalDateTime.now());

        DataSensor sensor = new DataSensor("mqtt:topic1", 22.5, LocalDateTime.now());
        List<Operation> ops = controller.processSensorData(sensor);

        assertThat(ops).isEmpty();
    }

    @Test
    @DisplayName("Debe respetar el límite de energía exacto")
    void shouldRespectExactEnergyLimit() {
        // maxEnergy = 3.0, room1 consume 2.0, room2 consume 2.0
        // Solo puede encender 1 habitación

        DataSensor s1 = new DataSensor("mqtt:topic1", 18.0, LocalDateTime.now());
        List<Operation> ops1 = controller.processSensorData(s1);

        assertThat(ops1).hasSize(1);
        assertThat(ops1.get(0).getAction()).isEqualTo("ON");

        // Ahora room2 también está fría, pero NO hay energía
        DataSensor s2 = new DataSensor("mqtt:topic2", 17.0, LocalDateTime.now());
        List<Operation> ops2 = controller.processSensorData(s2);

        // Solo debería hacer swap si room2 es MÁS prioritaria
        // (en este caso lo es: 17°C vs 18°C)
    }
}
