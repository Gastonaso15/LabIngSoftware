package com.iotest.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotest.domain.model.Controllers.TemperatureController;
import com.iotest.domain.model.Logica.ISwitchController;
import com.iotest.domain.model.Logica.SwitchController;
import com.iotest.domain.model.POJOS.DataSwitch;
import com.iotest.domain.model.POJOS.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuración que carga el JSON de configuración del sitio y crea
 * los beans necesarios para el sistema de control de temperatura.
 */
@Configuration
public class TemperatureControlConfig {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureControlConfig.class);

    @Value("${temperature-control.config-file:classpath:site-config.json}")
    private String configLocation;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResourceLoader resourceLoader;

    public TemperatureControlConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Carga la configuración del sitio desde el JSON.
     * Soporta tanto el nuevo formato como el formato legacy para compatibilidad.
     */
    @Bean
    public SiteConfiguration siteConfiguration() throws IOException {
        Resource resource = resolveConfigResource();

        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);

            // Leer siteName (nuevo formato: "site", legacy: "siteName")
            String siteName = root.has("site") 
                    ? root.get("site").asText() 
                    : root.get("siteName").asText();

            // Leer maxEnergy/maxPowerWatts (nuevo formato: "maxEnergy" como string "60 kWh", legacy: "maxPowerWatts" como número)
            double maxPowerWatts;
            if (root.has("maxEnergy")) {
                String maxEnergy = root.get("maxEnergy").asText();
                maxPowerWatts = parseEnergyToWatts(maxEnergy);
            } else {
                maxPowerWatts = root.get("maxPowerWatts").asDouble();
            }

            JsonNode roomsNode = root.get("rooms");

            List<RoomConfig> rooms = new ArrayList<>();
            if (roomsNode.isArray()) {
                for (JsonNode roomNode : roomsNode) {
                    // id: usar "name" como id si "id" no está presente (nuevo formato)
                    String id = roomNode.has("id") 
                            ? roomNode.get("id").asText() 
                            : roomNode.get("name").asText();

                    // name
                    String name = roomNode.has("name") ? roomNode.get("name").asText() : null;

                    // sensorTopic (nuevo formato: "sensor", legacy: "sensorTopic")
                    String sensorTopic = roomNode.has("sensor") 
                            ? roomNode.get("sensor").asText() 
                            : roomNode.get("sensorTopic").asText();

                    // switchUrl (nuevo formato: "switch", legacy: "switchUrl")
                    String switchUrl = roomNode.has("switch") 
                            ? roomNode.get("switch").asText() 
                            : roomNode.get("switchUrl").asText();

                    // desiredTemperature (nuevo formato: "expectedTemp" como string, legacy: "desiredTemperature" como número)
                    double desiredTemperature;
                    if (roomNode.has("expectedTemp")) {
                        String expectedTemp = roomNode.get("expectedTemp").asText();
                        desiredTemperature = parseTemperature(expectedTemp);
                    } else {
                        desiredTemperature = roomNode.get("desiredTemperature").asDouble();
                    }

                    // temperatureTolerance (opcional, default 1.0)
                    double temperatureTolerance = roomNode.has("temperatureTolerance")
                            ? roomNode.get("temperatureTolerance").asDouble()
                            : 1.0;

                    // powerConsumptionWatts (nuevo formato: "energy" como string "80 kWh", legacy: "powerConsumptionWatts" como número)
                    double powerConsumptionWatts;
                    if (roomNode.has("energy")) {
                        String energy = roomNode.get("energy").asText();
                        powerConsumptionWatts = parseEnergyToWatts(energy);
                    } else {
                        powerConsumptionWatts = roomNode.get("powerConsumptionWatts").asDouble();
                    }

                    rooms.add(new RoomConfig(
                            id, name, sensorTopic, switchUrl,
                            desiredTemperature, temperatureTolerance,
                            powerConsumptionWatts
                    ));
                }
            }

            return new SiteConfiguration(siteName, maxPowerWatts, rooms);
        }
    }

    /**
     * Parsea un string de energía con unidades (ej: "60 kWh", "80 kWh") a watts.
     * Soporta formatos: "60 kWh", "60kWh", "60", etc.
     */
    private double parseEnergyToWatts(String energy) {
        if (energy == null || energy.trim().isEmpty()) {
            throw new IllegalArgumentException("Energy string cannot be null or empty");
        }

        String trimmed = energy.trim().toLowerCase();
        
        // Extraer el número
        String numberPart = trimmed.replaceAll("[^0-9.]", "").trim();
        if (numberPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid energy format: " + energy);
        }

        double value = Double.parseDouble(numberPart);

        // Determinar la unidad y convertir a watts
        if (trimmed.contains("kwh") || trimmed.contains("kw")) {
            // Si tiene "kWh" o "kW", convertir de kWh a watts (multiplicar por 1000)
            return value * 1000.0;
        } else if (trimmed.contains("wh") || trimmed.contains("w")) {
            // Si tiene "Wh" o "W" (sin 'k'), ya está en watts
            return value;
        } else {
            // Si no tiene unidad, asumir que está en kWh (para compatibilidad con formato "60")
            return value * 1000.0;
        }
    }

    /**
     * Parsea un string de temperatura a double.
     * Soporta formatos: "22", "22.0", "21.5", etc.
     */
    private double parseTemperature(String temp) {
        if (temp == null || temp.trim().isEmpty()) {
            throw new IllegalArgumentException("Temperature string cannot be null or empty");
        }

        String trimmed = temp.trim();
        
        // Remover cualquier unidad o carácter no numérico excepto el punto decimal
        String numberPart = trimmed.replaceAll("[^0-9.]", "").trim();
        if (numberPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid temperature format: " + temp);
        }

        return Double.parseDouble(numberPart);
    }

    private Resource resolveConfigResource() throws IOException {
        Resource resource = resourceLoader.getResource(configLocation);
        if (resource.exists()) {
            return resource;
        }

        // Si no tiene prefijo, interpretarlo como ruta absoluta o relativa en el filesystem
        if (!configLocation.startsWith("classpath:") && !configLocation.startsWith("file:")) {
            Path path = Path.of(configLocation).toAbsolutePath();
            if (Files.exists(path)) {
                return resourceLoader.getResource("file:" + path);
            }
        }

        logger.warn("No se encontró el archivo de configuración en '{}'. Usando fallback del classpath.", configLocation);
        Resource fallback = resourceLoader.getResource("classpath:site-config.json");
        if (!fallback.exists()) {
            throw new IOException("No se encontró la configuración del sitio ni en " + configLocation + " ni en classpath:site-config.json");
        }
        return fallback;
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

