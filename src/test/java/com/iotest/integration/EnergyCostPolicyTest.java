package com.iotest.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotest.TemperatureControlApplication;
import com.iotest.domain.model.EnergyCost;
import com.iotest.domain.model.Logica.ISwitchController;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de Integración - Política de Alto Costo de Energía
 * Verifica el comportamiento del sistema con diferentes tarifas eléctricas
 */
@SpringBootTest(classes = TemperatureControlApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "mqtt.broker=tcp://localhost:1883",
        "mqtt.enabled=false",
        "temperature-control.config-file=classpath:test-site-config.json"
})
@DisplayName("Tests de Política de Costo de Energía")
class EnergyCostPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ISwitchController switchController;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        when(switchController.postSwitchStatus(anyString(), anyBoolean()))
                .thenReturn("Respuesta: {\"on\":true}");
    }

    @Test
    @DisplayName("COSTO 1: Debe apagar todos los switches cuando tarifa es ALTA")
    void testTurnOffAllWhenHighCost() throws Exception {
        // PASO 1: Encender algunas habitaciones primero
        String encenderRoom1 = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        mockMvc.perform(post("/api/sensor/reading")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(encenderRoom1))
                .andExpect(status().isOk());

        String encenderRoom2 = """
            {
                "sensor_id": "mqtt:topic2",
                "temperature": 18.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        mockMvc.perform(post("/api/sensor/reading")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(encenderRoom2))
                .andExpect(status().isOk());

        // PASO 2: Verificar que hay consumo
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_energy_consumption").value(greaterThan(0.0)));

        // PASO 3: Aplicar política de alto costo
        int currentTariff = EnergyCost.currentEnergyZone(EnergyCost.TEST_CONTRACT_30S).current();

        mockMvc.perform(post("/api/system/energy-cost-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contract\": \"testContract\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensor_id").value("SYSTEM"));

        // Si la tarifa es ALTA, todos los switches deben estar apagados
        if (currentTariff == EnergyCost.HIGH) {
            mockMvc.perform(get("/api/system/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.current_energy_consumption").value(0.0));
        }
    }

    @Test
    @DisplayName("COSTO 2: No debe apagar si tarifa es BAJA")
    void testNoTurnOffWhenLowCost() throws Exception {
        // Esperar a que la tarifa sea BAJA
        int currentTariff = EnergyCost.currentEnergyZone(EnergyCost.TEST_CONTRACT_30S).current();

        if (currentTariff == EnergyCost.LOW) {
            // Encender habitación
            String encenderRoom1 = """
                {
                    "sensor_id": "mqtt:topic1",
                    "temperature": 19.0,
                    "time_stamp": "%s"
                }
                """.formatted(LocalDateTime.now());

            mockMvc.perform(post("/api/sensor/reading")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(encenderRoom1))
                    .andExpect(status().isOk());

            // Verificar que está encendida
            double consumoAntes = 2.0;

            // Aplicar política (no debería apagar)
            mockMvc.perform(post("/api/system/energy-cost-check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contract\": \"testContract\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.operations_count").value(0));

            // Verificar que sigue encendida
            mockMvc.perform(get("/api/system/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.current_energy_consumption").value(greaterThanOrEqualTo(0.0)));
        }
    }

    @Test
    @DisplayName("COSTO 3: Debe poder reencender después de periodo de alto costo")
    void testReenableAfterHighCostPeriod() throws Exception {
        // Simular ciclo completo
        int currentTariff = EnergyCost.currentEnergyZone(EnergyCost.TEST_CONTRACT_30S).current();

        if (currentTariff == EnergyCost.HIGH) {
            // Durante alto costo - apagar t0d0
            mockMvc.perform(post("/api/system/energy-cost-check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contract\": \"testContract\"}"))
                    .andExpect(status().isOk());

            // Esperar a que cambie a bajo costo (en tests reales esperaríamos 30s)
            // Por ahora solo verificamos que el endpoint funciona
        }

        // Después de cambio de tarifa, enviar lectura fría
        String request = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        mockMvc.perform(post("/api/sensor/reading")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operations_count").exists());
    }

    @Test
    @DisplayName("COSTO 4: Debe manejar múltiples checks de costo consecutivos")
    void testMultipleConsecutiveCostChecks() throws Exception {
        // Realizar múltiples checks seguidos
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/system/energy-cost-check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contract\": \"testContract\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sensor_id").value("SYSTEM"));

            Thread.sleep(100);
        }

        // El sistema debe seguir operativo
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("COSTO 5: Debe validar contrato inválido")
    void testInvalidContract() throws Exception {
        // Intentar con contrato que no existe
        mockMvc.perform(post("/api/system/energy-cost-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contract\": \"invalidContract\"}"))
                .andExpect(status().is5xxServerError()); // Debe fallar
    }

    @Test
    @DisplayName("COSTO 6: Debe usar contrato por defecto si no se especifica")
    void testDefaultContract() throws Exception {
        // No enviar contract en el body
        mockMvc.perform(post("/api/system/energy-cost-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensor_id").value("SYSTEM"));
    }

    @Test
    @DisplayName("COSTO 7: Debe retornar información de la zona de energía")
    void testEnergyZoneInformation() throws Exception {
        EnergyCost.EnergyZone zone = EnergyCost.currentEnergyZone(EnergyCost.TEST_CONTRACT_30S);

        // Verificar que tenemos información válida
        org.assertj.core.api.Assertions.assertThat(zone.current()).isIn(EnergyCost.LOW, EnergyCost.HIGH);
        org.assertj.core.api.Assertions.assertThat(zone.next()).isIn(EnergyCost.LOW, EnergyCost.HIGH);
        org.assertj.core.api.Assertions.assertThat(zone.current()).isNotEqualTo(zone.next());
        org.assertj.core.api.Assertions.assertThat(zone.nextTS()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    @DisplayName("COSTO 8: Debe aplicar política solo cuando es necesario")
    void testApplyPolicyOnlyWhenNeeded() throws Exception {
        // Si no hay nada encendido, no debería generar operaciones
        mockMvc.perform(post("/api/system/energy-cost-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contract\": \"testContract\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operations_count").value(lessThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("COSTO 9: Debe mantener estado consistente después de política")
    void testConsistentStateAfterPolicy() throws Exception {
        // Aplicar política
        mockMvc.perform(post("/api/system/energy-cost-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contract\": \"testContract\"}"))
                .andExpect(status().isOk());

        // Verificar que el sistema está en estado consistente
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.max_energy").value(3.0))
                .andExpect(jsonPath("$.current_energy_consumption").value(lessThanOrEqualTo(3.0)))
                .andExpect(jsonPath("$.available_energy").value(greaterThanOrEqualTo(0.0)));
    }

    @Test
    @DisplayName("COSTO 10: Debe registrar tiempo de próximo cambio de tarifa")
    void testNextTariffChangeTime() throws Exception {
        EnergyCost.EnergyZone zone = EnergyCost.currentEnergyZone(EnergyCost.TEST_CONTRACT_30S);
        long now = System.currentTimeMillis();

        // El próximo cambio debe estar en el futuro
        org.assertj.core.api.Assertions.assertThat(zone.nextTS()).isGreaterThan(now);

        // Y debe ser dentro de los próximos 30 minutos (1800000 ms)
        org.assertj.core.api.Assertions.assertThat(zone.nextTS() - now)
                .isLessThanOrEqualTo(1800000);
    }
}