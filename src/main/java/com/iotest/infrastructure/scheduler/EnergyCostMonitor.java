package com.iotest.infrastructure.scheduler;

import com.iotest.domain.model.Controllers.TemperatureController;
import com.iotest.domain.model.EnergyCost;
import com.iotest.domain.model.Operation;
import com.iotest.domain.model.TimeEvent;
import com.iotest.domain.model.Logica.ISwitchController;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitor que verifica periódicamente los cambios en la tarifa de energía
 * y genera eventos de tiempo automáticamente cuando detecta cambios.
 * 
 * Este componente implementa el esquema de eventos de tiempo del diagrama:
 * - Monitorea cambios de tarifa (HIGH → LOW o LOW → HIGH)
 * - Genera eventos de tiempo cuando detecta cambios
 * - Envía los eventos al TemperatureController para procesamiento
 * 
 * Usa un thread manual para monitorear cambios cada 5 segundos.
 * 
 * Se puede deshabilitar configurando: energy-cost-monitor.enabled=false
 */
@Component
@ConditionalOnProperty(name = "energy-cost-monitor.enabled", havingValue = "true", matchIfMissing = true)
public class EnergyCostMonitor {

    private static final Logger logger = LoggerFactory.getLogger(EnergyCostMonitor.class);

    private final TemperatureController temperatureController;
    private final ISwitchController switchController;
    private final String contract;

    // Estado interno para detectar cambios
    private Integer lastKnownTariff = null;
    
    // Thread para monitorear cambios de tarifa
    private Thread monitorThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final long checkIntervalMs; // Intervalo en milisegundos (configurable)

    public EnergyCostMonitor(
            TemperatureController temperatureController,
            ISwitchController switchController,
            @Value("${temperature-control.energy-contract:testContract}") String contract,
            @Value("${energy-cost-monitor.check-interval-seconds:5}") long checkIntervalSeconds) {
        this.temperatureController = temperatureController;
        this.switchController = switchController;
        this.contract = contract;
        this.checkIntervalMs = checkIntervalSeconds * 1000; // Convertir segundos a milisegundos
    }

    /**
     * Inicia el thread de monitoreo cuando el componente se crea.
     */
    @PostConstruct
    public void init() {
        running.set(true);
        monitorThread = new Thread(this::monitorEnergyCostChanges, "EnergyCostMonitor-Thread");
        monitorThread.setDaemon(true); // Thread daemon para que no impida el cierre de la aplicación
        monitorThread.start();
        logger.info("Monitor de energía iniciado (thread manual) - Intervalo de verificación: {} segundos", checkIntervalMs / 1000);
    }

    /**
     * Detiene el thread de monitoreo cuando el componente se destruye.
     */
    @PreDestroy
    public void destroy() {
        running.set(false);
        if (monitorThread != null) {
            monitorThread.interrupt();
            try {
                monitorThread.join(2000); // Esperar hasta 2 segundos para que termine
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrumpido mientras esperaba que el thread de monitoreo termine");
            }
            logger.info("Monitor de energía detenido");
        }
    }

    /**
     * Método que se ejecuta en el thread para monitorear cambios de tarifa.
     * Verifica periódicamente si la tarifa de energía ha cambiado.
     * 
     * El intervalo de verificación es configurable mediante la propiedad
     * 'energy-cost-monitor.check-interval-seconds' (por defecto 5 segundos).
     * 
     * Para testContract, la tarifa cambia cada 30 segundos, por lo que
     * verificar cada 5 segundos (o menos) es suficiente para detectar los cambios.
     */
    private void monitorEnergyCostChanges() {
        logger.debug("Thread de monitoreo de energía iniciado");
        
        while (running.get()) {
            try {
                checkEnergyCostChanges();
                
                // Dormir según el intervalo configurado, pero verificar running periódicamente
                long sleepTime = checkIntervalMs;
                long startTime = System.currentTimeMillis();
                while (running.get() && sleepTime > 0) {
                    Thread.sleep(Math.min(sleepTime, 1000)); // Dormir en bloques de 1 segundo
                    sleepTime = checkIntervalMs - (System.currentTimeMillis() - startTime);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Thread de monitoreo interrumpido");
                break;
            } catch (Exception e) {
                logger.error("Error en el thread de monitoreo de energía: {}", e.getMessage(), e);
                // Continuar ejecutando aunque haya un error
                try {
                    Thread.sleep(1000); // Esperar 1 segundo antes de reintentar
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.debug("Thread de monitoreo de energía finalizado");
    }

    /**
     * Verifica si la tarifa de energía ha cambiado.
     */
    private void checkEnergyCostChanges() {
        try {
            // Obtener el tiempo actual (solo aquí, en la capa de infraestructura)
            long currentTime = System.currentTimeMillis();
            
            // Pasar el tiempo como parámetro (NO consultar dentro del dominio)
            EnergyCost.EnergyZone zone = EnergyCost.energyZone(contract, currentTime);
            int currentTariff = zone.current();

            // Primera ejecución: inicializar el estado
            if (lastKnownTariff == null) {
                lastKnownTariff = currentTariff;
                logger.debug("Monitor de energía inicializado. Tarifa actual: {}", 
                    currentTariff == EnergyCost.HIGH ? "HIGH" : "LOW");
                
                // Si la tarifa inicial es HIGH, apagar todos los switches inmediatamente
                // (no esperar a que cambie, porque si ya es HIGH al inicio, deben apagarse)
                if (currentTariff == EnergyCost.HIGH) {
                    logger.info("Tarifa inicial es HIGH. Apagando todos los switches encendidos...");
                    // Crear un evento de tiempo simulado: LOW -> HIGH (para que el controller apague)
                    // Usamos LOW como previousTariff para que isChangeToHigh() retorne true
                    LocalDateTime eventTimestamp = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(currentTime), ZoneId.systemDefault());
                    
                    TimeEvent timeEvent = new TimeEvent(
                        contract,
                        EnergyCost.LOW, // Simulamos que veníamos de LOW
                        EnergyCost.HIGH, // Y ahora estamos en HIGH
                        eventTimestamp,
                        zone.nextTS()
                    );
                    
                    processTimeEvent(timeEvent);
                }
                return;
            }

            // Detectar cambio de tarifa
            if (lastKnownTariff != currentTariff) {
                logger.info("Cambio de tarifa detectado: {} → {} (contrato: {})", 
                    lastKnownTariff == EnergyCost.HIGH ? "HIGH" : "LOW",
                    currentTariff == EnergyCost.HIGH ? "HIGH" : "LOW",
                    contract);

                // Crear evento de tiempo con el timestamp pasado como parámetro
                LocalDateTime eventTimestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(currentTime), ZoneId.systemDefault());
                
                TimeEvent timeEvent = new TimeEvent(
                    contract,
                    lastKnownTariff,
                    currentTariff,
                    eventTimestamp,
                    zone.nextTS()
                );

                // Procesar el evento en el controller (el controller NO consulta el tiempo)
                processTimeEvent(timeEvent);

                // Actualizar el estado
                lastKnownTariff = currentTariff;
            }
        } catch (Exception e) {
            logger.error("Error al verificar cambios de tarifa de energía: {}", e.getMessage(), e);
        }
    }

    /**
     * Procesa un evento de tiempo: envía el evento al controller y ejecuta las operaciones.
     */
    private void processTimeEvent(TimeEvent timeEvent) {
        try {
            // Enviar evento al controller (según el diagrama: Evento → Controller)
            List<Operation> operations = temperatureController.processTimeEvent(timeEvent);

            // Ejecutar las operaciones sobre los switches
            if (!operations.isEmpty()) {
                logger.info("Ejecutando {} operaciones debido a cambio de tarifa", operations.size());
                executeOperations(operations);
            } else {
                logger.debug("No se requieren operaciones para este cambio de tarifa");
            }
        } catch (Exception e) {
            logger.error("Error al procesar evento de tiempo: {}", e.getMessage(), e);
        }
    }

    /**
     * Ejecuta las operaciones sobre los switches físicos.
     */
    private void executeOperations(List<Operation> operations) {
        for (Operation operation : operations) {
            try {
                boolean desiredState = "ON".equals(operation.getAction());
                switchController.postSwitchStatus(operation.getSwitchUrl(), desiredState);
                logger.info("Operación ejecutada: {} en {}", operation.getAction(), operation.getSwitchUrl());
            } catch (IOException | InterruptedException e) {
                logger.error("Error al ejecutar operación en {}: {}", 
                    operation.getSwitchUrl(), e.getMessage());
            }
        }
    }
}

