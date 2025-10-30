package com.iotest.domain.model.POJOS;
import java.time.LocalDateTime;

public class Room {
    private final String id;
    private final String name;
    private final double desiredTemperature;
    private final int powerConsumption;

    private Double currentTemperature;
    private boolean heatingOn;
    private LocalDateTime lastUpdate;
    private Double temperatureTolerance; //aceptable temperature fluctutation

    public Room(String id, String name, double desiredTemperature,
                int powerConsumption, Double currentTemperature, boolean heatingOn,LocalDateTime lastUpdate, Double temperatureTolerance, String switchUrl) {
        this.id = id;
        this.name = name;
        this.desiredTemperature = desiredTemperature;
        this.powerConsumption = powerConsumption ;
        this.currentTemperature = currentTemperature;
        this.heatingOn = heatingOn;
        this.lastUpdate = lastUpdate;
        this.temperatureTolerance = temperatureTolerance;
        this.heatingOn = false;
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

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public double getDesiredTemperature() { return desiredTemperature; }
    public Double getCurrentTemperature() { return currentTemperature; }
    public Double getTemperatureTolerance() { return temperatureTolerance; }
    public boolean isHeatingOn() { return heatingOn; }
    public void setHeatingOn(boolean heatingOn) { this.heatingOn = heatingOn; }
}