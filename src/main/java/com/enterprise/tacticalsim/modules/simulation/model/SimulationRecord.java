package com.enterprise.tacticalsim.modules.simulation.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    // CAMBIO: @Lob se cambio por @JdbcTypeCode(SqlTypes.LONGVARCHAR).
    // @Lob mapeaba este campo a "oid" (objeto grande) en Postgres, que
    // requiere leer el dato como un stream especial dentro de la MISMA
    // conexion/transaccion -- con el pool de conexiones de Spring Boot
    // (HikariCP), esa conexion ya se habia liberado antes de poder leer
    // el stream, y tronaba con "Unable to access lob stream" al listar
    // el historial. Con LONGVARCHAR, Postgres guarda esto como columna
    // "text" normal, sin ese problema. En H2 (local) no cambia nada, ya
    // funcionaba bien de cualquier forma.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "positions_json", columnDefinition = "text")
    private String positionsJson; // snapshot de las posiciones al momento de guardar

    @Column(name = "success_probability")
    private Double successProbability;

    @Column(name = "secondary_efficiency")
    private Double secondaryEfficiency;

    @Column(name = "risk_index")
    private Double riskIndex;

    @Column(name = "recommended_action", length = 100)
    private String recommendedAction;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "tactical_note", columnDefinition = "text")
    private String tacticalNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}