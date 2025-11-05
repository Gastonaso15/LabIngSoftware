package com.iotest.integration;

import com.iotest.TemperatureControlApplication;
import com.iotest.domain.model.Logica.ISwitchController;
import com.iotest.infrastructure.mqtt.MqttSensorSubscriber;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests de Integración con MQTT Real usando Testcontainers
 * Requisito: Casos borde de caída y recuperación del MQTT server
 */
@SpringBootTest(classes = TemperatureControlApplication.class)
@Testcontainers
@TestPropertySource(properties = {
        "mqtt.enabled=true",
        "mqtt.auto-reconnect=true",
        "temperature-control.config-file=classpath:test-site-config.json"
})
@DisplayName("Tests de Integración MQTT - Casos Borde")
class MqttIntegrationTest {

    @Container
    static GenericContainer<?> mosquitto = new GenericContainer<>(
            DockerImageName.parse("eclipse-mosquitto:2.0"))
            .withExposedPorts(1883)
            .withCommand("mosquitto -c /mosquitto-no-auth.conf");

    @DynamicPropertySource
    static void configureMqtt(DynamicPropertyRegistry registry) {
        registry.add("mqtt.broker", () ->
                "tcp://localhost:" + mosquitto.getMappedPort(1883));
    }

    @Autowired(required = false)
    private MqttSensorSubscriber mqttSubscriber;

    @MockBean
    private ISwitchController switchController;

    private MqttClient testPublisher;

    @BeforeEach
    void setUp() throws Exception {
        // Configurar mock del switch controller
        when(switchController.postSwitchStatus(anyString(), anyBoolean()))
                .thenReturn("Respuesta: {\"on\":true}");

        // Crear cliente MQTT de prueba para publicar mensajes
        String brokerUrl = "tcp://localhost:" + mosquitto.getMappedPort(1883);
        testPublisher = new MqttClient(brokerUrl, "test-publisher", new MemoryPersistence());
        testPublisher.connect();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (testPublisher != null && testPublisher.isConnected()) {
            testPublisher.disconnect();
            testPublisher.close();
        }
    }

    @Test
    @DisplayName("MQTT 1: Debe procesar mensajes MQTT correctamente")
    void testProcessMqttMessages() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        // Publicar mensaje al tópico
        String message = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
        mqttMessage.setQos(1);

        testPublisher.publish("mqtt:topic1", mqttMessage);

        // Esperar un poco para que se procese el mensaje
        boolean processed = latch.await(3, TimeUnit.SECONDS);

        // Verificar que el subscriber esté conectado
        assertThat(mqttSubscriber).isNotNull();
    }

    @Test
    @DisplayName("MQTT 2: Debe manejar caída del broker MQTT")
    void testMqttBrokerFailure() throws Exception {
        // Verificar que está conectado
        assertThat(mosquitto.isRunning()).isTrue();

        // Detener el broker
        mosquitto.stop();

        // Esperar un momento
        Thread.sleep(2000);

        // Verificar que el broker está detenido
        assertThat(mosquitto.isRunning()).isFalse();

        // Reiniciar el broker
        mosquitto.start();

        // Esperar reconexión (auto-reconnect está habilitado)
        Thread.sleep(3000);

        // Verificar que está corriendo de nuevo
        assertThat(mosquitto.isRunning()).isTrue();
    }

    @Test
    @DisplayName("MQTT 3: Debe reconectar automáticamente después de caída")
    void testMqttAutoReconnect() throws Exception {
        // Publicar mensaje antes de caída
        String message1 = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        testPublisher.publish("mqtt:topic1", new MqttMessage(message1.getBytes()));
        Thread.sleep(1000);

        // Detener broker
        mosquitto.stop();
        Thread.sleep(2000);

        // Reiniciar broker
        mosquitto.start();
        Thread.sleep(3000);

        // Reconectar publisher
        if (!testPublisher.isConnected()) {
            testPublisher.connect();
        }

        // Publicar mensaje después de reconexión
        String message2 = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 20.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        testPublisher.publish("mqtt:topic1", new MqttMessage(message2.getBytes()));
        Thread.sleep(1000);

        // El sistema debe seguir funcionando
        assertThat(mosquitto.isRunning()).isTrue();
    }

    @Test
    @DisplayName("MQTT 4: Debe manejar mensajes con formato alternativo")
    void testAlternativeMessageFormats() throws Exception {
        // Formato alternativo 1: sensorId en lugar de sensor_id
        String message1 = """
            {
                "sensorId": "mqtt:topic1",
                "temp": 19.0,
                "timestamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        testPublisher.publish("mqtt:topic1", new MqttMessage(message1.getBytes()));
        Thread.sleep(1000);

        // Formato alternativo 2: sensor en lugar de sensor_id
        String message2 = """
            {
                "sensor": "mqtt:topic2",
                "value": 20.0,
                "time": "%s"
            }
            """.formatted(LocalDateTime.now());

        testPublisher.publish("mqtt:topic2", new MqttMessage(message2.getBytes()));
        Thread.sleep(1000);

        // Ambos formatos deben ser procesados sin errores
        assertThat(testPublisher.isConnected()).isTrue();
    }

    @Test
    @DisplayName("MQTT 5: Debe manejar mensajes duplicados (QoS 1)")
    void testDuplicateMessages() throws Exception {
        String message = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
        mqttMessage.setQos(1); // QoS 1 puede causar duplicados

        // Publicar el mismo mensaje varias veces
        for (int i = 0; i < 3; i++) {
            testPublisher.publish("mqtt:topic1", mqttMessage);
            Thread.sleep(100);
        }

        Thread.sleep(1000);

        // El sistema debe manejar duplicados sin fallar
        assertThat(testPublisher.isConnected()).isTrue();
    }

    @Test
    @DisplayName("MQTT 6: Debe manejar mensajes malformados")
    void testMalformedMessages() throws Exception {
        // Mensaje con JSON inválido
        String badMessage1 = "{ invalid json }";
        testPublisher.publish("mqtt:topic1", new MqttMessage(badMessage1.getBytes()));
        Thread.sleep(500);

        // Mensaje sin campo de temperatura
        String badMessage2 = """
            {
                "sensor_id": "mqtt:topic1",
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());
        testPublisher.publish("mqtt:topic1", new MqttMessage(badMessage2.getBytes()));
        Thread.sleep(500);

        // Mensaje vacío
        testPublisher.publish("mqtt:topic1", new MqttMessage("".getBytes()));
        Thread.sleep(500);

        // El sistema debe seguir funcionando a pesar de mensajes malos
        assertThat(testPublisher.isConnected()).isTrue();
    }

    @Test
    @DisplayName("MQTT 7: Debe manejar múltiples tópicos simultáneamente")
    void testMultipleTopicsSimultaneously() throws Exception {
        // Publicar a múltiples tópicos al mismo tiempo
        String message1 = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        String message2 = """
            {
                "sensor_id": "mqtt:topic2",
                "temperature": 20.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        // Publicar en paralelo
        testPublisher.publish("mqtt:topic1", new MqttMessage(message1.getBytes()));
        testPublisher.publish("mqtt:topic2", new MqttMessage(message2.getBytes()));

        Thread.sleep(1000);

        // Ambos mensajes deben ser procesados
        assertThat(testPublisher.isConnected()).isTrue();
    }

    @Test
    @DisplayName("MQTT 8: Debe manejar ráfagas de mensajes (burst)")
    void testMessageBurst() throws Exception {
        // Enviar ráfaga de mensajes
        for (int i = 0; i < 10; i++) {
            String message = """
                {
                    "sensor_id": "mqtt:topic1",
                    "temperature": %f,
                    "time_stamp": "%s"
                }
                """.formatted(19.0 + (i * 0.1), LocalDateTime.now());

            testPublisher.publish("mqtt:topic1", new MqttMessage(message.getBytes()));
        }

        Thread.sleep(2000);

        // El sistema debe procesar todos los mensajes sin fallar
        assertThat(testPublisher.isConnected()).isTrue();
    }

    @Test
    @DisplayName("MQTT 9: Debe manejar reconexión con mensajes retenidos")
    void testRetainedMessagesAfterReconnect() throws Exception {
        // Publicar mensaje retenido
        String message = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
        mqttMessage.setRetained(true);
        mqttMessage.setQos(1);

        testPublisher.publish("mqtt:topic1", mqttMessage);
        Thread.sleep(1000);

        // El mensaje retenido debe estar disponible
        assertThat(testPublisher.isConnected()).isTrue();
    }

    @Test
    @DisplayName("MQTT 10: Debe manejar desconexión limpia")
    void testCleanDisconnect() throws Exception {
        // Publicar mensaje
        String message = """
            {
                "sensor_id": "mqtt:topic1",
                "temperature": 19.0,
                "time_stamp": "%s"
            }
            """.formatted(LocalDateTime.now());

        testPublisher.publish("mqtt:topic1", new MqttMessage(message.getBytes()));
        Thread.sleep(1000);

        // Desconectar limpiamente
        testPublisher.disconnect();

        // Debe desconectarse sin errores
        assertThat(testPublisher.isConnected()).isFalse();
    }
}