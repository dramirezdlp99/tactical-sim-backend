package com.enterprise.tacticalsim.modules.simulation.controller;

import com.enterprise.tacticalsim.core.ApiResponse;
import com.enterprise.tacticalsim.modules.simulation.dto.SaveSimulationRequest;
import com.enterprise.tacticalsim.modules.simulation.dto.SimulationRecordResponse;
import com.enterprise.tacticalsim.modules.simulation.service.SimulationHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Historial de jugadas guardadas: el Entrenador guarda (POST), el
 * Analista consulta y exporta (GET). Ambos endpoints GET quedan
 * cubiertos por anyRequest().authenticated() en SecurityConfig -- no
 * hizo falta tocar SecurityConfig porque ya existia esa regla de
 * respaldo. El POST cae dentro del matcher existente
 * "POST /api/v1/simulation/**" (hasAnyRole COACH, ANALYST, ADMIN),
 * asi que tampoco requirio cambios ahi.
 */
@RestController
@RequestMapping("/api/v1/simulation/history")
@RequiredArgsConstructor
public class SimulationHistoryController {

    private final SimulationHistoryService historyService;

    @PostMapping
    public ResponseEntity<ApiResponse<SimulationRecordResponse>> save(
            @Valid @RequestBody SaveSimulationRequest request,
            Authentication authentication
    ) {
        SimulationRecordResponse response = historyService.save(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Jugada guardada en el historial"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SimulationRecordResponse>>> list(Authentication authentication) {
        List<SimulationRecordResponse> records = historyService.listForUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(records, "Historial obtenido"));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportCsv(Authentication authentication) {
        String csv = historyService.exportCsvForUser(authentication.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=historial_simulaciones.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}