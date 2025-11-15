package com.iotest.domain.model.Controllers;

import com.iotest.domain.model.POJOS.DataSensor;
import com.iotest.domain.model.POJOS.DataSwitch;
import com.iotest.domain.model.Operation;
import com.iotest.domain.model.POJOS.Room;
import com.iotest.domain.model.EnergyCost;
import com.iotest.domain.model.TimeEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TemperatureController {

    private final double maxEnergy;
    // Mapas para búsqueda rápida (eficiencia)
    private final Map<String, Room> roomBySensorId;
    private final Map<String, DataSwitch> switchByUrl;
    // Lista de todas las habitaciones para iterar
    private final List<Room> allRooms;

    public TemperatureController(double maxEnergy, List<Room> rooms, List<DataSwitch> switches) {
        this.maxEnergy = maxEnergy;
        this.allRooms = rooms;

        // Creamos mapas para encontrar objetos por su ID/URL rápidamente
        this.roomBySensorId = rooms.stream()
                .collect(Collectors.toMap(Room::getSensorId, Function.identity()));

        this.switchByUrl = switches.stream()
                .collect(Collectors.toMap(DataSwitch::getSwitchUrl, Function.identity()));
    }

    /**
     * Este es el método principal que define la API de lógica.
     * Recibe datos del sensor y devuelve las acciones a tomar.
     */
    public List<Operation> processSensorData(DataSensor sensorData) {

        // 1. Encontrar la habitación que reporta la temperatura
        Room reportingRoom = findRoomBySensorId(sensorData.getSensorId()).orElse(null);

        // Si el sensorId no corresponde a ninguna habitación, no hacemos nada
        if (reportingRoom == null) {
            return new ArrayList<>();
        }

        // 2. Actualizar el estado interno de la habitación
        reportingRoom.updateTemperature(sensorData.getTemperature(), sensorData.getTimestamp());

        // 3. Ejecutar la lógica principal de decisión
        return calculateOperations();
    }

    /**
     * Contiene la lógica principal del controlador.
     * Decide qué switches prender o apagar basado en el estado de TODAS las habitaciones
     * y el límite de energía.
     */
    private List<Operation> calculateOperations() {
        List<Operation> operations = new ArrayList<>();
        double currentConsumption = getCurrentEnergyConsumption();

        // --- PASO 1: APAGAR switches que ya no se necesitan ---
        // Iteramos todas las habitaciones para ver si alguna está cálida pero encendida.
        for (Room room : allRooms) {
            DataSwitch sw = findSwitchByUrl(room.getSwitchUrl()).orElse(null);

            // Si la habitación NO necesita calefacción Y su switch ESTÁ encendido
            if (sw != null && !room.needsHeating() && sw.isOn()) {
                // Generamos la operación de apagado
                // NO actualizamos el estado interno aquí - se actualizará DESPUÉS de que la operación física se ejecute exitosamente
                operations.add(new Operation(sw.getSwitchUrl(), "OFF"));
                // Recuperamos la energía que estaba consumiendo (para el cálculo de energía disponible)
                currentConsumption -= room.getEnergyConsumption();
            }
        }

        // --- PASO 2: ENCENDER switches prioritarios (si hay energía) ---

        // Recalcular energía disponible basándose en el estado actual después del PASO 1
        double availableEnergy = maxEnergy - currentConsumption;
        // Usar un pequeño epsilon para evitar problemas de precisión de punto flotante
        final double EPSILON = 0.001;

        // Obtenemos una lista de habitaciones que NECESITAN calefacción y están APAGADAS
        List<Room> roomsToHeat = allRooms.stream()
                .filter(Room::needsHeating)
                .filter(room -> findSwitchByUrl(room.getSwitchUrl()).map(sw -> !sw.isOn()).orElse(false))
                .sorted(Comparator.comparing(Room::getTemperatureDeficit).reversed()) // Prioriza la MÁS fría
                .collect(Collectors.toList());

        // Lista de habitaciones que están encendidas (para posible "swap")
        List<Room> runningRooms = allRooms.stream()
                .filter(room -> findSwitchByUrl(room.getSwitchUrl()).map(DataSwitch::isOn).orElse(false))
                .sorted(Comparator.comparing(Room::getTemperatureDeficit)) // Ordena de menos prioritaria a más
                .collect(Collectors.toList());


        // Iteramos por la lista de prioridad (de más fría a menos fría)
        for (Room roomToHeat : roomsToHeat) {
            DataSwitch swToTurnOn = findSwitchByUrl(roomToHeat.getSwitchUrl()).orElse(null);
            if (swToTurnOn == null) continue; // Saltar si no se encuentra el switch
            
            double roomEnergy = roomToHeat.getEnergyConsumption();

            // --- Caso A: Hay energía de sobra. La encendemos.
            // Usar >= con epsilon para manejar precisión de punto flotante
            if (roomEnergy <= availableEnergy + EPSILON) {
                operations.add(new Operation(swToTurnOn.getSwitchUrl(), "ON"));
                // NO actualizamos el estado interno aquí - se actualizará DESPUÉS de que la operación física se ejecute exitosamente
                availableEnergy -= roomEnergy;

                // --- Caso B: No hay energía. Vemos si podemos "robar" de otra menos prioritaria.
            } else {
                // (Esto cubre el Test 5: shouldPrioritizeColderRoom...)
                // Buscamos en las habitaciones ya encendidas (runningRooms)
                // si alguna tiene MENOS prioridad (menor déficit) que la que queremos encender.
                for (Room runningRoom : runningRooms) {

                    // Comparamos prioridades
                    if (roomToHeat.getTemperatureDeficit() > runningRoom.getTemperatureDeficit()) {

                        DataSwitch swToTurnOff = findSwitchByUrl(runningRoom.getSwitchUrl()).orElse(null);
                        if (swToTurnOff == null) continue; // Saltar si no se encuentra el switch
                        
                        double freedEnergy = runningRoom.getEnergyConsumption();

                        // Si apagando esta, ¿hay sitio para la nueva?
                        // Usar >= con epsilon para manejar precisión de punto flotante
                        if (availableEnergy + freedEnergy >= roomEnergy - EPSILON) {
                            // ¡Sí! Hacemos el "swap"

                            // 1. Apagar la menos prioritaria
                            operations.add(new Operation(swToTurnOff.getSwitchUrl(), "OFF"));
                            // NO actualizamos el estado interno aquí - se actualizará DESPUÉS de que la operación física se ejecute exitosamente

                            // 2. Encender la más prioritaria
                            operations.add(new Operation(swToTurnOn.getSwitchUrl(), "ON"));
                            // NO actualizamos el estado interno aquí - se actualizará DESPUÉS de que la operación física se ejecute exitosamente

                            // 3. Actualizar energía disponible
                            availableEnergy = (availableEnergy + freedEnergy) - roomEnergy;

                            // 4. Quitar la habitación que apagamos de la lista de "running"
                            runningRooms.remove(runningRoom);

                            // 5. Dejamos de buscar "víctimas" para esta habitación.
                            break;
                        }
                    }
                }
            }
        }

        return operations;
    }

    // --- Métodos de Ayuda (Helpers) ---

    private double getCurrentEnergyConsumption() {
        return allRooms.stream()
                .filter(room -> findSwitchByUrl(room.getSwitchUrl()).map(DataSwitch::isOn).orElse(false))
                .mapToDouble(Room::getEnergyConsumption)
                .sum();
    }

    private Optional<Room> findRoomBySensorId(String sensorId) {
        return Optional.ofNullable(roomBySensorId.get(sensorId));
    }

    private Optional<DataSwitch> findSwitchByUrl(String switchUrl) {
        return Optional.ofNullable(switchByUrl.get(switchUrl));
    }

    public double getMaxEnergy() {
        return maxEnergy;
    }

    /**
     * Procesa un evento de tiempo (cambio de tarifa de energía).
     * Este método se llama automáticamente cuando se detecta un cambio en la tarifa.
     * 
     * @param timeEvent Evento de tiempo que contiene información sobre el cambio de tarifa
     * @return Lista de operaciones a realizar (apagar switches si la tarifa es HIGH)
     */
    public List<Operation> processTimeEvent(TimeEvent timeEvent) {
        List<Operation> operations = new ArrayList<>();
        
        // Si la tarifa cambió a HIGH, apagar todos los switches
        if (timeEvent.isChangeToHigh()) {
            for (Room room : allRooms) {
                DataSwitch sw = findSwitchByUrl(room.getSwitchUrl()).orElse(null);
                if (sw != null && sw.isOn()) {
                    operations.add(new Operation(sw.getSwitchUrl(), "OFF"));
                    // NO actualizamos el estado interno aquí - se actualizará DESPUÉS de que la operación física se ejecute exitosamente
                }
            }
        }
        // Si la tarifa cambió a LOW, no hacemos nada automáticamente
        // Las habitaciones se encenderán cuando lleguen eventos de temperatura
        
        return operations;
    }

    /**
     * Método legacy para verificar y aplicar política de alto costo.
     * Ahora se recomienda usar processTimeEvent() que se llama automáticamente.
     * 
     * IMPORTANTE: Este método recibe el tiempo como parámetro, NO lo consulta internamente.
     * Esto cumple con el principio de que el controller no debe consultar el tiempo.
     * 
     * @param contract Contrato de energía
     * @param timestamp Timestamp actual (en milisegundos desde epoch)
     * @return Lista de operaciones a realizar
     * @deprecated Usar processTimeEvent() en su lugar
     */
    @Deprecated
    public List<Operation> turnSwitchOffWhenHighCost(String contract, long timestamp){
        List<Operation> operations = new ArrayList<>();
        // Usar energyZone() pasando el tiempo como parámetro (NO currentEnergyZone())
        EnergyCost.EnergyZone zone = EnergyCost.energyZone(contract, timestamp);
        if (zone.current() == EnergyCost.HIGH){
            for (Room room : allRooms){
                DataSwitch sw = findSwitchByUrl(room.getSwitchUrl()).orElse(null);
                if (sw != null && sw.isOn()){
                    operations.add(new Operation(sw.getSwitchUrl(), "OFF"));
                    // NO actualizamos el estado interno aquí - se actualizará DESPUÉS de que la operación física se ejecute exitosamente
                }
            }
        }
        return operations;
    }
}