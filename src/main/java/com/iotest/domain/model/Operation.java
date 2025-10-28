package com.iotest.domain.model;

import java.util.Objects;

// Representa una acción a tomar sobre un switch
public class Operation {
    private final String switchUrl;
    private final String action; // "ON" o "OFF"

    public Operation(String switchUrl, String action) {
        this.switchUrl = switchUrl;
        this.action = action;
    }

    public String getSwitchUrl() {
        return switchUrl;
    }

    public String getAction() {
        return action;
    }

    // Necesario para que AssertJ pueda comparar objetos en las pruebas
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Operation operation = (Operation) o;
        return Objects.equals(switchUrl, operation.switchUrl) &&
                Objects.equals(action, operation.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(switchUrl, action);
    }

    @Override
    public String toString() {
        return "Operation{" +
                "switchUrl='" + switchUrl + '\'' +
                ", action='" + action + '\'' +
                '}';
    }
}