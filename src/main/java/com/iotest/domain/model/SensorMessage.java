package com.iotest.domain.model;

public class SensorMessage {
    private String src;         // "shellyhtg3-84fce63ad204"
    private double temperature; // params.temperature:0.tC
    private long timestamp;     // params.ts (puede ser double → long)

    public SensorMessage() {}

    public SensorMessage(String src, double temperature, long timestamp) {
        this.src = src;
        this.temperature = temperature;
        this.timestamp = timestamp;
    }

    public String getSrc() { return src; }
    public void setSrc(String src) { this.src = src; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "SensorMessage{" +
                "src='" + src + '\'' +
                ", temperature=" + temperature +
                ", timestamp=" + timestamp +
                '}';
    }
}
