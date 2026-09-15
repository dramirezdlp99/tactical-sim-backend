package com.enterprise.tacticalsim.modules.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerPositionDto {

    @NotNull
    @JsonProperty("player_id")
    private String playerId;

    @NotNull
    private Double x;

    @NotNull
    private Double y;
}