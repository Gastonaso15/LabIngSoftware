package com.iotest.infrastructure.monitor;

import com.iotest.domain.model.api.dto.RoomStatusResponse;
import com.iotest.domain.model.api.dto.SystemStatusResponse;
import com.iotest.domain.service.TemperatureControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Monitor simple que muestra el estado del sistema en la terminal.
 * Se ejecuta cada 5 segundos y muestra información de todas las habitaciones.
 */
@Component
@ConditionalOnProperty(name = "terminal-monitor.enabled", havingValue = "true", matchIfMissing = false)
public class TerminalStatusMonitor {

    private static final Logger logger = LoggerFactory.getLogger(TerminalStatusMonitor.class);
    private final TemperatureControlService temperatureControlService;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public TerminalStatusMonitor(TemperatureControlService temperatureControlService) {
        this.temperatureControlService = temperatureControlService;
    }

    @Scheduled(fixedRate = 5000) // Cada 5 segundos
    public void displayStatus() {
        try {
            SystemStatusResponse status = temperatureControlService.getSystemStatus();
            printStatus(status);
        } catch (Exception e) {
            logger.error("Error al obtener estado del sistema: {}", e.getMessage());
        }
    }

    private void printStatus(SystemStatusResponse status) {
        // Limpiar pantalla (funciona en la mayoría de terminales)
        System.out.print("\033[H\033[2J");
        System.out.flush();

        // Encabezado
        String currentTime = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║     SISTEMA DE CONTROL DE TEMPERATURA - Estado Actual          ║");
        System.out.println("║                    " + currentTime + "                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Información de energía
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ ENERGÍA                                                      │");
        System.out.println("├──────────────────────────────────────────────────────────────┤");
        System.out.printf(" │ Máxima:          %.2f kW                                     │%n", status.getMaxEnergy() / 1000.0);
        System.out.printf(" │ Consumo Actual:  %.2f kW                                     │%n", status.getCurrentEnergyConsumption() / 1000.0);
        System.out.printf(" │ Disponible:      %.2f kW                                     │%n", status.getAvailableEnergy() / 1000.0);
        System.out.println("└──────────────────────────────────────────────────────────────┘");
        System.out.println();

        // Estado de habitaciones
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ HABITACIONES                                                 │");
        System.out.println("├──────────────────────────────────────────────────────────────┤");

        if (status.getRooms().isEmpty()) {
            System.out.println("│ No hay habitaciones configuradas                              │");
        } else {
            for (RoomStatusResponse room : status.getRooms()) {
                printRoomStatus(room);
            }
        }

        System.out.println("└──────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("Presiona Ctrl+C para salir...");
    }

    private void printRoomStatus(RoomStatusResponse room) {
        String heatingStatus = room.isHeatingOn() ? "[ON] " : "[OFF]";
        String needsHeating = room.isNeedsHeating() ? "SI" : "NO";
        
        System.out.println("├──────────────────────────────────────────────────────────────┤");
        System.out.printf("│ Habitacion: %s (%s)                                    │%n", 
            room.getName(), room.getRoomId());
        System.out.printf("│ Sensor:     %s                                          │%n", room.getSensorId());
        System.out.printf("│ Temperatura: %.2f°C / %.2f°C (deseada)                      │%n", 
            room.getCurrentTemperature(), room.getDesiredTemperature());
        System.out.printf("│ Calefaccion: %s                                          │%n", heatingStatus);
        System.out.printf("│ Necesita:    %s                                          │%n", needsHeating);
        if (room.getLastUpdate() != null) {
            System.out.printf("│Ultima act:  %s                                    │%n", room.getLastUpdate());
        }
    }
}

