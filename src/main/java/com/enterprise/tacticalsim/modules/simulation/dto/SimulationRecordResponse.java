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
    // NUEVO: ahora el historial es compartido entre todo el equipo (ver
    // SimulationHistoryService), asi que el Analista necesita saber QUE
    // Entrenador guardo cada jugada -- antes no hacia falta porque cada
    // quien solo veia las suyas propias.
    private String savedByEmail;
    private Double successProbability;
    private Double secondaryEfficiency;
    private Double riskIndex;
    private String recommendedAction;
    private String tacticalNote;
    private LocalDateTime createdAt;
}