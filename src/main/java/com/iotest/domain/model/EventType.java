package com.iotest.domain.model;

/**
 * Tipo de evento que puede llegar al controller.
 * Según el diagrama de diseño, hay dos tipos de eventos:
 * - TEMPERATURE: Evento de temperatura (lectura de sensor)
 * - TIME: Evento de tiempo (cambio de tarifa de energía)
 */
public enum EventType {
    TEMPERATURE,
    TIME
}


