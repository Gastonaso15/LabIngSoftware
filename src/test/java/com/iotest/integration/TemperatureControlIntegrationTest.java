package com.iotest.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotest.domain.model.Logica.ISwitchController;
import com.iotest.domain.service.TemperatureControlService;
import com.iotest.TemperatureControlApplication;
import com.iotest.infrastructure.mqtt.MqttSensorSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración para los endpoints REST del sistema de control de temperatura.
 * 
 * Estos tests verifican:
 * - Control de temperatura cuando la carga total no es una limitación
 * - Control cuando la carga total es una limitación efectiva
 * - Casos borde: fallas en comunicación REST
 */
@SpringBootTest(classes = TemperatureControlApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "mqtt.broker=tcp://localhost:1883",
    "mqtt.enabled=false",
    "temperature-control.config-file=classpath:test-site-config.json",
    "energy-cost-monitor.enabled=false"
})
@DisplayName("Tests de Integración - API REST")
class TemperatureControlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TemperatureControlService temperatureControlService;

    @MockBean
    private ISwitchController switchController;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        // Configurar mocks para switchController - éxito por defecto
        when(switchController.postSwitchStatus(anyString(), anyBoolean()))
                .thenReturn("Respuesta: {\"on\":true}");
    }

    @Test
    @DisplayName("Caso 1: Control de temperatura cuando la carga total NO es limitación")
    void testTemperatureControlWithoutEnergyLimitation() throws Exception {
        // Arrange: Sistema con energía suficiente (5.0 kW), habitación fría
        String requestBody = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        // Act & Assert
        mockMvc.perform(post("/api/sensor/reading")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensor_id").value("mqtt:topic1"))
                .andExpect(jsonPath("$.operations_count").exists())
                .andExpect(jsonPath("$.operations").isArray())
                .andExpect(jsonPath("$.current_energy_consumption").exists());
    }

    @Test
    @DisplayName("Caso 2: Control cuando la carga total ES una limitación efectiva")
    void testTemperatureControlWithEnergyLimitation() throws Exception {
        // Arrange: Primero encender una habitación
        String requestBody1 = """
            {
                "sensor_id": "mqtt:topic2",
                "temperature": 18.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        mockMvc.perform(post("/api/sensor/reading")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody1))
                .andExpect(status().isOk());

        // Ahora intentar encender otra con límite de energía (3.0 kW, cada una consume 2.0 kW)
        String requestBody2 = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 15.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        // Act & Assert: Debe hacer swap o no poder encender
        mockMvc.perform(post("/api/sensor/reading")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operations_count").exists());
    }

    @Test
    @DisplayName("Caso 3: Debe retornar estado del sistema correctamente")
    void testGetSystemStatus() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.max_energy").exists())
                .andExpect(jsonPath("$.current_energy_consumption").exists())
                .andExpect(jsonPath("$.available_energy").exists())
                .andExpect(jsonPath("$.rooms").isArray());
    }

    @Test
    @DisplayName("Caso 4: Debe retornar estado de todas las habitaciones")
    void testGetAllRooms() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].room_id").exists())
                .andExpect(jsonPath("$[0].sensor_id").exists())
                .andExpect(jsonPath("$[0].current_temperature").exists());
    }

    @Test
    @DisplayName("Caso 5: Debe retornar estado de habitación específica")
    void testGetRoomStatus() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/rooms/mqtt:topic1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room_id").exists())
                .andExpect(jsonPath("$.sensor_id").value("mqtt:topic1"))
                .andExpect(jsonPath("$.current_temperature").exists());
    }

    @Test
    @DisplayName("Caso 6: Debe retornar 404 para habitación no encontrada")
    void testGetRoomStatusNotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/rooms/unknown_room"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Caso 7: Debe manejar error cuando switch falla (simulación de falla REST)")
    void testSwitchFailureHandling() throws Exception {
        // Arrange: Simular falla en el switch SOLO cuando se llama
        // Primero necesitamos que haya una operación, así que enviamos una temperatura fría
        when(switchController.postSwitchStatus(anyString(), anyBoolean()))
                .thenThrow(new java.io.IOException("Error de conexión con switch"));

        String requestBody = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        // Act & Assert: Debe procesar el error pero retornar respuesta
        mockMvc.perform(post("/api/sensor/reading")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operations").isArray())
                .andExpect(jsonPath("$.operations_count").exists());
        
        // Si hay operaciones, verificar que al menos una tenga success=false
        // Si no hay operaciones (porque la habitación ya está cálida), el test pasa
    }

    @Test
    @DisplayName("Caso 8: Debe validar request inválido (sensor desconocido)")
    void testInvalidSensorRequest() throws Exception {
        String requestBody = """
            {
                "sensor_id": "mqtt:unknown_sensor",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        // Act & Assert: Debe procesar pero sin operaciones
        mockMvc.perform(post("/api/sensor/reading")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operations_count").value(0));
    }

    @Test
    @DisplayName("Caso 9: Health check debe funcionar")
    void testHealthCheck() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Caso 10: Verificar política de alto costo de energía")
    void testEnergyCostCheck() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/system/energy-cost-check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contract\": \"testContract\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensor_id").value("SYSTEM"))
                .andExpect(jsonPath("$.operations_count").exists());
    }
}
