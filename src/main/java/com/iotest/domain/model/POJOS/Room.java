package com.iotest.domain.model.POJOS;
import java.time.LocalDateTime;

public class Room {
    private final String sensorId;
    private final String name;
    private final String switchUrl;
    private final double desiredTemperature;
    private final double powerConsumption;

    private Double currentTemperature;
    private boolean heatingOn;
    private LocalDateTime lastUpdate;
    private final Double temperatureTolerance; //aceptable temperature fluctutation

    // Constructor simplificado para los tests
    public Room(String sensorId, String switchUrl, double desiredTemperature, double powerConsumption) {
        this(sensorId, null, switchUrl, desiredTemperature, powerConsumption, null, false, null, 1.0);
    }

    // Constructor completo
    public Room(String sensorId, String name, String switchUrl, double desiredTemperature,
                double powerConsumption, Double currentTemperature, boolean heatingOn, 
                LocalDateTime lastUpdate, Double temperatureTolerance) {
        this.sensorId = sensorId;
        this.name = name;
        this.switchUrl = switchUrl;
        this.desiredTemperature = desiredTemperature;
        this.powerConsumption = powerConsumption;
        this.currentTemperature = currentTemperature;
        this.heatingOn = heatingOn;
        this.lastUpdate = lastUpdate;
        this.temperatureTolerance = temperatureTolerance != null ? temperatureTolerance : 1.0;
    }

    public boolean needsHeating() {
        if (currentTemperature == null || temperatureTolerance == null) {
            return false;
        }
        double threshold = desiredTemperature - temperatureTolerance;
        return currentTemperature < threshold;
    }

    public void updateTemperature(double temperature, LocalDateTime timestamp) {
        this.currentTemperature = temperature;
        this.lastUpdate = timestamp;
    }

    // Calcula el déficit de temperatura (cuánto falta para llegar a la temperatura deseada)
    public double getTemperatureDeficit() {
        if (currentTemperature == null) {
            return 0.0;
        }
        return Math.max(0.0, desiredTemperature - currentTemperature);
    }

    // Getters
    public String getSensorId() { return sensorId; }
    public String getName() { return name; }
    public String getSwitchUrl() { return switchUrl; }
    public String getId() { return sensorId; } // Para retrocompatibilidad
    public double getDesiredTemperature() { return desiredTemperature; }
    public Double getCurrentTemperature() { return currentTemperature; }
    public Double getTemperatureTolerance() { return temperatureTolerance; }
    public boolean isHeatingOn() { return heatingOn; }
    public void setHeatingOn(boolean heatingOn) { this.heatingOn = heatingOn; }
    public double getEnergyConsumption() { return powerConsumption; }
    public LocalDateTime getLastUpdate() { return lastUpdate; }
}