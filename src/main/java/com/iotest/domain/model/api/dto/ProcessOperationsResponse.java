package com.iotest.domain.model.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

//dto para devolver el resultado de una operacion sobre un switch
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessOperationsResponse {
    @JsonProperty("sensor_id")
    private String sensorId;

    @JsonProperty("operations_count")
    private int operationscount;

    @JsonProperty("operations")
    private List<SwitchOperationResponse> operations;

    @JsonProperty("current_energy_consumption")
    private double currentEnergyConsumption;
}
