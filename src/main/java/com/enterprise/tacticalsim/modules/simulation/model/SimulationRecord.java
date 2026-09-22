package com.enterprise.tacticalsim.modules.simulation.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro de una jugada simulada, guardado por un Entrenador y
 * consultado despues por un Analista (historial + exportar CSV).
 * Guardar en: tactical-sim-backend/src/main/java/com/enterprise/tacticalsim/modules/simulation/model/SimulationRecord.java
 */
@Entity
@Table(name = "simulation_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, length = 100)
    private String userEmail;

    @Column(nullable = false, length = 20)
    private String sport; // "BASKETBALL" | "TENNIS"

    @Column(name = "play_name", length = 100)
    private String playName;

    @Lob
    @Column(name = "positions_json")
    private String positionsJson; // snapshot de las posiciones al momento de guardar

    @Column(name = "success_probability")
    private Double successProbability;

    @Column(name = "secondary_efficiency")
    private Double secondaryEfficiency;

    @Column(name = "risk_index")
    private Double riskIndex;

    @Column(name = "recommended_action", length = 100)
    private String recommendedAction;

    @Lob
    @Column(name = "tactical_note")
    private String tacticalNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}