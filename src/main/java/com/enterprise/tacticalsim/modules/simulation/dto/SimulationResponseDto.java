package com.enterprise.tacticalsim.modules.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SimulationResponseDto {

    @JsonProperty("success_probability")
    private Double successProbability;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    @JsonProperty("heat_map_coords")
    private List<List<Double>> heatMapCoords;

    @JsonProperty("execution_thread")
    private String executionThread;
}