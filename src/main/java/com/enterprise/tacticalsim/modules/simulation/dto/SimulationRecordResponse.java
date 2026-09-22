package com.enterprise.tacticalsim.modules.simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SimulationRecordResponse {
    private Long id;
    private String sport;
    private String playName;
    private Double successProbability;
    private Double secondaryEfficiency;
    private Double riskIndex;
    private String recommendedAction;
    private String tacticalNote;
    private LocalDateTime createdAt;
}