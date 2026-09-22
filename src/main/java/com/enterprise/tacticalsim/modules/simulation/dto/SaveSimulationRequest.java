package com.enterprise.tacticalsim.modules.simulation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SaveSimulationRequest {

    @NotBlank(message = "El deporte es requerido")
    private String sport; // "BASKETBALL" | "TENNIS"

    private String playName;
    private String positionsJson;
    private Double successProbability;
    private Double secondaryEfficiency;
    private Double riskIndex;
    private String recommendedAction;
    private String tacticalNote;
}