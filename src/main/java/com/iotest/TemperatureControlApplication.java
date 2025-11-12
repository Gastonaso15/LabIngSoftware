package com.iotest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aplicación principal del sistema de control de temperatura.
 * 
 * Esta aplicación ejecuta un componente de control de temperatura para
 * gestionar la calefacción eléctrica de múltiples habitaciones basándose
 * en lecturas de sensores MQTT y límites de consumo energético.
 */
@SpringBootApplication
@EnableScheduling
public class TemperatureControlApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemperatureControlApplication.class, args);
    }
}

