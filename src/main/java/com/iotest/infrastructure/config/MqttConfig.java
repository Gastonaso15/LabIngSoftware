package com.iotest.infrastructure.config;

import com.iotest.domain.service.TemperatureControlService;
import com.iotest.infrastructure.mqtt.MqttSensorSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuración para el cliente MQTT.
 * Crea el suscriptor MQTT con los tópicos configurados en el site-config.json.
 * Solo se activa si mqtt.enabled=true (por defecto está deshabilitado en tests).
 */
@Configuration
public class MqttConfig {

    @Bean
    @ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
    public MqttSensorSubscriber mqttSensorSubscriber(
            TemperatureControlService temperatureControlService,
            TemperatureControlConfig.SiteConfiguration siteConfiguration,
            @Value("${mqtt.broker:tcp://localhost:1883}") String brokerUrl,
            @Value("${mqtt.client-id:temp-controller}") String clientId,
            @Value("${mqtt.auto-reconnect:true}") boolean autoReconnect) {
        
        // Extraer los tópicos de los sensores desde la configuración
        List<String> topics = siteConfiguration.getRooms().stream()
                .map(TemperatureControlConfig.RoomConfig::getSensorTopic)
                .collect(Collectors.toList());

        return new MqttSensorSubscriber(temperatureControlService, topics, brokerUrl, clientId, autoReconnect);
    }
}

