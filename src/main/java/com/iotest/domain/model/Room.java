package com.iotest.domain.model;

import java.time.LocalDateTime;

public class Room {
    private final String id;
    private final String name;
    private final double desiredTemperature;
    private final double powerConsumption;
    private final String switchUrl;
    private final String sensorId;

    private Double currentTemperature;
    private boolean heatingOn;
    private LocalDateTime lastUpdate;
    private static final double temperatureTolerance = 0.5;

    public Room(String id, String name, double desiredTemperature,
                double powerConsumption, String switchUrl, String sensorId) {
        this.id = id;
        this.name = name;
        this.desiredTemperature = desiredTemperature;
        this.powerConsumption = powerConsumption;
        this.heatingOn = false;
        this.switchUrl = switchUrl;
        this.sensorId = sensorId;
    }

    public boolean needsHeating() {
        if (currentTemperature == null) {
            return false;
        }
        double threshold = desiredTemperature - temperatureTolerance;
        return currentTemperature < threshold;
    }

    public void updateTemperature(double temperature, LocalDateTime timestamp) {
        this.currentTemperature = temperature;
        this.lastUpdate = timestamp;
    }

    public double getTemperatureDeficit() {
        if (currentTemperature == null || needsHeating() == false) return 0.0;
        return desiredTemperature - currentTemperature;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public double getDesiredTemperature() { return desiredTemperature; }
    public Double getCurrentTemperature() { return currentTemperature; }
    public String getSwitchUrl() { return switchUrl; }
    public double getEnergyConsumption() { return powerConsumption; }
    public boolean isHeatingOn() { return heatingOn; }
    public void setHeatingOn(boolean heatingOn) { this.heatingOn = heatingOn; }
    public String getSensorId() { return sensorId; }
}