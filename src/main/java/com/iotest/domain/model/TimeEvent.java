package com.iotest.domain.model;

import java.time.LocalDateTime;

/**
 * Evento de tiempo que representa un cambio en la tarifa de energía.
 * Este evento se genera automáticamente cuando la tarifa cambia de HIGH a LOW o viceversa.
 */
public class TimeEvent {
    private final String contract;
    private final int previousTariff; // Tarifa anterior (LOW o HIGH)
    private final int currentTariff;  // Tarifa actual (LOW o HIGH)
    private final LocalDateTime timestamp;
    private final long nextChangeTimestamp; // Cuándo cambiará la próxima tarifa

    public TimeEvent(String contract, int previousTariff, int currentTariff, 
                    LocalDateTime timestamp, long nextChangeTimestamp) {
        this.contract = contract;
        this.previousTariff = previousTariff;
        this.currentTariff = currentTariff;
        this.timestamp = timestamp;
        this.nextChangeTimestamp = nextChangeTimestamp;
    }

    public String getContract() {
        return contract;
    }

    public int getPreviousTariff() {
        return previousTariff;
    }

    public int getCurrentTariff() {
        return currentTariff;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public long getNextChangeTimestamp() {
        return nextChangeTimestamp;
    }

    /**
     * Indica si la tarifa cambió de LOW a HIGH.
     */
    public boolean isChangeToHigh() {
        return previousTariff == EnergyCost.LOW && currentTariff == EnergyCost.HIGH;
    }

    /**
     * Indica si la tarifa cambió de HIGH a LOW.
     */
    public boolean isChangeToLow() {
        return previousTariff == EnergyCost.HIGH && currentTariff == EnergyCost.LOW;
    }

    /**
     * Indica si hubo un cambio de tarifa.
     */
    public boolean hasTariffChanged() {
        return previousTariff != currentTariff;
    }
}


