package com.iotest.domain.model.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;

//dt para recibir lecturas de temperatura de los sensores
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensorReadingRequest {
    @JsonProperty("sensor_id")
    private String sensorId;

    @JsonProperty("temperature")
    private double temperature;

    @JsonProperty("time_stamp")
    private LocalDateTime timeStamp;
}
