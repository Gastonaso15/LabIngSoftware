package com.iotest.domain.model.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

//dto para devolver el estado de una habitaicion
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStatusResponse {
    @JsonProperty("room_id")
    private String roomId;

    @JsonProperty("sensor_id")
    private String sensorId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("current_temperature")
    private double currentTemperature;

    @JsonProperty("desired_temperature")
    private double desiredTemperature;

    @JsonProperty("temperature_tolerance")
    private double temperatureTolerance;

    @JsonProperty("is_heating_on")
    private boolean isHeatingOn;

    @JsonProperty("last_update")
    private LocalDateTime lastUpdate;

    @JsonProperty("needs_heating")
    private boolean needsHeating;
}
