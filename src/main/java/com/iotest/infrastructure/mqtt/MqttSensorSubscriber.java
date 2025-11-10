package com.iotest.infrastructure.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotest.domain.model.api.dto.SensorReadingRequest;
import com.iotest.domain.service.TemperatureControlService;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente MQTT que se suscribe a los tópicos de sensores y procesa los mensajes.
 * Los mensajes recibidos se convierten y envían al TemperatureControlService.
 */
public class MqttSensorSubscriber implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(MqttSensorSubscriber.class);

    private final TemperatureControlService temperatureControlService;
    private final List<String> topicsToSubscribe;
    private final String brokerUrl;
    private final String clientId;
    private final boolean autoReconnect;
    private MqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MqttSensorSubscriber(
            TemperatureControlService temperatureControlService,
            List<String> topicsToSubscribe,
            String brokerUrl,
            String clientId,
            boolean autoReconnect) {
        this.temperatureControlService = temperatureControlService;
        this.topicsToSubscribe = topicsToSubscribe != null ? topicsToSubscribe : new ArrayList<>();
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.autoReconnect = autoReconnect;
    }

    @PostConstruct
    public void init() {
        try {
            String uniqueClientId = clientId + "-" + System.currentTimeMillis();
            mqttClient = new MqttClient(brokerUrl, uniqueClientId, new MemoryPersistence());
            
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(autoReconnect);
            options.setCleanSession(true);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);

            mqttClient.setCallback(this);
            mqttClient.connect(options);

            subscribeToTopics();
            
            logger.info("Cliente MQTT conectado al broker: {}", brokerUrl);
        } catch (MqttException e) {
            logger.warn("No se pudo conectar con el broker MQTT (puede ser normal en tests): {}", e.getMessage());
            // No lanzar excepción para permitir que la aplicación continúe sin MQTT
        } catch (Exception e) {
            logger.error("Error inesperado al inicializar cliente MQTT: {}", e.getMessage(), e);
            // No lanzar excepción para permitir que la aplicación continúe sin MQTT
        }
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                logger.info("Cliente MQTT desconectado");
            } catch (MqttException e) {
                logger.error("Error al desconectar cliente MQTT: {}", e.getMessage(), e);
            }
        }
    }

    private void subscribeToTopics() {
        if (topicsToSubscribe.isEmpty()) {
            logger.warn("No hay tópicos configurados para suscribirse");
            return;
        }

        try {
            for (String topic : topicsToSubscribe) {
                mqttClient.subscribe(topic, 1); // QoS 1
                logger.info("Suscrito al tópico: {}", topic);
            }
        } catch (MqttException e) {
            logger.error("Error al suscribirse a los tópicos: {}", e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.warn("Conexión MQTT perdida: {}", cause.getMessage());
        
        if (autoReconnect) {
            logger.info("Intentando reconectar...");
            // La reconexión automática se maneja por el cliente MQTT
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        try {
            logger.debug("Mensaje recibido del tópico: {} - {}", topic, new String(message.getPayload()));

            // Parsear el mensaje JSON
            String payload = new String(message.getPayload());
            JsonNode jsonNode = objectMapper.readTree(payload);

            // Extraer información del mensaje
            // El formato puede variar, pero intentamos extraer:
            // - sensor_id (del tópico o del JSON)
            // - temperature
            // - timestamp (o usar el actual si no está)

            String sensorId = extractSensorId(topic, jsonNode);
            double temperature = extractTemperature(jsonNode);
            LocalDateTime timestamp = extractTimestamp(jsonNode);

            // Crear el request
            SensorReadingRequest request = new SensorReadingRequest(
                    sensorId,
                    temperature,
                    timestamp
            );

            // Procesar el mensaje a través del servicio
            temperatureControlService.processSensorReading(request);
            
            logger.info("Mensaje procesado exitosamente - Sensor: {}, Temperatura: {}", sensorId, temperature);

        } catch (Exception e) {
            logger.error("Error al procesar mensaje MQTT del tópico {}: {}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // No se usa en este caso ya que solo subscribimos, no publicamos
    }

    /**
     * Extrae el sensor_id del tópico o del JSON.
     * Formato esperado del tópico: "mqtt:topic1" o "home/sensors/living_room/temperature"
     * 
     * Prioridad:
     * 1. Campo en el JSON (sensor_id, sensorId, sensor)
     * 2. Buscar en los tópicos configurados para mapear tópico -> sensor_id
     * 3. Usar el tópico como sensor_id
     */
    private String extractSensorId(String topic, JsonNode jsonNode) {
        // Primero intentar desde el JSON
        if (jsonNode.has("sensor_id")) {
            return jsonNode.get("sensor_id").asText();
        }
        if (jsonNode.has("sensorId")) {
            return jsonNode.get("sensorId").asText();
        }
        if (jsonNode.has("sensor")) {
            return jsonNode.get("sensor").asText();
        }
        
        // Si no está en el JSON, buscar en los tópicos configurados
        // Los tópicos configurados pueden venir como "mqtt:" o como paths
        // Si el tópico está en la lista, buscar el sensor_id correspondiente
        for (String configuredTopic : topicsToSubscribe) {
            if (topic.equals(configuredTopic) || topic.endsWith(configuredTopic)) {
                // Si el tópico configurado empieza con "mqtt:", usarlo directamente
                // Si es un path, necesitamos mapearlo de vuelta
                if (configuredTopic.startsWith("mqtt:")) {
                    return configuredTopic;
                }
                // Si no, intentar buscar en los tópicos configurados
                // Por ahora, usar el tópico como sensor_id
                return configuredTopic;
            }
        }
        
        // Si no está en los tópicos configurados, usar el tópico como sensor_id
        // Formato: "mqtt:topic1" o "home/sensors/living_room/temperature"
        if (topic.startsWith("mqtt:")) {
            return topic;
        }
        // Si es un tópico con path, usar el tópico completo como sensor_id
        return topic;
    }

    /**
     * Extrae la temperatura del JSON.
     */
    private double extractTemperature(JsonNode jsonNode) {
        if (jsonNode.has("temperature")) {
            return jsonNode.get("temperature").asDouble();
        }
        if (jsonNode.has("temp")) {
            return jsonNode.get("temp").asDouble();
        }
        if (jsonNode.has("value")) {
            return jsonNode.get("value").asDouble();
        }
        // Mensajes del simulador Shelly: params -> "temperature:0" -> tC
        JsonNode paramsNode = jsonNode.get("params");
        if (paramsNode != null) {
            JsonNode shellyTemp = paramsNode.get("temperature:0");
            if (shellyTemp != null && shellyTemp.has("tC")) {
                return shellyTemp.get("tC").asDouble();
            }
            // Algunos mensajes usan "temperature" directo dentro de params
            if (paramsNode.has("temperature") && paramsNode.get("temperature").has("tC")) {
                return paramsNode.get("temperature").get("tC").asDouble();
            }
        }
        throw new IllegalArgumentException("No se encontró campo de temperatura en el mensaje");
    }

    /**
     * Extrae el timestamp del JSON o usa el actual.
     */
    private LocalDateTime extractTimestamp(JsonNode jsonNode) {
        if (jsonNode.has("time_stamp")) {
            String timestampStr = jsonNode.get("time_stamp").asText();
            return LocalDateTime.parse(timestampStr);
        }
        if (jsonNode.has("timestamp")) {
            String timestampStr = jsonNode.get("timestamp").asText();
            return LocalDateTime.parse(timestampStr);
        }
        if (jsonNode.has("time")) {
            String timestampStr = jsonNode.get("time").asText();
            return LocalDateTime.parse(timestampStr);
        }
        // Mensajes del simulador Shelly: timestamp en segundos (double) dentro de params.ts o ts
        JsonNode paramsNode = jsonNode.get("params");
        if (paramsNode != null && paramsNode.has("ts")) {
            double epochSeconds = paramsNode.get("ts").asDouble();
            return LocalDateTime.ofInstant(Instant.ofEpochSecond((long) epochSeconds), ZoneId.systemDefault());
        }
        if (jsonNode.has("ts")) {
            double epochSeconds = jsonNode.get("ts").asDouble();
            return LocalDateTime.ofInstant(Instant.ofEpochSecond((long) epochSeconds), ZoneId.systemDefault());
        }
        // Usar timestamp actual si no está presente
        return LocalDateTime.now();
    }
}

