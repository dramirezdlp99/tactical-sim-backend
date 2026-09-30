package com.enterprise.tacticalsim.modules.simulation.repository;

import com.enterprise.tacticalsim.modules.simulation.model.SimulationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulationRecordRepository extends JpaRepository<SimulationRecord, Long> {

    List<SimulationRecord> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    // NUEVO: historial compartido de todo el equipo, ordenado del mas
    // reciente al mas antiguo -- usado por los Analistas para ver las
    // jugadas guardadas por CUALQUIER Entrenador, no solo las propias.
    List<SimulationRecord> findAllByOrderByCreatedAtDesc();
}