package com.iotest.domain.model.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

//dto para devolver el estado general del sistema
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusResponse {
    @JsonProperty("max_energy")
    private double maxEnergy;

    @JsonProperty("current_energy_consumption")
    private double currentEnergyConsumption;

    @JsonProperty("available_energy")
    private double availableEnergy;

    @JsonProperty("rooms")
    private List<RoomStatusResponse> rooms;
}