package com.iotest.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotest.domain.model.Controllers.TemperatureController;
import com.iotest.domain.model.Logica.ISwitchController;
import com.iotest.domain.model.Logica.SwitchController;
import com.iotest.domain.model.POJOS.DataSwitch;
import com.iotest.domain.model.POJOS.Room;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuración que carga el JSON de configuración del sitio y crea
 * los beans necesarios para el sistema de control de temperatura.
 */
@Configuration
public class TemperatureControlConfig {

    @Value("${temperature-control.config-file:classpath:site-config.json}")
    private Resource configFile;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Carga la configuración del sitio desde el JSON.
     */
    @Bean
    public SiteConfiguration siteConfiguration() throws IOException {
        JsonNode root = objectMapper.readTree(configFile.getInputStream());
        
        double maxPowerWatts = root.get("maxPowerWatts").asDouble();
        JsonNode roomsNode = root.get("rooms");

        List<RoomConfig> rooms = new ArrayList<>();
        if (roomsNode.isArray()) {
            for (JsonNode roomNode : roomsNode) {
                rooms.add(new RoomConfig(
                        roomNode.get("id").asText(),
                        roomNode.has("name") ? roomNode.get("name").asText() : null,
                        roomNode.get("sensorTopic").asText(),
                        roomNode.get("switchUrl").asText(),
                        roomNode.get("desiredTemperature").asDouble(),
                        roomNode.has("temperatureTolerance") 
                                ? roomNode.get("temperatureTolerance").asDouble() 
                                : 1.0,
                        roomNode.get("powerConsumptionWatts").asDouble()
                ));
            }
        }

        return new SiteConfiguration(root.get("siteName").asText(), maxPowerWatts, rooms);
    }

    /**
     * Crea las habitaciones (Room) desde la configuración.
     */
    @Bean
    public List<Room> rooms(SiteConfiguration config) {
        List<Room> roomList = new ArrayList<>();
        for (RoomConfig roomConfig : config.getRooms()) {
            Room room = new Room(
                    roomConfig.getSensorTopic(),
                    roomConfig.getName(),
                    roomConfig.getSwitchUrl(),
                    roomConfig.getDesiredTemperature(),
                    roomConfig.getPowerConsumptionWatts() / 1000.0, // Convertir W a kW
                    null, // currentTemperature
                    false, // heatingOn
                    null, // lastUpdate
                    roomConfig.getTemperatureTolerance()
            );
            roomList.add(room);
        }
        return roomList;
    }

    /**
     * Crea los switches desde la configuración.
     */
    @Bean
    public List<DataSwitch> switches(SiteConfiguration config) {
        List<DataSwitch> switchList = new ArrayList<>();
        for (RoomConfig roomConfig : config.getRooms()) {
            DataSwitch dataSwitch = new DataSwitch(roomConfig.getSwitchUrl(), false);
            switchList.add(dataSwitch);
        }
        return switchList;
    }

    /**
     * Crea el controlador de switches.
     */
    @Bean
    public ISwitchController switchController() {
        return new SwitchController();
    }

    /**
     * Crea el controlador de temperatura.
     */
    @Bean
    public TemperatureController temperatureController(
            SiteConfiguration config,
            List<Room> rooms,
            List<DataSwitch> switches) {
        // Convertir maxPowerWatts a kW
        double maxEnergy = config.getMaxPowerWatts() / 1000.0;
        return new TemperatureController(maxEnergy, rooms, switches);
    }

    // Clases internas para configuración
    public static class SiteConfiguration {
        private final String siteName;
        private final double maxPowerWatts;
        private final List<RoomConfig> rooms;

        public SiteConfiguration(String siteName, double maxPowerWatts, List<RoomConfig> rooms) {
            this.siteName = siteName;
            this.maxPowerWatts = maxPowerWatts;
            this.rooms = rooms;
        }

        public String getSiteName() {
            return siteName;
        }

        public double getMaxPowerWatts() {
            return maxPowerWatts;
        }

        public List<RoomConfig> getRooms() {
            return rooms;
        }
    }

    public static class RoomConfig {
        private final String id;
        private final String name;
        private final String sensorTopic;
        private final String switchUrl;
        private final double desiredTemperature;
        private final double temperatureTolerance;
        private final double powerConsumptionWatts;

        public RoomConfig(String id, String name, String sensorTopic, String switchUrl,
                        double desiredTemperature, double temperatureTolerance,
                        double powerConsumptionWatts) {
            this.id = id;
            this.name = name;
            this.sensorTopic = sensorTopic;
            this.switchUrl = switchUrl;
            this.desiredTemperature = desiredTemperature;
            this.temperatureTolerance = temperatureTolerance;
            this.powerConsumptionWatts = powerConsumptionWatts;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getSensorTopic() { return sensorTopic; }
        public String getSwitchUrl() { return switchUrl; }
        public double getDesiredTemperature() { return desiredTemperature; }
        public double getTemperatureTolerance() { return temperatureTolerance; }
        public double getPowerConsumptionWatts() { return powerConsumptionWatts; }
    }
}

