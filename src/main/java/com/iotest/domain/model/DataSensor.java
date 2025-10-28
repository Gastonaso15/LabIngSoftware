package com.iotest.domain.model;

import java.time.LocalDateTime;

// Representa un mensaje recibido del sensor
public class DataSensor {
    private final String sensorId; // ej. "mqtt:topic1"
    private final double temperature;
    private final LocalDateTime timestamp;

    public DataSensor(String sensorId, double temperature, LocalDateTime timestamp) {
        this.sensorId = sensorId;
        this.temperature = temperature;
        this.timestamp = timestamp;
    }

    public String getSensorId() {
        return sensorId;
    }

    public double getTemperature() {
        return temperature;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}