package com.enterprise.tacticalsim.modules.simulation.service;

import com.enterprise.tacticalsim.modules.simulation.dto.SaveSimulationRequest;
import com.enterprise.tacticalsim.modules.simulation.dto.SimulationRecordResponse;
import com.enterprise.tacticalsim.modules.simulation.model.SimulationRecord;
import com.enterprise.tacticalsim.modules.simulation.repository.SimulationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SimulationHistoryService {

    private final SimulationRecordRepository repository;
    private static final DateTimeFormatter CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public SimulationRecordResponse save(String userEmail, SaveSimulationRequest request) {
        SimulationRecord record = SimulationRecord.builder()
                .userEmail(userEmail)
                .sport(request.getSport())
                .playName(request.getPlayName())
                .positionsJson(request.getPositionsJson())
                .successProbability(request.getSuccessProbability())
                .secondaryEfficiency(request.getSecondaryEfficiency())
                .riskIndex(request.getRiskIndex())
                .recommendedAction(request.getRecommendedAction())
                .tacticalNote(request.getTacticalNote())
                .build();

        repository.save(record);
        return toResponse(record);
    }

    public List<SimulationRecordResponse> listForUser(String userEmail) {
        return repository.findByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public String exportCsvForUser(String userEmail) {
        List<SimulationRecord> records = repository.findByUserEmailOrderByCreatedAtDesc(userEmail);

        StringBuilder csv = new StringBuilder();
        csv.append("Fecha,Deporte,Jugada,Probabilidad Exito (%),Eficiencia (%),Riesgo (%),Accion Recomendada\n");

        for (SimulationRecord r : records) {
            csv.append(r.getCreatedAt().format(CSV_DATE_FORMAT)).append(",")
                    .append(r.getSport()).append(",")
                    .append(escapeCsv(r.getPlayName())).append(",")
                    .append(percent(r.getSuccessProbability())).append(",")
                    .append(percent(r.getSecondaryEfficiency())).append(",")
                    .append(percent(r.getRiskIndex())).append(",")
                    .append(escapeCsv(r.getRecommendedAction()))
                    .append("\n");
        }
        return csv.toString();
    }

    private String percent(Double value) {
        return value == null ? "" : String.valueOf(Math.round(value * 100));
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private SimulationRecordResponse toResponse(SimulationRecord r) {
        return SimulationRecordResponse.builder()
                .id(r.getId())
                .sport(r.getSport())
                .playName(r.getPlayName())
                .successProbability(r.getSuccessProbability())
                .secondaryEfficiency(r.getSecondaryEfficiency())
                .riskIndex(r.getRiskIndex())
                .recommendedAction(r.getRecommendedAction())
                .tacticalNote(r.getTacticalNote())
                .createdAt(r.getCreatedAt())
                .build();
    }
}