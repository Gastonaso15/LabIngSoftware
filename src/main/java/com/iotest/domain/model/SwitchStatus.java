package com.iotest.domain.model;

public class SwitchStatus {
    private int id;
    private String source;
    private boolean output;
    private double apower;
    private double voltage;
    private double current;
    private double freq;
    private double totalEnergyKWh;

    public SwitchStatus() {}

    public SwitchStatus(boolean output, double apower, double totalEnergyKWh) {
        this.output = output;
        this.apower = apower;
        this.totalEnergyKWh = totalEnergyKWh;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public boolean isOutput() { return output; }
    public void setOutput(boolean output) { this.output = output; }

    public double getApower() { return apower; }
    public void setApower(double apower) { this.apower = apower; }

    public double getVoltage() { return voltage; }
    public void setVoltage(double voltage) { this.voltage = voltage; }

    public double getCurrent() { return current; }
    public void setCurrent(double current) { this.current = current; }

    public double getFreq() { return freq; }
    public void setFreq(double freq) { this.freq = freq; }

    public double getTotalEnergyKWh() { return totalEnergyKWh; }
    public void setTotalEnergyKWh(double totalEnergyKWh) { this.totalEnergyKWh = totalEnergyKWh; }

    @Override
    public String toString() {
        return "SwitchStatus{" +
                "id=" + id +
                ", source='" + source + '\'' +
                ", output=" + output +
                ", apower=" + apower +
                ", totalEnergyKWh=" + totalEnergyKWh +
                '}';
    }
}
