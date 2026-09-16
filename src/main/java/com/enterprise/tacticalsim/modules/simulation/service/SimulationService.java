package com.enterprise.tacticalsim.modules.simulation.service;

import com.enterprise.tacticalsim.modules.simulation.dto.SimulationRequestDto;
import com.enterprise.tacticalsim.modules.simulation.dto.SimulationResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private final WebClient aiEngineWebClient;

    public SimulationResponseDto runSimulation(SimulationRequestDto request) {
        return aiEngineWebClient.post()
                .uri("/api/v1/simulation/predict")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SimulationResponseDto.class)
                .block(); // Ejecución síncrona controlada en el cliente WebClient
    }
}