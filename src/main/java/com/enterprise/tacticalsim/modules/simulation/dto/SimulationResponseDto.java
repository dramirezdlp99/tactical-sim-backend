package com.enterprise.tacticalsim.modules.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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

    // CAMBIO: campos nuevos que ahora devuelve el motor de IA (calculo
    // geometrico real en vez del mock que siempre daba 50%).
    @JsonProperty("secondary_efficiency")
    private Double secondaryEfficiency;

    @JsonProperty("risk_index")
    private Double riskIndex;

    @JsonProperty("tactical_note")
    private String tacticalNote;

    @JsonProperty("ball_speed_kmh")
    private Double ballSpeedKmh;

    @JsonProperty("spin_rate_rpm")
    private Double spinRateRpm;

    // ---- Modelo predictivo entrenado + "Cuerpo Tecnico Rival" (todos opcionales) ----
    // Si el motor de IA es una version anterior, llegan como null y el
    // frontend simplemente no los dibuja.

    /** Posicion estimada de los defensores (tenis: del restador) DESPUES de reaccionar. */
    @JsonProperty("predicted_defense")
    private List<Map<String, Object>> predictedDefense;

    /** Esquema defensivo legible, ej. "Hombre a hombre con ayuda en la pintura". */
    @JsonProperty("defense_scheme")
    private String defenseScheme;

    /** Baloncesto: probabilidad de anotar un tiro limpio contra la defensa predicha. */
    @JsonProperty("clean_shot_probability")
    private Double cleanShotProbability;

    /** Baloncesto: efectividad del mejor pase disponible. */
    @JsonProperty("pass_effectiveness")
    private Double passEffectiveness;

    /** Descripcion del modelo que produjo el resultado (tipo, datos, metricas). */
    @JsonProperty("model_info")
    private String modelInfo;
}
