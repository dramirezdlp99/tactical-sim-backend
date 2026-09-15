package com.enterprise.tacticalsim.modules.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SimulationRequestDto {

    @NotBlank
    @JsonProperty("match_id")
    private String matchId;

    @NotNull
    @JsonProperty("team_home_positions")
    private List<PlayerPositionDto> teamHomePositions;

    @NotNull
    @JsonProperty("team_away_positions")
    private List<PlayerPositionDto> teamAwayPositions;

    @NotNull
    @JsonProperty("ball_position")
    private PlayerPositionDto ballPosition;
}