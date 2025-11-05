package com.iotest.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotest.TemperatureControlApplication;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de Estrés y Performance
 * Verifican el comportamiento del sistema bajo carga y condiciones extremas
 */
@SpringBootTest(classes = TemperatureControlApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "mqtt.broker=tcp://localhost:1883",
        "mqtt.enabled=false",
        "temperature-control.config-file=classpath:test-site-config.json"
})
@DisplayName("Tests de Estrés y Performance")
class StressAndPerformanceTest {

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
    @DisplayName("STRESS 1: Debe manejar 100 requests concurrentes")
    void testHandleConcurrentRequests() throws Exception {
        int numberOfRequests = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfRequests);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Enviar 100 requests concurrentes
        for (int i = 0; i < numberOfRequests; i++) {
            final int index = i;
            Future<Boolean> future = executorService.submit(() -> {
                try {
                    String sensorId = index % 2 == 0 ? "mqtt:topic1" : "mqtt:topic2";
                    String request = """
                        {
                            "sensor_id": "%s",
                            "temperature": %f,
                            "time_stamp": "%s"
                        }
                        """.formatted(sensorId, 18.0 + (index % 5), LocalDateTime.now());

                    mockMvc.perform(post("/api/sensor/reading")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                            .andExpect(status().isOk());

                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Esperar a que todos terminen
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Verificar que todos fueron exitosos
        long successCount = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(success -> success)
                .count();

        assertThat(successCount).isEqualTo(numberOfRequests);
        executorService.shutdown();
    }

    @Test
    @DisplayName("STRESS 2: Debe manejar requests con latencia variable")
    void testHandleRequestsWithVariableLatency() throws Exception {
        // Simular switches con diferentes tiempos de respuesta
        when(switchController.postSwitchStatus(anyString(), anyBoolean()))
                .thenAnswer(invocation -> {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
                    return "Respuesta: {\"on\":true}";
                });

        for (int i = 0; i < 20; i++) {
            String request = """
                {
                    "sensor_id": "mqtt:topic1",
                    "temperature": %f,
                    "time_stamp": "%s"
                }
                """.formatted(18.0 + (i * 0.1), LocalDateTime.now());

            mockMvc.perform(post("/api/sensor/reading")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk());
        }

        // El sistema debe seguir respondiendo
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("STRESS 3: Debe manejar cambios rápidos de temperatura")
    void testHandleRapidTemperatureChanges() throws Exception {
        // Simular temperatura oscilando rápidamente
        double[] temperatures = {19.0, 23.0, 18.0, 24.0, 17.0, 25.0, 19.0, 22.0};

        for (double temp : temperatures) {
            String request = """
                {
                    "sensor_id": "mqtt:topic1",
                    "temperature": %f,
                    "time_stamp": "%s"
                }
                """.formatted(temp, LocalDateTime.now());

            mockMvc.perform(post("/api/sensor/reading")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk());

            Thread.sleep(50); // Pausa mínima entre cambios
        }

        // Verificar estado final consistente
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_energy_consumption").exists());
    }

    @Test
    @DisplayName("STRESS 4: Debe mantener performance bajo carga sostenida")
    void testSustainedLoad() throws Exception {
        int durationSeconds = 10;
        int requestsPerSecond = 10;
        long startTime = System.currentTimeMillis();
        int totalRequests = 0;
        int successfulRequests = 0;

        while (System.currentTimeMillis() - startTime < durationSeconds * 1000) {
            for (int i = 0; i < requestsPerSecond; i++) {
                try {
                    String request = """
                        {
                            "sensor_id": "mqtt:topic1",
                            "temperature": %f,
                            "time_stamp": "%s"
                        }
                        """.formatted(19.0 + Math.random(), LocalDateTime.now());

                    mockMvc.perform(post("/api/sensor/reading")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                            .andExpect(status().isOk());

                    successfulRequests++;
                } catch (Exception e) {
                    // Contar fallas
                }
                totalRequests++;
            }
            Thread.sleep(1000);
        }

        // Al menos 95% de éxito
        double successRate = (double) successfulRequests / totalRequests;
        assertThat(successRate).isGreaterThanOrEqualTo(0.95);
    }

    @Test
    @DisplayName("STRESS 5: Debe recuperarse de fallos masivos de switches")
    void testRecoveryFromMassiveSwitchFailures() throws Exception {
        // Simular que todos los switches fallan
        when(switchController.postSwitchStatus(anyString(), anyBoolean()))
                .thenThrow(new java.io.IOException("Network error"));

        // Enviar requests (todos fallarán)
        for (int i = 0; i < 5; i++) {
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
                    .andExpect(status().isOk()); // Debe responder OK aunque falle el switch
        }

        // Restaurar switches
        when(switchController.postSwitchStatus(anyString(), anyBoolean()))
                .thenReturn("Respuesta: {\"on\":true}");

        // Verificar recuperación
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
                .andExpect(jsonPath("$.operations[0].success").value(true));
    }

    @Test
    @DisplayName("STRESS 6: Debe manejar memoria eficientemente con muchas lecturas")
    void testMemoryEfficiencyWithManyReadings() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Enviar muchas lecturas
        for (int i = 0; i < 1000; i++) {
            String request = """
                {
                    "sensor_id": "mqtt:topic1",
                    "temperature": %f,
                    "time_stamp": "%s"
                }
                """.formatted(19.0 + (i % 10) * 0.1, LocalDateTime.now());

            mockMvc.perform(post("/api/sensor/reading")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk());

            if (i % 100 == 0) {
                System.gc();
                Thread.sleep(10);
            }
        }

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = memoryAfter - memoryBefore;

        // El aumento de memoria no debería ser excesivo (menos de 50MB)
        assertThat(memoryIncrease).isLessThan(50 * 1024 * 1024);
    }

    @Test
    @DisplayName("STRESS 7: Debe responder rápidamente a queries de estado bajo carga")
    void testStatusQueryPerformanceUnderLoad() throws Exception {
        // Iniciar carga de escritura en background
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<?> loadFuture = executorService.submit(() -> {
            for (int i = 0; i < 100; i++) {
                try {
                    String request = """
                        {
                            "sensor_id": "mqtt:topic1",
                            "temperature": 19.0,
                            "time_stamp": "%s"
                        }
                        """.formatted(LocalDateTime.now());

                    mockMvc.perform(post("/api/sensor/reading")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request));
                    Thread.sleep(50);
                } catch (Exception e) {
                    // Ignore
                }
            }
        });

        // Mientras hay carga, hacer queries de estado
        long totalTime = 0;
        int queryCount = 20;

        for (int i = 0; i < queryCount; i++) {
            long startTime = System.currentTimeMillis();

            mockMvc.perform(get("/api/system/status"))
                    .andExpect(status().isOk());

            long queryTime = System.currentTimeMillis() - startTime;
            totalTime += queryTime;

            Thread.sleep(100);
        }

        double averageQueryTime = (double) totalTime / queryCount;

        // Tiempo promedio de query debe ser menor a 200ms
        assertThat(averageQueryTime).isLessThan(200.0);

        loadFuture.get(10, TimeUnit.SECONDS);
        executorService.shutdown();
    }

    @Test
    @DisplayName("STRESS 8: Debe manejar secuencia completa de escenarios extremos")
    void testCompleteStressScenario() throws Exception {
        // Escenario 1: Carga normal
        for (int i = 0; i < 10; i++) {
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
                    .andExpect(status().isOk());
        }

        // Escenario 2: Falla masiva
        when(switchController.postSwitchStatus(anyString(), anyBoolean()))
                .thenThrow(new java.io.IOException("Network error"));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/sensor/reading")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                        {
                            "sensor_id": "mqtt:topic1",
                            "temperature": 19.0,
                            "time_stamp": "%s"
                        }
                        """.formatted(LocalDateTime.now())))
                    .andExpect(status().isOk());
        }

        // Escenario 3: Recuperación
        when(switchController.postSwitchStatus(anyString(), anyBoolean()))
                .thenReturn("Respuesta: {\"on\":true}");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/sensor/reading")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                        {
                            "sensor_id": "mqtt:topic1",
                            "temperature": 19.0,
                            "time_stamp": "%s"
                        }
                        """.formatted(LocalDateTime.now())))
                    .andExpect(status().isOk());
        }

        // Verificar que el sistema está operativo
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}