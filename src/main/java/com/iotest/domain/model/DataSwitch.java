package com.iotest.domain.model;

// Representa el estado interno de un switch
public class DataSwitch {
    private final String switchUrl;
    private boolean isOn;

    public DataSwitch(String switchUrl, boolean isOn) {
        this.switchUrl = switchUrl;
        this.isOn = isOn;
    }

    public String getSwitchUrl() {
        return switchUrl;
    }

    public boolean isOn() {
        return isOn;
    }

    public void setOn(boolean on) {
        isOn = on;
    }
}