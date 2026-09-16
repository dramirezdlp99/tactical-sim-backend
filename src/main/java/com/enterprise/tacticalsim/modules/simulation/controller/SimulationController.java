package com.enterprise.tacticalsim.modules.simulation.controller;

import com.enterprise.tacticalsim.core.ApiResponse;
import com.enterprise.tacticalsim.modules.simulation.dto.SimulationRequestDto;
import com.enterprise.tacticalsim.modules.simulation.dto.SimulationResponseDto;
import com.enterprise.tacticalsim.modules.simulation.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<SimulationResponseDto>> runSimulation(
            @Valid @RequestBody SimulationRequestDto request
    ) {
        SimulationResponseDto response = simulationService.runSimulation(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Tactical simulation processed successfully by AI engine"));
    }
}