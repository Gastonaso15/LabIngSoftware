package com.iotest.domain.model.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

//dto para devolver el resultado del procesamiento de sensores
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchOperationResponse {
    @JsonProperty("switch_url")
    private String switchUrl;

    @JsonProperty("action")
    private String action;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;
}
